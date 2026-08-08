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
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "classroll_db").build()
    private val settingsRepo = SettingsRepository(application)
    val repository = ClassRollRepository(db.classRollDao(), NetworkModule.apiService, settingsRepo)

    val webAppUrl = settingsRepo.webAppUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://script.google.com/macros/s/AKfycbzTDiNJh4LEaIah19SVFaf6JlESbW5tf2ElwaMULTDENIAlXFOFI4QAXEmV1nYwrVdA/exec")
    val currentYear = settingsRepo.academicYearFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val appTheme = settingsRepo.appThemeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    private val _availableYears = MutableStateFlow<List<String>>(emptyList())
    val availableYears: StateFlow<List<String>> = _availableYears.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

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
            _isSyncing.value = false
            onResult(success)
        }
    }
    
    fun updateCell(date: String, roll: String, status: String) {
        viewModelScope.launch {
            val year = currentYear.value
            repository.updateAttendanceCell(year, date, roll, status)
        }
    }

    fun addOrUpdateStudent(roll: String, name: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.addOrUpdateStudent(year, roll, name)
            }
        }
    }

    fun deleteStudent(roll: String) {
        viewModelScope.launch {
            val year = currentYear.value
            if (year.isNotBlank()) {
                repository.deleteStudent(year, roll)
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
                val studentsArray = jsonRoot.getJSONArray("students")
                val recordsArray = jsonRoot.getJSONArray("attendanceRecords")

                val students = mutableListOf<StudentEntity>()
                for (i in 0 until studentsArray.length()) {
                    val sObj = studentsArray.getJSONObject(i)
                    students.add(
                        StudentEntity(
                            year = sObj.getString("year"),
                            roll = sObj.getString("roll"),
                            name = sObj.optString("name", ""),
                            active = sObj.optBoolean("active", true)
                        )
                    )
                }

                val records = mutableListOf<AttendanceRecordEntity>()
                for (i in 0 until recordsArray.length()) {
                    val rObj = recordsArray.getJSONObject(i)
                    records.add(
                        AttendanceRecordEntity(
                            year = rObj.getString("year"),
                            date = rObj.getString("date"),
                            roll = rObj.getString("roll"),
                            status = rObj.getString("status"),
                            isSynced = true
                        )
                    )
                }

                repository.restoreBackupData(students, records)
                onResult(true, "Restored ${students.size} students and ${records.size} attendance records successfully.")
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
