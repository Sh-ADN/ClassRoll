package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import com.aistudio.classroll.jkmxlp.R
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeKey(key: String): String = key.replace("/", "_").replace(" ", "").trim()
    private fun studentDocId(year: String, roll: String): String = "${sanitizeKey(year)}_${sanitizeKey(roll)}"
    private fun attendanceDocId(year: String, date: String, roll: String): String = "${sanitizeKey(year)}_${sanitizeKey(date)}_${sanitizeKey(roll)}"

    fun observeStudents(year: String): Flow<List<FirestoreStudent>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/students"
        val query = db.collection("users").document(uid).collection("students")
            .whereEqualTo("year", year)

        query.snapshots()
            .map { snapshot -> snapshot.toObjects(FirestoreStudent::class.java) }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun saveStudent(student: StudentEntity) {
        val uid = requireUserId()
        val normYear = student.year.trim().ifBlank {
            java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
        }
        val normRoll = student.roll.trim()
        val docId = studentDocId(normYear, normRoll)
        val docRef = db.collection("users").document(uid).collection("students").document(docId)
        val data = hashMapOf(
            "userId" to uid,
            "year" to normYear,
            "roll" to normRoll,
            "name" to student.name.trim(),
            "active" to student.active,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudent(year: String, roll: String) {
        val uid = requireUserId()
        val docId = studentDocId(year, roll)
        val docRef = db.collection("users").document(uid).collection("students").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun saveAttendanceRecord(record: AttendanceRecordEntity) {
        val uid = requireUserId()
        val normYear = record.year.trim().ifBlank {
            java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
        }
        val normDate = record.date.trim()
        val normRoll = record.roll.trim()
        val normStatus = when (record.status.trim().uppercase()) {
            "PRESENT", "P" -> "P"
            "ABSENT", "A" -> "A"
            else -> record.status.trim().ifBlank { "P" }
        }
        val docId = attendanceDocId(normYear, normDate, normRoll)
        val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
        val data = hashMapOf(
            "userId" to uid,
            "year" to normYear,
            "date" to normDate,
            "roll" to normRoll,
            "status" to normStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Saves a whole group of attendance records at once (e.g. "Mark All Present")
    // using batched writes. Previously this was one awaited write per student,
    // one after another, which is slow and hangs while offline.
    suspend fun saveAttendanceBatch(records: List<AttendanceRecordEntity>) {
        val valid = records.filter { it.roll.isNotBlank() && it.date.isNotBlank() }
        if (valid.isEmpty()) return
        val uid = requireUserId()
        val attendanceCol = db.collection("users").document(uid).collection("attendance")
        val tasks = mutableListOf<Task<Void>>()
        valid.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val y = record.year.trim().ifBlank {
                    java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
                }
                val d = record.date.trim()
                val r = record.roll.trim()
                val st = when (record.status.trim().uppercase()) {
                    "PRESENT", "P" -> "P"
                    "ABSENT", "A" -> "A"
                    else -> record.status.trim().ifBlank { "P" }
                }
                batch.set(
                    attendanceCol.document(attendanceDocId(y, d, r)),
                    hashMapOf(
                        "userId" to uid,
                        "year" to y,
                        "date" to d,
                        "roll" to r,
                        "status" to st,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tasks.add(batch.commit())
        }
        try {
            tasks.forEach { it.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, attendanceCol.path)
            throw e
        }
    }

    suspend fun deleteAttendanceRecord(year: String, date: String, roll: String) {
        val uid = requireUserId()
        val docId = attendanceDocId(year, date, roll)
        val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun clearAttendanceForDate(year: String, date: String) {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("attendance")
        try {
            val snapshot = colRef
                .whereEqualTo("year", year)
                .whereEqualTo("date", date)
                .get()
                .await()
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    suspend fun saveUserSettings(academicYear: String, appTheme: String, holidays: Set<String>? = null) {
        try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("settings").document("user_config")
            val data = hashMapOf<String, Any>(
                "userId" to uid,
                "academicYear" to academicYear.trim(),
                "appTheme" to appTheme.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (holidays != null) {
                data["holidays"] = holidays.sorted()
            }
            // merge, so a call that does not include holidays never erases them
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignore settings sync failure
        }
    }

    // Returns the holiday list stored in the cloud, or null if the cloud copy
    // has none (so a restore never wipes local holidays with "nothing").
    suspend fun pullHolidays(): Set<String>? {
        val uid = requireUserId()
        return try {
            val snap = db.collection("users").document(uid)
                .collection("settings").document("user_config").get().await()
            (snap.get("holidays") as? List<*>)?.mapNotNull { it?.toString() }?.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncLocalToCloud(
        students: List<StudentEntity>,
        attendance: List<AttendanceRecordEntity>,
        academicYear: String = "",
        appTheme: String = "SYSTEM",
        holidays: Set<String>? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val uid = requireUserId()
        val defaultYear = academicYear.ifBlank {
            java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
        }

        // Save user settings so academic year and holidays survive data clearing
        saveUserSettings(defaultYear, appTheme, holidays)

        val studentsCol = db.collection("users").document(uid).collection("students")
        val attendanceCol = db.collection("users").document(uid).collection("attendance")

        // Skip rows the security rules would reject anyway. A single rejected
        // row makes Firestore reject its whole batch of up to 450 records.
        val validStudents = students.filter { it.roll.isNotBlank() && it.name.isNotBlank() }
        val validAttendance = attendance.filter { it.roll.isNotBlank() && it.date.isNotBlank() }

        // Queue every batch first and only then wait for them. The Firestore SDK
        // pipelines queued batches over one connection, so this avoids paying a
        // full network round-trip for each group of 450 records, one after another.
        val pending = mutableListOf<Task<Void>>()

        validStudents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { student ->
                val y = student.year.trim().ifBlank { defaultYear }
                val r = student.roll.trim()
                val doc = studentsCol.document(studentDocId(y, r))
                batch.set(
                    doc,
                    hashMapOf(
                        "userId" to uid,
                        "year" to y,
                        "roll" to r,
                        "name" to student.name.trim(),
                        "active" to student.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            pending.add(batch.commit())
        }

        validAttendance.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val y = record.year.trim().ifBlank { defaultYear }
                val d = record.date.trim()
                val r = record.roll.trim()
                val s = when (record.status.trim().uppercase()) {
                    "PRESENT", "P" -> "P"
                    "ABSENT", "A" -> "A"
                    else -> record.status.trim().ifBlank { "P" }
                }
                val doc = attendanceCol.document(attendanceDocId(y, d, r))
                batch.set(
                    doc,
                    hashMapOf(
                        "userId" to uid,
                        "year" to y,
                        "date" to d,
                        "roll" to r,
                        "status" to s,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            pending.add(batch.commit())
        }

        val total = pending.size
        onProgress(0, total)
        pending.forEachIndexed { index, task ->
            task.await()
            onProgress(index + 1, total)
        }
    }

    suspend fun pullCloudToLocal(): Triple<List<StudentEntity>, List<AttendanceRecordEntity>, String?> {
        val uid = requireUserId()
        val studentsCol = db.collection("users").document(uid).collection("students")
        val attendanceCol = db.collection("users").document(uid).collection("attendance")
        val settingsDoc = db.collection("users").document(uid).collection("settings").document("user_config")

        // Fetch settings, students and attendance at the same time instead of
        // three network round-trips one after another.
        val (cloudYear, studentsSnapshot, attendanceSnapshot) = coroutineScope {
            val settingsJob = async {
                try {
                    settingsDoc.get().await().getString("academicYear")?.takeIf { it.isNotBlank() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            val studentsJob = async { studentsCol.get().await() }
            val attendanceJob = async { attendanceCol.get().await() }
            Triple(settingsJob.await(), studentsJob.await(), attendanceJob.await())
        }

        val students = studentsSnapshot.documents.mapNotNull { doc ->
            val docIdParts = doc.id.split("_")
            val y = (doc.get("year") ?: if (docIdParts.size >= 2) docIdParts[0] else null)?.toString()?.trim()
            val r = (doc.get("roll") ?: if (docIdParts.size >= 2) docIdParts[1] else null)?.toString()?.trim()
            if (y.isNullOrBlank() || r.isNullOrBlank()) return@mapNotNull null
            val n = doc.getString("name") ?: doc.get("name")?.toString() ?: ""
            val a = doc.getBoolean("active") ?: true
            StudentEntity(year = y, roll = r, name = n, active = a)
        }

        val records = attendanceSnapshot.documents.mapNotNull { doc ->
            val docIdParts = doc.id.split("_")
            val y = (doc.get("year") ?: if (docIdParts.size >= 3) docIdParts[0] else null)?.toString()?.trim()
            val d = (doc.get("date") ?: if (docIdParts.size >= 3) docIdParts[1] else null)?.toString()?.trim()
            val r = (doc.get("roll") ?: if (docIdParts.size >= 3) docIdParts[2] else null)?.toString()?.trim()
            if (y.isNullOrBlank() || d.isNullOrBlank() || r.isNullOrBlank()) return@mapNotNull null
            val rawStatus = (doc.get("status") ?: "P").toString().trim()
            val s = when (rawStatus.uppercase()) {
                "PRESENT", "P" -> "P"
                "ABSENT", "A" -> "A"
                else -> rawStatus.ifBlank { "P" }
            }
            AttendanceRecordEntity(year = y, date = d, roll = r, status = s, isSynced = true)
        }

        return Triple(students, records, cloudYear)
    }
}
