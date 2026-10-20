package com.aistudio.classroll.jkmxlp.data

import com.google.firebase.Timestamp

data class FirestoreStudent(
    val id: String = "",
    val userId: String = "",
    val year: String = "",
    val roll: String = "",
    val name: String = "",
    val active: Boolean = true,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    // No-arg constructor for Firestore
    constructor() : this("", "", "", "", "", true, Timestamp.now(), Timestamp.now())
}

data class FirestoreAttendanceRecord(
    val id: String = "",
    val userId: String = "",
    val year: String = "",
    val date: String = "",
    val roll: String = "",
    val status: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "", "", "", "", Timestamp.now(), Timestamp.now())
}

data class FirestoreUserSettings(
    val id: String = "",
    val userId: String = "",
    val academicYear: String = "2025",
    val appTheme: String = "system",
    val updatedAt: Timestamp = Timestamp.now()
) {
    constructor() : this("", "", "2025", "system", Timestamp.now())
}
