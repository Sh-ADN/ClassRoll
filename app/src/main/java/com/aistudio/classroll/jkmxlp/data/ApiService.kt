package com.aistudio.classroll.jkmxlp.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {
    @GET
    suspend fun getYears(
        @Url url: String,
        @Query("action") action: String = "getYears",
        @Query("token") token: String
    ): YearsResponse

    @GET
    suspend fun getStudents(
        @Url url: String,
        @Query("action") action: String = "getStudents",
        @Query("token") token: String,
        @Query("year") year: String
    ): StudentsResponse

    @GET
    suspend fun getAttendance(
        @Url url: String,
        @Query("action") action: String = "getAttendance",
        @Query("token") token: String,
        @Query("year") year: String,
        @Query("month") month: String
    ): AttendanceResponse

    @POST
    suspend fun importStudents(
        @Url url: String,
        @Query("action") action: String = "importStudents",
        @Body request: ImportStudentsRequest
    ): SimpleResponse

    @POST
    suspend fun submitAttendance(
        @Url url: String,
        @Query("action") action: String = "submitAttendance",
        @Body request: SubmitAttendanceRequest
    ): SimpleResponse

    @POST
    suspend fun updateAttendanceCell(
        @Url url: String,
        @Query("action") action: String = "updateAttendanceCell",
        @Body request: UpdateCellRequest
    ): SimpleResponse
}
