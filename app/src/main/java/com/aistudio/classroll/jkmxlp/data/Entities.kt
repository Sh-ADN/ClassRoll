package com.aistudio.classroll.jkmxlp.data

import androidx.room.Entity

@Entity(tableName = "students", primaryKeys = ["year", "roll"])
data class StudentEntity(
    val year: String,
    val roll: String,
    val name: String,
    val active: Boolean = true
)

@Entity(tableName = "attendance_records", primaryKeys = ["year", "date", "roll"])
data class AttendanceRecordEntity(
    val year: String,
    val date: String,
    val roll: String,
    val status: String,
    val isSynced: Boolean = true
)
