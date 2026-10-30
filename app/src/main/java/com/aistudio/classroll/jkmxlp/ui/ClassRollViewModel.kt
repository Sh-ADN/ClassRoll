package com.aistudio.classroll.jkmxlp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.classroll.jkmxlp.data.AppDatabase
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.ClassRollRepository
import com.aistudio.classroll.jkmxlp.data.RemoteStudent
import com.aistudio.classroll.jkmxlp.data.SettingsRepository
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.flatMapLatest

import org.json.JSONArray
import org.json.JSONObject

private data class ParsedBackup(
    val students: List<StudentEntity>,
    val records: List<AttendanceRecordEntity>,
    val explicitYear: String,
    val holidays: Set<String>?,
    val finePerAbsentDay: Int?,
    val skippedRecords: Int
)

class ClassRollViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settingsRepo = SettingsRepository(application)
    val repository = ClassRollRepository(db.classRollDao(), settingsRepo, db)
    val firestoreRepo = com.aistudio.classroll.jkmxlp.data.FirestoreRepository(application)

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    // Short status text shown while a cloud backup/restore is running.
    private val _cloudProgress = MutableStateFlow("")
    val cloudProgress: StateFlow<String> = _cloudProgress.asStateFlow()

    // null until the saved choice has been read from disk, so the sign-in
    // screen does not flash on launch for people who chose to stay offline.
    val offlineMode: StateFlow<Boolean?> = settingsRepo.offlineModeFlow
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateOfflineMode(enabled)
        }
    }

    val currentYear = settingsRepo.academicYearFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val appTheme = settingsRepo.appThemeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")
    // NEW
    val lastBackupTimestamp = settingsRepo.lastBackupTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val reminderEnabled = settingsRepo.reminderEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val reminderTime = settingsRepo.reminderTimeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "10:00")
    val holidays: StateFlow<Set<String>> = settingsRepo.holidaysFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val finePerAbsentDay: StateFlow<Int> = settingsRepo.finePerAbsentDayFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    fun updateFinePerAbsentDay(amount: Int) {
        viewModelScope.launch {
            settingsRepo.updateFinePerAbsentDay(amount)
        }
    }

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

    fun importStudents(year: String, students: List<RemoteStudent>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.importStudents(year, students)
            _isSyncing.value = false
            loadAvailableYears()
            onResult(result)

            if (result == "Success") {
                launch {
                    try {
                        val entities = students.map {
                            StudentEntity(year = year, roll = it.roll, name = it.name, active = it.active)
                        }
                        firestoreRepo.saveStudentsBatch(entities)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Offline-first: local copy is already saved.
                    }
                }
            }
        }
    }

    fun submitAttendance(date: String, records: List<AttendanceRecordEntity>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val year = currentYear.value
            val success = repository.submitAttendance(year, date, records)
            _isSyncing.value = false
            onResult(success)

            // The cloud copy runs on its own, in one batched write, so the UI never
            // waits for the network. Before, this awaited one write per student,
            // one after another (slow, and it stalled completely while offline).
            launch {
                try {
                    firestoreRepo.saveAttendanceBatch(records)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline-first: the local copy is already saved.
                }
            }
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

    fun addOrUpdateStudent(roll: String, name: String, active: Boolean = true) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.addOrUpdateStudent(year, roll, name, active)
                try {
                    firestoreRepo.saveStudent(StudentEntity(year, roll, name, active))
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
            _cloudProgress.value = "Preparing..."
            try {
                val students = repository.getAllStudentsAllYears()
                val records = repository.getAllAttendanceAllYears()
                val holidaySet = settingsRepo.holidaysFlow.first()
                val fineRate = settingsRepo.finePerAbsentDayFlow.first()
                val activeYear = currentYear.value.ifBlank {
                    students.firstOrNull { it.year.isNotBlank() }?.year
                        ?: records.firstOrNull { it.year.isNotBlank() }?.year
                        ?: SimpleDateFormat("yyyy", Locale.US).format(Date())
                }
                val studentCount = students.count { it.roll.isNotBlank() && it.name.isNotBlank() }
                val recordCount = records.count { it.roll.isNotBlank() && it.date.isNotBlank() }

                // Give up after 3 minutes instead of spinning forever when offline.
                val finished = withTimeoutOrNull(180_000L) {
                    firestoreRepo.syncLocalToCloud(
                        students, records, activeYear, appTheme.value, holidaySet, fineRate
                    ) { done, total ->
                        _cloudProgress.value = if (total == 0) "" else "Uploading $done of $total..."
                    }
                    true
                }

                if (finished == null) {
                    onComplete(false, "Backup timed out. Check your internet connection and try again.")
                } else {
                    settingsRepo.updateLastBackupTimestamp(System.currentTimeMillis())
                    onComplete(true, "Successfully backed up $studentCount students and $recordCount attendance records for academic year $activeYear to Cloud Firestore!")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Failed to sync to cloud")
            } finally {
                _cloudProgress.value = ""
                _isCloudSyncing.value = false
            }
        }
    }

    fun restoreFromCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudProgress.value = "Downloading from cloud..."
            try {
                val pulled = withTimeoutOrNull(120_000L) { firestoreRepo.pullCloudToLocal() }
                if (pulled == null) {
                    onComplete(false, "Restore timed out. Check your internet connection and try again. Nothing on this phone was changed.")
                    return@launch
                }
                val (students, records, cloudYear) = pulled

                _cloudProgress.value = "Saving to this phone..."
                // Throws, without touching local data, if the cloud copy is empty.
                repository.restoreBackupData(students, records)

                val cloudHolidays = try {
                    firestoreRepo.pullHolidays()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (cloudHolidays != null) {
                    settingsRepo.setHolidays(cloudHolidays)
                }

                // The fine rate is money, so it travels with the backup too.
                val cloudFine = try {
                    firestoreRepo.pullFinePerAbsentDay()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (cloudFine != null) {
                    settingsRepo.updateFinePerAbsentDay(cloudFine)
                }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(false, "Restore failed: " + (e.localizedMessage ?: "could not restore from cloud"))
            } finally {
                _cloudProgress.value = ""
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
                val holidaySet = settingsRepo.holidaysFlow.first()
                val fineRate = settingsRepo.finePerAbsentDayFlow.first()
                val year = currentYear.value

                // Building a large JSON document is real work, so do it off the main thread.
                val json = withContext(Dispatchers.Default) {
                    val jsonRoot = JSONObject()
                    jsonRoot.put("version", 2)
                    jsonRoot.put("academicYear", year)
                    jsonRoot.put("exportedAt", System.currentTimeMillis())

                    val studentsArray = JSONArray()
                    students.forEach { st ->
                        val sObj = JSONObject()
                        sObj.put("year", st.year)
                        sObj.put("roll", st.roll)
                        sObj.put("name", st.name)
                        sObj.put("active", st.active)
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

                    // Holidays used to be left out of backups, so a restore lost them.
                    val holidaysArray = JSONArray()
                    holidaySet.sorted().forEach { holidaysArray.put(it) }
                    jsonRoot.put("holidays", holidaysArray)

                    // The fine rate used to be left out, so a restore silently reset it to 5.
                    jsonRoot.put("finePerAbsentDay", fineRate)

                    // Compact output (the old pretty-printed form was much larger and slower).
                    jsonRoot.toString()
                }
                onResult(json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("")
            }
        }
    }

    fun restoreBackupJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val fallbackYear = SimpleDateFormat("yyyy", Locale.US).format(Date())

                // Parse off the main thread; nothing is changed until parsing succeeds.
                val parsed = withContext(Dispatchers.Default) {
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
                            explicitYear.ifBlank { fallbackYear }
                        }
                        val r = (sObj.opt("roll") ?: "").toString().trim()
                        if (r.isBlank()) continue
                        val n = sObj.optString("name", "")
                        val a = sObj.optBoolean("active", true)
                        students.add(StudentEntity(year = y, roll = r, name = n, active = a))
                    }

                    val records = mutableListOf<AttendanceRecordEntity>()
                    var skippedRecords = 0
                    for (i in 0 until recordsArray.length()) {
                        val rObj = recordsArray.optJSONObject(i) ?: continue
                        val y = rObj.optString("year", explicitYear).ifBlank {
                            explicitYear.ifBlank { fallbackYear }
                        }
                        val d = rObj.optString("date", "").trim()
                        val r = (rObj.opt("roll") ?: "").toString().trim()
                        val rawStatus = (rObj.opt("status") ?: "P").toString().trim()
                        val st = when (rawStatus.uppercase()) {
                            "PRESENT", "P" -> "P"
                            "ABSENT", "A" -> "A"
                            else -> rawStatus.ifBlank { "P" }
                        }
                        // Dates must look like 2026-10-28. A malformed date would crash the
                        // Summary screen later, so such records are skipped (and counted).
                        val dateOk = d.length == 10 && d[4] == '-' && d[7] == '-'
                        if (d.isNotBlank() && r.isNotBlank()) {
                            if (dateOk) {
                                records.add(AttendanceRecordEntity(year = y, date = d, roll = r, status = st, isSynced = true))
                            } else {
                                skippedRecords++
                            }
                        }
                    }

                    val holidaysArray = jsonRoot.optJSONArray("holidays")
                    val holidays: Set<String>? = if (holidaysArray != null) {
                        (0 until holidaysArray.length())
                            .map { holidaysArray.optString(it, "") }
                            .filter { it.isNotBlank() }
                            .toSet()
                    } else {
                        null // older backup files have no holiday list; keep the current one
                    }

                    val fine: Int? = if (jsonRoot.has("finePerAbsentDay")) {
                        jsonRoot.optInt("finePerAbsentDay", -1).takeIf { it >= 0 }
                    } else {
                        null // older backup files have no fine rate; keep the current one
                    }

                    ParsedBackup(students, records, explicitYear, holidays, fine, skippedRecords)
                }

                // Throws, without touching local data, if the file has no usable data.
                repository.restoreBackupData(parsed.students, parsed.records)
                if (parsed.holidays != null) {
                    settingsRepo.setHolidays(parsed.holidays)
                }
                if (parsed.finePerAbsentDay != null) {
                    settingsRepo.updateFinePerAbsentDay(parsed.finePerAbsentDay)
                }

                val targetYear = when {
                    parsed.explicitYear.isNotBlank() -> parsed.explicitYear
                    parsed.students.any { it.year.isNotBlank() } -> parsed.students.first { it.year.isNotBlank() }.year
                    parsed.records.any { it.year.isNotBlank() } -> parsed.records.first { it.year.isNotBlank() }.year
                    currentYear.value.isNotBlank() -> currentYear.value
                    else -> fallbackYear
                }
                settingsRepo.updateAcademicYear(targetYear)
                loadAvailableYears()

                val skippedNote = if (parsed.skippedRecords > 0) {
                    " (${parsed.skippedRecords} records with an invalid date were skipped.)"
                } else {
                    ""
                }
                onResult(true, "Restored ${parsed.students.size} students and ${parsed.records.size} attendance records for academic year $targetYear successfully.$skippedNote")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Restore failed: ${e.message}")
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
