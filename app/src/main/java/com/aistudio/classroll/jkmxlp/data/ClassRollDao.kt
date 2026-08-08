package com.aistudio.classroll.jkmxlp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassRollDao {
    @Query("SELECT * FROM students WHERE year = :year AND active = 1 ORDER BY CAST(roll AS INTEGER) ASC")
    fun getStudentsForYear(year: String): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Query("SELECT * FROM attendance_records WHERE year = :year AND date = :date")
    fun getAttendanceForDate(year: String, date: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE year = :year AND date LIKE :monthPrefix || '%'")
    fun getAttendanceForMonth(year: String, monthPrefix: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE year = :year")
    fun getAllAttendanceForYear(year: String): Flow<List<AttendanceRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecordEntity>)

    @Query("SELECT * FROM attendance_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<AttendanceRecordEntity>

    @Query("UPDATE attendance_records SET isSynced = 1 WHERE year = :year AND date = :date AND roll = :roll")
    suspend fun markSynced(year: String, date: String, roll: String)

    @Query("DELETE FROM students WHERE year = :year AND roll = :roll")
    suspend fun deleteStudent(year: String, roll: String)

    @Query("DELETE FROM attendance_records WHERE year = :year")
    suspend fun clearAttendanceForYear(year: String)

    @Query("DELETE FROM students WHERE year = :year")
    suspend fun clearStudentsForYear(year: String)

    @Query("SELECT * FROM students")
    suspend fun getAllStudentsAllYears(): List<StudentEntity>

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllAttendanceAllYears(): List<AttendanceRecordEntity>

    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("DELETE FROM attendance_records")
    suspend fun clearAllAttendance()
}
