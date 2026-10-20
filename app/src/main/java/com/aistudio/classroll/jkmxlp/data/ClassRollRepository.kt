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

    suspend fun syncStudents() {
        // no-op or optional sync
    }

    suspend fun importStudents(year: String, students: List<RemoteStudent>): String {
        val entities = students.map {
            StudentEntity(
                year = year,
                roll = it.roll,
                name = it.name,
                active = it.active
            )
        }
        dao.insertStudents(entities)

        val webAppUrl = settingsRepo.webAppUrlFlow.first()
        val token = settingsRepo.appsScriptTokenFlow.first()
        if (webAppUrl.isNotBlank() && token.isNotBlank()) {
            try {
                val response = api.importStudents(
                    url = webAppUrl,
                    request = ImportStudentsRequest(token = token, year = year, students = students)
                )
                if (!response.success) {
                    return response.error ?: "Import failed on server"
                }
            } catch (e: Exception) {
                return "Saved locally. Server sync failed: ${e.message}"
            }
        }
        return "Success"
    }

    suspend fun submitAttendance(
        year: String,
        date: String,
        records: List<AttendanceRecordEntity>
    ): Boolean {
        dao.insertAttendanceRecords(records)

        val webAppUrl = settingsRepo.webAppUrlFlow.first()
        val token = settingsRepo.appsScriptTokenFlow.first()
        if (webAppUrl.isNotBlank() && token.isNotBlank()) {
            try {
                val remoteRecords = records.map {
                    RemoteAttendanceRecord(roll = it.roll, status = it.status)
                }
                api.submitAttendance(
                    url = webAppUrl,
                    request = SubmitAttendanceRequest(
                        token = token,
                        year = year,
                        date = date,
                        records = remoteRecords
                    )
                )
            } catch (e: Exception) {
                // Return true since local save succeeded
                return true
            }
        }
        return true
    }

    suspend fun addOrUpdateStudent(year: String, roll: String, name: String) {
        dao.insertStudents(listOf(StudentEntity(year = year, roll = roll, name = name, active = true)))
    }

    suspend fun deleteStudent(year: String, roll: String) {
        dao.deleteStudent(year = year, roll = roll)
    }

    suspend fun deleteAttendanceRecord(year: String, date: String, roll: String) {
        dao.deleteAttendanceRecord(year = year, date = date, roll = roll)
    }

    suspend fun updateAttendanceCell(
        year: String,
        date: String,
        roll: String,
        status: String
    ): Boolean {
        dao.insertAttendanceRecords(
            listOf(
                AttendanceRecordEntity(
                    year = year,
                    date = date,
                    roll = roll,
                    status = status
                )
            )
        )

        val webAppUrl = settingsRepo.webAppUrlFlow.first()
        val token = settingsRepo.appsScriptTokenFlow.first()
        if (webAppUrl.isNotBlank() && token.isNotBlank()) {
            try {
                api.updateAttendanceCell(
                    url = webAppUrl,
                    request = UpdateCellRequest(
                        token = token,
                        year = year,
                        date = date,
                        roll = roll,
                        status = status
                    )
                )
            } catch (e: Exception) {
                return true
            }
        }
        return true
    }

    suspend fun clearAttendanceForDate(year: String, date: String) {
        dao.clearAttendanceForDate(year = year, date = date)
    }

    suspend fun clearAttendanceForYear(year: String) {
        dao.clearAttendanceForYear(year = year)
    }

    suspend fun clearStudentsForYear(year: String) {
        dao.clearStudentsForYear(year = year)
    }

    suspend fun getAllStudentsAllYears(): List<StudentEntity> {
        return dao.getAllStudentsAllYears()
    }

    suspend fun getAllAttendanceAllYears(): List<AttendanceRecordEntity> {
        return dao.getAllAttendanceAllYears()
    }

    suspend fun restoreBackupData(
        students: List<StudentEntity>,
        records: List<AttendanceRecordEntity>
    ) {
        if (students.isEmpty() && records.isEmpty()) {
            throw IllegalArgumentException("The backup contains no students or attendance records, so nothing was changed on this phone.")
        }
        if (database != null) {
            database.withTransaction {
                dao.clearAllStudents()
                dao.clearAllAttendance()
                dao.insertStudents(students)
                dao.insertAttendanceRecords(records)
            }
        } else {
            dao.clearAllStudents()
            dao.clearAllAttendance()
            dao.insertStudents(students)
            dao.insertAttendanceRecords(records)
        }
    }

    suspend fun updateAppTheme(theme: String) {
        settingsRepo.updateAppTheme(theme)
    }

    suspend fun fetchYears(): List<String> {
        val webAppUrl = settingsRepo.webAppUrlFlow.first()
        val token = settingsRepo.appsScriptTokenFlow.first()
        if (webAppUrl.isNotBlank() && token.isNotBlank()) {
            try {
                val response = api.getYears(url = webAppUrl, token = token)
                return response.years
            } catch (e: Exception) {
                // fallback
            }
        }
        return emptyList()
    }
}
