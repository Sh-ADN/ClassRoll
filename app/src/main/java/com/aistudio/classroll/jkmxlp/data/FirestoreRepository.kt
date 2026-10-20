package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import com.aistudio.classroll.jkmxlp.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirestoreRepository(
    private val db: FirebaseFirestore
) {
    private val auth: FirebaseAuth = Firebase.auth

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    )

    private fun requireUserId(): String {
        val currentUser: FirebaseUser? = auth.currentUser
        return currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeKey(key: String): String {
        return key.replace("/", "_").replace(" ", "").trim()
    }

    private fun studentDocId(year: String, roll: String): String {
        return "${sanitizeKey(year)}_${sanitizeKey(roll)}"
    }

    private fun attendanceDocId(year: String, date: String, roll: String): String {
        return "${sanitizeKey(year)}_${sanitizeKey(date)}_${sanitizeKey(roll)}"
    }

    fun observeStudents(year: String): Flow<List<FirestoreStudent>> = callbackFlow {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        val listener = db.collection("users")
            .document(uid)
            .collection("students")
            .whereEqualTo("year", year)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val students = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(FirestoreStudent::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(students)
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveStudent(student: StudentEntity) {
        val uid = requireUserId()
        val normYear = student.year.trim().ifBlank {
            SimpleDateFormat("yyyy", Locale.US).format(Date())
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
            docRef.set(data, SetOptions.merge()).await()
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
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun saveAttendanceRecord(record: AttendanceRecordEntity) {
        val uid = requireUserId()
        val docId = attendanceDocId(record.year, record.date, record.roll)
        val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
        val data = hashMapOf(
            "userId" to uid,
            "year" to record.year,
            "date" to record.date,
            "roll" to record.roll,
            "status" to record.status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun saveAttendanceBatch(records: List<AttendanceRecordEntity>) {
        if (records.isEmpty()) return
        val uid = requireUserId()
        records.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val docId = attendanceDocId(record.year, record.date, record.roll)
                val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
                val data = hashMapOf(
                    "userId" to uid,
                    "year" to record.year,
                    "date" to record.date,
                    "roll" to record.roll,
                    "status" to record.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, data, SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    suspend fun deleteAttendanceRecord(year: String, date: String, roll: String) {
        val uid = requireUserId()
        val docId = attendanceDocId(year, date, roll)
        val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun clearAttendanceForDate(year: String, date: String) {
        val uid = requireUserId()
        val query = db.collection("users").document(uid).collection("attendance")
            .whereEqualTo("year", year)
            .whereEqualTo("date", date)
            .get().await()

        query.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    suspend fun saveUserSettings(
        academicYear: String,
        appTheme: String,
        holidays: Set<String> = emptySet()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("user_config")
        val data = hashMapOf(
            "userId" to uid,
            "academicYear" to academicYear,
            "appTheme" to appTheme,
            "holidays" to holidays.toList(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun pullHolidays(): Set<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("settings").document("user_config")
            val snapshot = docRef.get().await()
            val list = snapshot.get("holidays") as? List<*>
            list?.filterIsInstance<String>()?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun syncLocalToCloud(
        students: List<StudentEntity>,
        attendance: List<AttendanceRecordEntity>,
        academicYear: String,
        appTheme: String,
        holidays: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        val total = students.size + attendance.size + 1
        var done = 0
        val uid = requireUserId()

        // 1. Save user config
        val configRef = db.collection("users").document(uid).collection("settings").document("user_config")
        configRef.set(
            hashMapOf(
                "userId" to uid,
                "academicYear" to academicYear,
                "appTheme" to appTheme,
                "holidays" to holidays.toList(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        done++
        onProgress(done, total)

        // 2. Batch students
        students.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { student ->
                val docId = studentDocId(student.year, student.roll)
                val docRef = db.collection("users").document(uid).collection("students").document(docId)
                batch.set(
                    docRef,
                    hashMapOf(
                        "userId" to uid,
                        "year" to student.year,
                        "roll" to student.roll,
                        "name" to student.name,
                        "active" to student.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
            done += chunk.size
            onProgress(done, total)
        }

        // 3. Batch attendance
        attendance.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val docId = attendanceDocId(record.year, record.date, record.roll)
                val docRef = db.collection("users").document(uid).collection("attendance").document(docId)
                batch.set(
                    docRef,
                    hashMapOf(
                        "userId" to uid,
                        "year" to record.year,
                        "date" to record.date,
                        "roll" to record.roll,
                        "status" to record.status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
            done += chunk.size
            onProgress(done, total)
        }
    }

    suspend fun pullCloudToLocal(): Triple<List<StudentEntity>, List<AttendanceRecordEntity>, String?> = coroutineScope {
        val uid = requireUserId()
        val settingsRef = db.collection("users").document(uid).collection("settings").document("user_config")
        val studentsCol = db.collection("users").document(uid).collection("students")
        val attendanceCol = db.collection("users").document(uid).collection("attendance")

        val settingsJob = async { settingsRef.get().await() }
        val studentsJob = async { studentsCol.get().await() }
        val attendanceJob = async { attendanceCol.get().await() }

        val settingsSnap = settingsJob.await()
        val studentsSnap = studentsJob.await()
        val attendanceSnap = attendanceJob.await()

        val cloudYear = settingsSnap.getString("academicYear")

        val students = studentsSnap.documents.mapNotNull { doc ->
            val year = doc.getString("year") ?: return@mapNotNull null
            val roll = doc.getString("roll") ?: return@mapNotNull null
            val name = doc.getString("name") ?: ""
            val active = doc.getBoolean("active") ?: true
            StudentEntity(year = year, roll = roll, name = name, active = active)
        }

        val records = attendanceSnap.documents.mapNotNull { doc ->
            val year = doc.getString("year") ?: return@mapNotNull null
            val date = doc.getString("date") ?: return@mapNotNull null
            val roll = doc.getString("roll") ?: return@mapNotNull null
            val status = doc.getString("status") ?: "PRESENT"
            AttendanceRecordEntity(year = year, date = date, roll = roll, status = status, isSynced = true)
        }

        Triple(students, records, cloudYear)
    }
}
