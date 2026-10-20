package com.aistudio.classroll.jkmxlp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.aistudio.classroll.jkmxlp.data.AppDatabase
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.ClassRollRepository
import com.aistudio.classroll.jkmxlp.data.FirestoreRepository
import com.aistudio.classroll.jkmxlp.data.NetworkModule
import com.aistudio.classroll.jkmxlp.data.RemoteStudent
import com.aistudio.classroll.jkmxlp.data.SettingsRepository
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import com.aistudio.classroll.jkmxlp.reminder.AttendanceReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedBackup(
    val students: List<StudentEntity>,
    val records: List<AttendanceRecordEntity>,
    val explicitYear: String,
    val holidays: Set<String>?
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClassRollViewModel(application: Application) : AndroidViewModel(application) {

    val db: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "classroll_db"
    ).fallbackToDestructiveMigration().build()

    val settingsRepo: SettingsRepository = SettingsRepository(application)
    val repository: ClassRollRepository = ClassRollRepository(
        dao = db.classRollDao(),
        api = NetworkModule.apiService,
        settingsRepo = settingsRepo,
        database = db
    )
    val firestoreRepo: FirestoreRepository = FirestoreRepository(application)

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _cloudProgress = MutableStateFlow("")
    val cloudProgress: StateFlow<String> = _cloudProgress.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val offlineMode: StateFlow<Boolean?> = settingsRepo.offlineModeFlow
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val webAppUrl: StateFlow<String> = settingsRepo.webAppUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val currentYear: StateFlow<String> = settingsRepo.academicYearFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "2025")

    val appTheme: StateFlow<String> = settingsRepo.appThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val lastBackupTimestamp: StateFlow<Long> = settingsRepo.lastBackupTimestampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val reminderEnabled: StateFlow<Boolean> = settingsRepo.reminderEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reminderTime: StateFlow<String> = settingsRepo.reminderTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "09:00")

    val holidays: StateFlow<Set<String>> = settingsRepo.holidaysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _availableYears = MutableStateFlow(listOf("2024", "2025", "2026", "2027"))
    val availableYears: StateFlow<List<String>> = _availableYears.asStateFlow()

    val students: StateFlow<List<StudentEntity>> = currentYear
        .flatMapLatest { year -> repository.getStudentsForYear(year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendanceForYear: StateFlow<List<AttendanceRecordEntity>> = currentYear
        .flatMapLatest { year -> repository.getAllAttendanceForYear(year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecordEntity>> {
        val year = currentYear.value
        return repository.getAttendanceForDate(year, date)
    }

    fun getAttendanceForMonth(monthPrefix: String): Flow<List<AttendanceRecordEntity>> {
        val year = currentYear.value
        return repository.getAttendanceForMonth(year, monthPrefix)
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateOfflineMode(enabled)
        }
    }

    fun updateSettings(year: String) {
        viewModelScope.launch {
            settingsRepo.updateAcademicYear(year)
        }
    }

    fun updateTheme(themeMode: String) {
        viewModelScope.launch {
            settingsRepo.updateAppTheme(themeMode)
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateReminderEnabled(enabled)
            if (enabled) {
                AttendanceReminderWorker.scheduleNext(getApplication(), reminderTime.value)
            } else {
                AttendanceReminderWorker.cancel(getApplication())
            }
        }
    }

    fun setReminderTime(time: String) {
        viewModelScope.launch {
            settingsRepo.updateReminderTime(time)
            if (reminderEnabled.value) {
                AttendanceReminderWorker.scheduleNext(getApplication(), time)
            }
        }
    }

    fun toggleHoliday(date: String) {
        viewModelScope.launch {
            settingsRepo.toggleHoliday(date)
        }
    }

    fun markBackupDone() {
        viewModelScope.launch {
            settingsRepo.updateLastBackupTimestamp(System.currentTimeMillis())
        }
    }

    fun loadAvailableYears() {
        // Can be populated from API or presets
    }

    fun fetchYears() {
        viewModelScope.launch {
            val fetched = repository.fetchYears()
            if (fetched.isNotEmpty()) {
                val combined = (_availableYears.value + fetched).distinct().sorted()
                _availableYears.value = combined
            }
        }
    }

    fun syncStudents() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncStudents()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importStudents(year: String, students: List<RemoteStudent>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val res = repository.importStudents(year, students)
                onResult(res)
            } catch (e: Exception) {
                onResult(e.message ?: "Import failed")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun submitAttendance(records: List<AttendanceRecordEntity>) {
        viewModelScope.launch {
            val year = currentYear.value
            val date = records.firstOrNull()?.date ?: return@launch
            repository.submitAttendance(year, date, records)
        }
    }

    fun updateCell(roll: String, status: String, date: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.updateAttendanceCell(year, date, roll, status)
        }
    }

    fun addOrUpdateStudent(roll: String, name: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.addOrUpdateStudent(year, roll, name)
        }
    }

    fun deleteStudent(roll: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.deleteStudent(year, roll)
        }
    }

    fun deleteAttendanceRecord(date: String, roll: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.deleteAttendanceRecord(year, date, roll)
        }
    }

    fun clearAttendanceForDate(date: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.clearAttendanceForDate(year, date)
        }
    }

    fun clearAttendance() {
        viewModelScope.launch {
            val year = currentYear.value
            repository.clearAttendanceForYear(year)
        }
    }

    fun clearStudents() {
        viewModelScope.launch {
            val year = currentYear.value
            repository.clearStudentsForYear(year)
        }
    }

    fun syncToCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudProgress.value = "Starting cloud sync..."
            try {
                val allStudents = repository.getAllStudentsAllYears()
                val allAttendance = repository.getAllAttendanceAllYears()
                val year = currentYear.value
                val theme = appTheme.value
                val holidaySet = holidays.value

                firestoreRepo.syncLocalToCloud(
                    students = allStudents,
                    attendance = allAttendance,
                    academicYear = year,
                    appTheme = theme,
                    holidays = holidaySet
                ) { done, total ->
                    _cloudProgress.value = "Synced $done of $total records..."
                }
                markBackupDone()
                onComplete(true, "Cloud sync completed successfully!")
            } catch (e: Exception) {
                onComplete(false, "Cloud sync failed: ${e.localizedMessage}")
            } finally {
                _isCloudSyncing.value = false
                _cloudProgress.value = ""
            }
        }
    }

    fun restoreFromCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudProgress.value = "Pulling data from cloud..."
            try {
                val triple = firestoreRepo.pullCloudToLocal()
                val students = triple.first
                val records = triple.second
                val cloudYear = triple.third

                if (students.isNotEmpty() || records.isNotEmpty()) {
                    repository.restoreBackupData(students, records)
                    if (!cloudYear.isNullOrBlank()) {
                        settingsRepo.updateAcademicYear(cloudYear)
                    }
                    val cloudHolidays = firestoreRepo.pullHolidays()
                    if (cloudHolidays.isNotEmpty()) {
                        settingsRepo.setHolidays(cloudHolidays)
                    }
                    markBackupDone()
                    onComplete(true, "Restored ${students.size} students and ${records.size} attendance records.")
                } else {
                    onComplete(false, "No cloud backup found for this account.")
                }
            } catch (e: Exception) {
                onComplete(false, "Restore failed: ${e.localizedMessage}")
            } finally {
                _isCloudSyncing.value = false
                _cloudProgress.value = ""
            }
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val students = repository.getAllStudentsAllYears()
            val records = repository.getAllAttendanceAllYears()
            val holidaySet = holidays.value
            val year = currentYear.value

            val json = withContext(Dispatchers.Default) {
                val obj = JSONObject()
                obj.put("version", 2)
                obj.put("academicYear", year)
                obj.put("exportedAt", System.currentTimeMillis())

                val studentsArray = JSONArray()
                students.forEach { s ->
                    val sObj = JSONObject()
                    sObj.put("year", s.year)
                    sObj.put("roll", s.roll)
                    sObj.put("name", s.name)
                    sObj.put("active", s.active)
                    studentsArray.put(sObj)
                }
                obj.put("students", studentsArray)

                val recordsArray = JSONArray()
                records.forEach { r ->
                    val rObj = JSONObject()
                    rObj.put("year", r.year)
                    rObj.put("date", r.date)
                    rObj.put("roll", r.roll)
                    rObj.put("status", r.status)
                    recordsArray.put(rObj)
                }
                obj.put("attendanceRecords", recordsArray)

                val holidaysArray = JSONArray()
                holidaySet.sorted().forEach { holidaysArray.put(it) }
                obj.put("holidays", holidaysArray)

                obj.toString(2)
            }
            onResult(json)
        }
    }

    fun restoreBackupJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    val root = JSONObject(jsonString)
                    val targetYear = root.optString("academicYear", currentYear.value)

                    val studentsList = mutableListOf<StudentEntity>()
                    val sArr = root.optJSONArray("students")
                    if (sArr != null) {
                        for (i in 0 until sArr.length()) {
                            val o = sArr.getJSONObject(i)
                            studentsList.add(
                                StudentEntity(
                                    year = o.optString("year", targetYear),
                                    roll = o.optString("roll", ""),
                                    name = o.optString("name", ""),
                                    active = o.optBoolean("active", true)
                                )
                            )
                        }
                    }

                    val recordsList = mutableListOf<AttendanceRecordEntity>()
                    val rArr = root.optJSONArray("attendanceRecords") ?: root.optJSONArray("records")
                    if (rArr != null) {
                        for (i in 0 until rArr.length()) {
                            val o = rArr.getJSONObject(i)
                            recordsList.add(
                                AttendanceRecordEntity(
                                    year = o.optString("year", targetYear),
                                    date = o.optString("date", ""),
                                    roll = o.optString("roll", ""),
                                    status = o.optString("status", "PRESENT"),
                                    isSynced = true
                                )
                            )
                        }
                    }

                    val holidaysSet = mutableSetOf<String>()
                    val hArr = root.optJSONArray("holidays")
                    if (hArr != null) {
                        for (i in 0 until hArr.length()) {
                            holidaysSet.add(hArr.getString(i))
                        }
                    }

                    ParsedBackup(
                        students = studentsList,
                        records = recordsList,
                        explicitYear = targetYear,
                        holidays = if (holidaysSet.isNotEmpty()) holidaysSet else null
                    )
                }

                repository.restoreBackupData(parsed.students, parsed.records)
                settingsRepo.updateAcademicYear(parsed.explicitYear)
                if (parsed.holidays != null) {
                    settingsRepo.setHolidays(parsed.holidays)
                }
                markBackupDone()
                onResult(true, "Restored ${parsed.students.size} students and ${parsed.records.size} attendance records.")
            } catch (e: Exception) {
                onResult(false, "Restore failed: ${e.localizedMessage}")
            }
        }
    }
}
