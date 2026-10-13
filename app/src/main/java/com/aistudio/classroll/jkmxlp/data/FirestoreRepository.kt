package com.aistudio.classroll.jkmxlp.data

import android.content.Context
import com.aistudio.classroll.jkmxlp.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
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

    private fun studentDocId(year: String, roll: String): String = "${year}_$roll"
    private fun attendanceDocId(year: String, date: String, roll: String): String = "${year}_${date}_$roll"

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
        val docId = studentDocId(student.year, student.roll)
        val docRef = db.collection("users").document(uid).collection("students").document(docId)
        val data = hashMapOf(
            "userId" to uid,
            "year" to student.year,
            "roll" to student.roll,
            "name" to student.name,
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
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
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

    suspend fun syncLocalToCloud(
        students: List<StudentEntity>,
        attendance: List<AttendanceRecordEntity>
    ) {
        val uid = requireUserId()
        val studentsCol = db.collection("users").document(uid).collection("students")
        val attendanceCol = db.collection("users").document(uid).collection("attendance")

        // Batch write students
        students.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { student ->
                val doc = studentsCol.document(studentDocId(student.year, student.roll))
                batch.set(
                    doc,
                    hashMapOf(
                        "userId" to uid,
                        "year" to student.year,
                        "roll" to student.roll,
                        "name" to student.name,
                        "active" to student.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }

        // Batch write attendance
        attendance.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val doc = attendanceCol.document(attendanceDocId(record.year, record.date, record.roll))
                batch.set(
                    doc,
                    hashMapOf(
                        "userId" to uid,
                        "year" to record.year,
                        "date" to record.date,
                        "roll" to record.roll,
                        "status" to record.status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
    }

    suspend fun pullCloudToLocal(): Pair<List<StudentEntity>, List<AttendanceRecordEntity>> {
        val uid = requireUserId()
        val studentsCol = db.collection("users").document(uid).collection("students")
        val attendanceCol = db.collection("users").document(uid).collection("attendance")

        val studentsSnapshot = studentsCol.get().await()
        val students = studentsSnapshot.documents.mapNotNull { doc ->
            val y = doc.getString("year") ?: return@mapNotNull null
            val r = doc.getString("roll") ?: return@mapNotNull null
            val n = doc.getString("name") ?: ""
            val a = doc.getBoolean("active") ?: true
            StudentEntity(year = y, roll = r, name = n, active = a)
        }

        val attendanceSnapshot = attendanceCol.get().await()
        val records = attendanceSnapshot.documents.mapNotNull { doc ->
            val y = doc.getString("year") ?: return@mapNotNull null
            val d = doc.getString("date") ?: return@mapNotNull null
            val r = doc.getString("roll") ?: return@mapNotNull null
            val s = doc.getString("status") ?: return@mapNotNull null
            AttendanceRecordEntity(year = y, date = d, roll = r, status = s, isSynced = true)
        }

        return Pair(students, records)
    }
}
