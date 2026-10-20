package com.aistudio.classroll.jkmxlp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreStudent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val year: String = "",
    val roll: String = "",
    val name: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val year: String = "",
    val date: String = "",
    val roll: String = "",
    val status: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreUserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val academicYear: String = "",
    val appTheme: String = "SYSTEM",
    @ServerTimestamp val updatedAt: Timestamp? = null
)
