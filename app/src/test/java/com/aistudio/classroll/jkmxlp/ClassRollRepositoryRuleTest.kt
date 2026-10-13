package com.aistudio.classroll.jkmxlp

import com.aistudio.classroll.jkmxlp.base.FirestoreEmulatorTestBase
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.FirestoreRepository
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClassRollRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticatedUser_cannotSaveStudent() = runBlocking {
        auth.signOut()
        val repo = FirestoreRepository(firestore)
        try {
            repo.saveStudent(StudentEntity(year = "2026", roll = "1", name = "Test Student", active = true))
            fail("Expected unauthenticated write to throw an exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e.message?.contains("User must be signed in") == true)
        }
    }

    @Test
    fun authenticatedUser_canSaveAndPullStudent() = runBlocking {
        val uid = signInTestUser("teacher1@example.com")
        val repo = FirestoreRepository(firestore)

        val student = StudentEntity(year = "2026", roll = "101", name = "Alice Johnson", active = true)
        repo.saveStudent(student)

        val (students, _) = repo.pullCloudToLocal()
        val match = students.find { it.year == "2026" && it.roll == "101" }
        assertEquals("Alice Johnson", match?.name)
    }

    @Test
    fun authenticatedUser_canSaveAndPullAttendance() = runBlocking {
        val uid = signInTestUser("teacher2@example.com")
        val repo = FirestoreRepository(firestore)

        val record = AttendanceRecordEntity(year = "2026", date = "2026-10-13", roll = "102", status = "P")
        repo.saveAttendanceRecord(record)

        val (_, attendance) = repo.pullCloudToLocal()
        val match = attendance.find { it.year == "2026" && it.date == "2026-10-13" && it.roll == "102" }
        assertEquals("P", match?.status)
    }
}
