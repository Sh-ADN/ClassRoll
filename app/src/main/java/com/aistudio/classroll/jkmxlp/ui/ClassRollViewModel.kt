package com.aistudio.classroll.jkmxlp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.aistudio.classroll.jkmxlp.data.AppDatabase
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.ClassRollRepository
import com.aistudio.classroll.jkmxlp.data.NetworkModule
import com.aistudio.classroll.jkmxlp.data.RemoteStudent
import com.aistudio.classroll.jkmxlp.data.SettingsRepository
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.flatMapLatest

import org.json.JSONArray
import org.json.JSONObject

class ClassRollViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "classroll_db")
        .fallbackToDestructiveMigration()
        .build()
    private val settingsRepo = SettingsRepository(application)
    val repository = ClassRollRepository(db.classRollDao(), NetworkModule.apiService, settingsRepo)
    val firestoreRepo = com.aistudio.classroll.jkmxlp.data.FirestoreRepository(application)

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    val webAppUrl = settingsRepo.webAppUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://script.google.com/macros/s/AKfycbzTDiNJh4LEaIah19SVFaf6JlESbW5tf2ElwaMULTDENIAlXFOFI4QAXEmV1nYwrVdA/exec")
    val currentYear = settingsRepo.academicYearFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val appTheme = settingsRepo.appThemeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")
    // NEW
    val lastBackupTimestamp = settingsRepo.lastBackupTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val reminderEnabled = settingsRepo.reminderEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val reminderTime = settingsRepo.reminderTimeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "10:00")
    val holidays: StateFlow<Set<String>> = settingsRepo.holidaysFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleHoliday(date: String) {
        viewModelScope.launch {
            settingsRepo.toggleHoliday(date)
        }
    }

    private val _availableYears = MutableStateFlow<List<String>>(emptyList())
    val availableYears: StateFlow<List<String>> = _availableYears.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    // NEW: call after a successful "Save Backup to File" so the reminder
    // banner in Settings knows when the last backup actually happened.
    fun markBackupDone() {
        viewModelScope.launch {
            settingsRepo.updateLastBackupTimestamp(System.currentTimeMillis())
        }
    }

    // NEW: turns the daily "take attendance" reminder on/off and
    // (re)schedules the underlying WorkManager job to match.
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateReminderEnabled(enabled)
            if (enabled) {
                com.aistudio.classroll.jkmxlp.reminder.AttendanceReminderWorker.scheduleNext(
                    getApplication(), reminderTime.value
                )
            } else {
                com.aistudio.classroll.jkmxlp.reminder.AttendanceReminderWorker.cancel(getApplication())
            }
        }
    }

    // NEW: called when the user picks a different reminder time.
    fun setReminderTime(time: String) {
        viewModelScope.launch {
            settingsRepo.updateReminderTime(time)
            if (reminderEnabled.value) {
                com.aistudio.classroll.jkmxlp.reminder.AttendanceReminderWorker.scheduleNext(getApplication(), time)
            }
        }
    }

    fun updateSettings(year: String) {
        viewModelScope.launch {
            settingsRepo.updateAcademicYear(year)
        }
    }

    fun updateTheme(themeMode: String) {
        viewModelScope.launch {
            repository.updateAppTheme(themeMode)
        }
    }

    // NEW: builds the year picker's options from years that actually exist
    // locally (plus the current calendar year, so a fresh install still has
    // something to pick), instead of a hardcoded list that goes stale.
    fun loadAvailableYears() {
        viewModelScope.launch {
            val allStudents = repository.getAllStudentsAllYears()
            val calendarYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
            val years = (allStudents.map { it.year }.filter { it.isNotBlank() } + calendarYear)
                .distinct()
                .sorted()
            _availableYears.value = years
        }
    }

    fun fetchYears() {
        viewModelScope.launch {
            _isSyncing.value = true
            val years = repository.fetchYears()
            if (years.isNotEmpty()) {
                _availableYears.value = years
            }
            _isSyncing.value = false
        }
    }

    fun syncStudents() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.syncStudents()
            _isSyncing.value = false
        }
    }

    fun importStudents(year: String, students: List<RemoteStudent>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.importStudents(year, students)
            _isSyncing.value = false
            onResult(result)
        }
    }

    fun submitAttendance(date: String, records: List<AttendanceRecordEntity>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val year = currentYear.value
            val success = repository.submitAttendance(year, date, records)
            try {
                records.forEach { firestoreRepo.saveAttendanceRecord(it) }
            } catch (e: Exception) {
                // Ignore background sync errors to preserve offline capability
            }
            _isSyncing.value = false
            onResult(success)
        }
    }
    
    fun updateCell(date: String, roll: String, status: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.updateAttendanceCell(year, date, roll, status)
            try {
                firestoreRepo.saveAttendanceRecord(AttendanceRecordEntity(year, date, roll, status, isSynced = true))
            } catch (e: Exception) {
                // Ignore background sync errors
            }
        }
    }

    fun addOrUpdateStudent(roll: String, name: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.addOrUpdateStudent(year, roll, name)
                try {
                    firestoreRepo.saveStudent(StudentEntity(year, roll, name, true))
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    fun deleteStudent(roll: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.deleteStudent(year, roll)
                try {
                    firestoreRepo.deleteStudent(year, roll)
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    fun deleteAttendanceRecord(date: String, roll: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.deleteAttendanceRecord(year, date, roll)
                try {
                    firestoreRepo.deleteAttendanceRecord(year, date, roll)
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    fun clearAttendanceForDate(date: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.clearAttendanceForDate(year, date)
                try {
                    firestoreRepo.clearAttendanceForDate(year, date)
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    fun syncToCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                val students = repository.getAllStudentsAllYears()
                val records = repository.getAllAttendanceAllYears()
                val activeYear = currentYear.value.ifBlank {
                    students.firstOrNull { it.year.isNotBlank() }?.year
                        ?: records.firstOrNull { it.year.isNotBlank() }?.year
                        ?: SimpleDateFormat("yyyy", Locale.US).format(Date())
                }
                firestoreRepo.syncLocalToCloud(students, records, activeYear, appTheme.value)
                onComplete(true, "Successfully backed up ${students.size} students and ${records.size} attendance records for academic year $activeYear to Cloud Firestore!")
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Failed to sync to cloud")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun restoreFromCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                val (students, records, cloudYear) = firestoreRepo.pullCloudToLocal()
                repository.restoreBackupData(students, records)

                // Automatically restore the academic year so the UI immediately shows the records
                val targetYear = when {
                    !cloudYear.isNullOrBlank() -> cloudYear
                    students.any { it.year.isNotBlank() } -> students.first { it.year.isNotBlank() }.year
                    records.any { it.year.isNotBlank() } -> records.first { it.year.isNotBlank() }.year
                    currentYear.value.isNotBlank() -> currentYear.value
                    else -> SimpleDateFormat("yyyy", Locale.US).format(Date())
                }
                settingsRepo.updateAcademicYear(targetYear)
                loadAvailableYears()

                if (records.isEmpty() && students.isNotEmpty()) {
                    onComplete(true, "Restored ${students.size} students for year $targetYear. (Note: No cloud attendance records were found on this account yet. Tap 'Backup to Cloud' after taking attendance to save them).")
                } else {
                    onComplete(true, "Successfully restored ${students.size} students and ${records.size} attendance records for academic year $targetYear from Cloud Firestore!")
                }
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Failed to restore from cloud")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }
    
    fun clearAttendance() {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.clearAttendanceForYear(year)
            }
        }
    }

    fun clearStudents() {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.clearStudentsForYear(year)
            }
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val students = repository.getAllStudentsAllYears()
                val records = repository.getAllAttendanceAllYears()

                val jsonRoot = JSONObject()
                jsonRoot.put("version", 1)
                jsonRoot.put("academicYear", currentYear.value)
                jsonRoot.put("exportedAt", System.currentTimeMillis())

                val studentsArray = JSONArray()
                students.forEach { s ->
                    val sObj = JSONObject()
                    sObj.put("year", s.year)
                    sObj.put("roll", s.roll)
                    sObj.put("name", s.name)
                    sObj.put("active", s.active)
                    studentsArray.put(sObj)
                }
                jsonRoot.put("students", studentsArray)

                val recordsArray = JSONArray()
                records.forEach { r ->
                    val rObj = JSONObject()
                    rObj.put("year", r.year)
                    rObj.put("date", r.date)
                    rObj.put("roll", r.roll)
                    rObj.put("status", r.status)
                    recordsArray.put(rObj)
                }
                jsonRoot.put("attendanceRecords", recordsArray)

                onResult(jsonRoot.toString(2))
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("")
            }
        }
    }

    fun restoreBackupJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonRoot = JSONObject(jsonString)
                val explicitYear = jsonRoot.optString("academicYear", "")

                val studentsArray = jsonRoot.optJSONArray("students") ?: JSONArray()
                val recordsArray = jsonRoot.optJSONArray("attendanceRecords") 
                    ?: jsonRoot.optJSONArray("attendance") 
                    ?: jsonRoot.optJSONArray("records") 
                    ?: JSONArray()

                val students = mutableListOf<StudentEntity>()
                for (i in 0 until studentsArray.length()) {
                    val sObj = studentsArray.optJSONObject(i) ?: continue
                    val y = sObj.optString("year", explicitYear).ifBlank {
                        explicitYear.ifBlank { SimpleDateFormat("yyyy", Locale.US).format(Date()) }
                    }
                    val r = (sObj.opt("roll") ?: "").toString().trim()
                    if (r.isBlank()) continue
                    val n = sObj.optString("name", "")
                    val a = sObj.optBoolean("active", true)
                    students.add(StudentEntity(year = y, roll = r, name = n, active = a))
                }

                val records = mutableListOf<AttendanceRecordEntity>()
                for (i in 0 until recordsArray.length()) {
                    val rObj = recordsArray.optJSONObject(i) ?: continue
                    val y = rObj.optString("year", explicitYear).ifBlank {
                        explicitYear.ifBlank { SimpleDateFormat("yyyy", Locale.US).format(Date()) }
                    }
                    val d = rObj.optString("date", "").trim()
                    val r = (rObj.opt("roll") ?: "").toString().trim()
                    val rawStatus = (rObj.opt("status") ?: "P").toString().trim()
                    val s = when (rawStatus.uppercase()) {
                        "PRESENT", "P" -> "P"
                        "ABSENT", "A" -> "A"
                        else -> rawStatus.ifBlank { "P" }
                    }
                    if (d.isNotBlank() && r.isNotBlank()) {
                        records.add(AttendanceRecordEntity(year = y, date = d, roll = r, status = s, isSynced = true))
                    }
                }

                repository.restoreBackupData(students, records)

                val targetYear = when {
                    explicitYear.isNotBlank() -> explicitYear
                    students.any { it.year.isNotBlank() } -> students.first { it.year.isNotBlank() }.year
                    records.any { it.year.isNotBlank() } -> records.first { it.year.isNotBlank() }.year
                    currentYear.value.isNotBlank() -> currentYear.value
                    else -> SimpleDateFormat("yyyy", Locale.US).format(Date())
                }
                settingsRepo.updateAcademicYear(targetYear)
                loadAvailableYears()

                onResult(true, "Restored ${students.size} students and ${records.size} attendance records for academic year $targetYear successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to parse backup JSON: ${e.message}")
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<StudentEntity>> = currentYear
        .flatMapLatest { year ->
            repository.getStudentsForYear(year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun getAttendanceForDate(date: String): StateFlow<List<AttendanceRecordEntity>> {
        val year = currentYear.value
        return repository.getAttendanceForDate(year, date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getAttendanceForMonth(monthPrefix: String): StateFlow<List<AttendanceRecordEntity>> {
        val year = currentYear.value
        return repository.getAttendanceForMonth(year, monthPrefix).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val allAttendanceForYear: StateFlow<List<AttendanceRecordEntity>> = currentYear
        .flatMapLatest { year ->
            repository.getAllAttendanceForYear(year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
