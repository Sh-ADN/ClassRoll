package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ClassRollViewModel) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val activeStudents = remember(students) { students.filter { it.name.isNotBlank() } }
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    var currentIndex by remember { mutableStateOf(0) }
    val records = remember { mutableStateListOf<AttendanceRecordEntity>() }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }
    var submissionMessage by remember { mutableStateOf("") }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
                        currentIndex = 0
                        records.clear()
                        submissionMessage = ""
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (activeStudents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No students found. Import students first.")
        }
        return
    }

    if (currentIndex >= activeStudents.size) {
        val presentCount = records.count { it.status == "P" }
        val absentCount = records.count { it.status == "A" }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Summary", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text("Present: $presentCount", style = MaterialTheme.typography.bodyLarge)
            Text("Absent: $absentCount", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { 
                 submissionMessage = "Submitting..."
                viewModel.submitAttendance(date, records) { success ->
                    submissionMessage = if (success) "Saved successfully!" else "Failed to save."
                }
            }) {
                Text("Submit")
            }
            if (currentIndex > 0) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    if (currentIndex > 0) currentIndex--
                    if (records.isNotEmpty()) records.removeAt(records.size - 1)
                    submissionMessage = ""
                }) {
                    Text("Undo Last")
                }
            }
            if (submissionMessage.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(submissionMessage)
            }
        }
        return
    }

    val currentStudent = activeStudents[currentIndex]

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayDate = remember(date) { formatDisplayDate(date) }
                    Text("Attendance for $displayDate", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
                Text("${currentIndex + 1} / ${activeStudents.size}", style = MaterialTheme.typography.bodyLarge)
            }
            if (currentIndex > 0) {
                TextButton(onClick = {
                    if (currentIndex > 0) currentIndex--
                    if (records.isNotEmpty()) records.removeAt(records.size - 1)
                }) {
                    Text("Undo")
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Progress bar
        LinearProgressIndicator(
            progress = { (currentIndex.toFloat() / activeStudents.size.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Quick Batch Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                records.clear()
                activeStudents.forEach { s ->
                    records.add(AttendanceRecordEntity(currentYear, date, s.roll, "P", false))
                }
                currentIndex = activeStudents.size
            }) {
                Text("Mark All Present")
            }
            
            OutlinedButton(onClick = {
                records.clear()
                activeStudents.forEach { s ->
                    records.add(AttendanceRecordEntity(currentYear, date, s.roll, "A", false))
                }
                currentIndex = activeStudents.size
            }) {
                Text("Mark All Absent")
            }
        }

        Spacer(Modifier.height(16.dp))
        
        key(currentStudent.roll) {
            SwipeableStudentCard(
                student = currentStudent,
                onSwiped = { status ->
                    records.add(AttendanceRecordEntity(currentYear, date, currentStudent.roll, status, false))
                    currentIndex++
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.height(16.dp))

        // Tap action buttons for Present/Absent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    records.add(AttendanceRecordEntity(currentYear, date, currentStudent.roll, "P", false))
                    currentIndex++
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f).height(50.dp).padding(end = 8.dp)
            ) {
                Text("Present (Swipe Right)", color = Color.White)
            }

            Button(
                onClick = {
                    records.add(AttendanceRecordEntity(currentYear, date, currentStudent.roll, "A", false))
                    currentIndex++
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.weight(1f).height(50.dp).padding(start = 8.dp)
            ) {
                Text("Absent (Swipe Left)", color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableStudentCard(
    student: StudentEntity,
    onSwiped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            dismissValue != SwipeToDismissBoxValue.Settled
        }
    )

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onSwiped("P")
            SwipeToDismissBoxValue.EndToStart -> onSwiped("A")
            else -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                else -> Color.LightGray
            }
            val alignment = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(Modifier.fillMaxSize().background(color, MaterialTheme.shapes.large).padding(24.dp), contentAlignment = alignment) {
                Text(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.StartToEnd -> "PRESENT"
                        SwipeToDismissBoxValue.EndToStart -> "ABSENT"
                        else -> ""
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(student.roll, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(24.dp))
                Text(student.name, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

private fun formatDisplayDate(dateString: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateString) ?: return dateString
        val day = SimpleDateFormat("d", Locale.US).format(date).toInt()
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        val monthYear = SimpleDateFormat("MMMM, yyyy", Locale.US).format(date)
        "${day}${suffix} $monthYear"
    } catch (e: Exception) {
        dateString
    }
}
