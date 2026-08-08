package com.aistudio.classroll.jkmxlp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ClassRollRepository(
    private val dao: ClassRollDao,
    private val api: ApiService,
    private val settingsRepo: SettingsRepository
) {
    fun getStudentsForYear(year: String): Flow<List<StudentEntity>> {
        return dao.getStudentsForYear(year)
    }

    fun getAttendanceForDate(year: String, date: String): Flow<List<AttendanceRecordEntity>> {
        return dao.getAttendanceForDate(year, date)
    }
    
    fun getAttendanceForMonth(year: String, month: String): Flow<List<AttendanceRecordEntity>> {
        return dao.getAttendanceForMonth(year, month)
    }

    fun getAllAttendanceForYear(year: String): Flow<List<AttendanceRecordEntity>> {
        return dao.getAllAttendanceForYear(year)
    }

    // Sync Students from Server
    suspend fun syncStudents() {
        // Feature disabled - local only
    }

    suspend fun importStudents(year: String, students: List<RemoteStudent>): String {
        return try {
            val maxRoll = students.maxOfOrNull { it.roll.toIntOrNull() ?: 0 } ?: 0
            val importedRolls = students.mapNotNull { it.roll.toIntOrNull() }.toSet()
            
            val allStudents = students.toMutableList()
            for (i in 1..maxRoll) {
                if (i !in importedRolls) {
                    allStudents.add(RemoteStudent(roll = i.toString(), name = "", active = true))
                }
            }

            val entities = allStudents.map {
                StudentEntity(year = year, roll = it.roll, name = it.name, active = it.active)
            }
            dao.insertStudents(entities)
            "Success"
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown error"
        }
    }

    // Submit a single day's attendance
    suspend fun submitAttendance(year: String, date: String, records: List<AttendanceRecordEntity>): Boolean {
        // Save locally 
        dao.insertAttendanceRecords(records)
        return true
    }

    suspend fun addOrUpdateStudent(year: String, roll: String, name: String) {
        dao.insertStudents(listOf(StudentEntity(year = year, roll = roll, name = name, active = true)))
    }

    suspend fun deleteStudent(year: String, roll: String) {
        dao.deleteStudent(year, roll)
    }

    // Update single cell (from Register screen)
    suspend fun updateAttendanceCell(year: String, date: String, roll: String, status: String): Boolean {
        // Update locally
        dao.insertAttendanceRecords(listOf(AttendanceRecordEntity(year, date, roll, status, isSynced = true)))
        return true
    }
    
    suspend fun clearAttendanceForYear(year: String) {
        dao.clearAttendanceForYear(year)
    }

    suspend fun clearStudentsForYear(year: String) {
        dao.clearStudentsForYear(year)
    }

    suspend fun getAllStudentsAllYears(): List<StudentEntity> {
        return dao.getAllStudentsAllYears()
    }

    suspend fun getAllAttendanceAllYears(): List<AttendanceRecordEntity> {
        return dao.getAllAttendanceAllYears()
    }

    suspend fun restoreBackupData(students: List<StudentEntity>, records: List<AttendanceRecordEntity>) {
        dao.clearAllStudents()
        dao.clearAllAttendance()
        dao.insertStudents(students)
        dao.insertAttendanceRecords(records)
    }

    suspend fun updateAppTheme(theme: String) {
        settingsRepo.updateAppTheme(theme)
    }

    suspend fun fetchYears(): List<String> {
        // Local only fallback, or could query DB
        return emptyList()
    }
}
