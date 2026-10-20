package com.aistudio.classroll.jkmxlp.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YearsResponse(val years: List<String>)

@JsonClass(generateAdapter = true)
data class RemoteStudent(val roll: String, val name: String, val active: Boolean = true)

@JsonClass(generateAdapter = true)
data class StudentsResponse(val students: List<RemoteStudent>)

@JsonClass(generateAdapter = true)
data class ImportStudentsRequest(val token: String, val year: String, val students: List<RemoteStudent>)

@JsonClass(generateAdapter = true)
data class RemoteAttendanceRecord(val roll: String, val status: String)

@JsonClass(generateAdapter = true)
data class SubmitAttendanceRequest(val token: String, val year: String, val date: String, val records: List<RemoteAttendanceRecord>)

@JsonClass(generateAdapter = true)
data class UpdateCellRequest(val token: String, val year: String, val date: String, val roll: String, val status: String)

@JsonClass(generateAdapter = true)
data class RemoteAttendanceFullRecord(val date: String, val roll: String, val name: String?, val status: String)

@JsonClass(generateAdapter = true)
data class AttendanceResponse(val records: List<RemoteAttendanceFullRecord>)

@JsonClass(generateAdapter = true)
data class SimpleResponse(val success: Boolean, val error: String? = null)
