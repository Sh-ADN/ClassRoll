package com.aistudio.classroll.jkmxlp.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ClassRollRepository(
    private val dao: ClassRollDao,
    private val api: ApiService,
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

    // Sync Students from Server
    suspend fun syncStudents() {
        // Feature disabled - local only
    }

    // FIX: removed the loop that used to fill in a placeholder student for
    // every number between 1 and the highest roll in the CSV. If a roll
    // number was large or not part of a clean 1..N sequence, that loop
    // could try to generate a huge number of blank rows and freeze this
    // screen. Now it just imports exactly what's in the CSV.
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

    suspend fun deleteAttendanceRecord(year: String, date: String, roll: String) {
        dao.deleteAttendanceRecord(year, date, roll)
    }

    // Update single cell (from Register screen)
    suspend fun updateAttendanceCell(year: String, date: String, roll: String, status: String): Boolean {
        // Update locally
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

    // Replaces everything on the phone with the given backup.
    //  - Refuses an empty backup, so picking the wrong file (or restoring from a
    //    brand-new empty cloud account) can never wipe real data.
    //  - Runs as ONE database transaction: either the old data is fully replaced
    //    or nothing changes. Before, it was four separate steps, so an interruption
    //    in the middle could leave the app with the old data deleted and nothing restored.
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

    suspend fun fetchYears(): List<String> {
        // Local only fallback, or could query DB
        return emptyList()
    }
}
