package com.aistudio.classroll.jkmxlp.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ClassRollRepository(
    private val dao: ClassRollDao,
    private val settingsRepo: SettingsRepository,
    private val database: AppDatabase? = null
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

    suspend fun importStudents(year: String, students: List<RemoteStudent>): String {
        return try {
            val entities = students.map {
                StudentEntity(year = year, roll = it.roll, name = it.name, active = it.active)
            }
            dao.insertStudents(entities)
            "Success"
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Unknown error"
        }
    }

    suspend fun submitAttendance(year: String, date: String, records: List<AttendanceRecordEntity>): Boolean {
        dao.insertAttendanceRecords(records)
        return true
    }

    suspend fun addOrUpdateStudent(year: String, roll: String, name: String, active: Boolean = true) {
        dao.insertStudents(listOf(StudentEntity(year = year, roll = roll, name = name, active = active)))
    }

    // Deletes both the student and all of their attendance records for that year
    // inside a single transaction so no orphaned attendance records remain.
    suspend fun deleteStudent(year: String, roll: String) {
        val deleteBoth: suspend () -> Unit = {
            dao.deleteStudent(year, roll)
            dao.deleteAttendanceForStudent(year, roll)
        }
        val db = database
        if (db != null) {
            db.withTransaction { deleteBoth() }
        } else {
            deleteBoth()
        }
    }

    suspend fun deleteAttendanceRecord(year: String, date: String, roll: String) {
        dao.deleteAttendanceRecord(year, date, roll)
    }

    suspend fun updateAttendanceCell(year: String, date: String, roll: String, status: String): Boolean {
        dao.insertAttendanceRecords(listOf(AttendanceRecordEntity(year, date, roll, status, isSynced = true)))
        return true
    }

    suspend fun clearAttendanceForDate(year: String, date: String) {
        dao.clearAttendanceForDate(year, date)
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
        if (students.isEmpty() && records.isEmpty()) {
            throw IllegalArgumentException(
                "The backup contains no students or attendance records, so nothing was changed on this phone."
            )
        }
        val replaceAll: suspend () -> Unit = {
            dao.clearAllStudents()
            dao.clearAllAttendance()
            dao.insertStudents(students)
            dao.insertAttendanceRecords(records)
        }
        val db = database
        if (db != null) {
            db.withTransaction { replaceAll() }
        } else {
            replaceAll()
        }
    }

    suspend fun updateAppTheme(theme: String) {
        settingsRepo.updateAppTheme(theme)
    }
}
