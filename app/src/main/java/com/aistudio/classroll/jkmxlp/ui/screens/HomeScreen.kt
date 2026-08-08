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
import kotlinx.coroutines.launch
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
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateRecordsFlow = remember(date, currentYear) { viewModel.getAttendanceForDate(date) }
    val dateRecords by dateRecordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val recordedRolls = remember(dateRecords) { dateRecords.map { it.roll }.toSet() }

    LaunchedEffect(dateRecords, activeStudents, date, currentYear) {
        if (activeStudents.isNotEmpty()) {
            val firstUnrecordedIndex = activeStudents.indexOfFirst { it.roll !in recordedRolls }
            if (firstUnrecordedIndex != -1) {
                currentIndex = firstUnrecordedIndex
            } else if (recordedRolls.isNotEmpty()) {
                currentIndex = activeStudents.size
            } else {
                currentIndex = 0
            }
        }
    }

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

    val displayDate = remember(date) { formatDisplayDate(date) }

    if (currentIndex >= activeStudents.size) {
        val presentCount = dateRecords.count { it.status == "P" }
        val absentCount = dateRecords.count { it.status == "A" }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Summary", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Attendance for $displayDate saved automatically!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Present: $presentCount", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(8.dp))
                    Text("Absent: $absentCount", style = MaterialTheme.typography.titleLarge, color = Color(0xFFC62828))
                }
            }
            Spacer(Modifier.height(32.dp))
            Row {
                Button(onClick = { viewModel.clearAttendanceForDate(date) }) {
                    Text("Clear & Retake Today")
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = {
                    val lastStudent = activeStudents.lastOrNull()
                    if (lastStudent != null) {
                        viewModel.deleteAttendanceRecord(date, lastStudent.roll)
                    }
                }) {
                    Text("Undo Last")
                }
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
                    Text("Attendance for $displayDate", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
                Text("${currentIndex + 1} / ${activeStudents.size}", style = MaterialTheme.typography.bodyLarge)
            }
            if (currentIndex > 0) {
                TextButton(onClick = {
                    if (currentIndex > 0) {
                        currentIndex--
                        val prevStudent = activeStudents[currentIndex]
                        viewModel.deleteAttendanceRecord(date, prevStudent.roll)
                    }
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
                val allPresent = activeStudents.map { AttendanceRecordEntity(currentYear, date, it.roll, "P", true) }
                viewModel.submitAttendance(date, allPresent) { }
                currentIndex = activeStudents.size
            }) {
                Text("Mark All Present")
            }
            
            OutlinedButton(onClick = {
                val allAbsent = activeStudents.map { AttendanceRecordEntity(currentYear, date, it.roll, "A", true) }
                viewModel.submitAttendance(date, allAbsent) { }
                currentIndex = activeStudents.size
            }) {
                Text("Mark All Absent")
            }
        }

        Spacer(Modifier.height(16.dp))
        
        key(currentStudent.roll) {
            val allAttendanceForYear by viewModel.allAttendanceForYear.collectAsStateWithLifecycle(initialValue = emptyList())
            val studentRecords = remember(allAttendanceForYear, currentStudent.roll) {
                allAttendanceForYear.filter { it.roll == currentStudent.roll }
            }
            SwipeableStudentCard(
                student = currentStudent,
                attendanceRecords = studentRecords,
                onSwiped = { status ->
                    viewModel.updateCell(date, currentStudent.roll, status)
                    currentIndex++
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableStudentCard(
    student: StudentEntity,
    attendanceRecords: List<AttendanceRecordEntity>,
    onSwiped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            dismissValue != SwipeToDismissBoxValue.Settled
        }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onSwiped("P")
            SwipeToDismissBoxValue.EndToStart -> onSwiped("A")
            else -> {}
        }
    }

    // --- 1. Attendance Rate Calculation ---
    val totalSessions = attendanceRecords.size
    val presentCount = attendanceRecords.count { it.status == "P" }
    val attendanceRate = if (totalSessions > 0) (presentCount.toFloat() / totalSessions.toFloat()) else 1.0f
    val ratePercentage = (attendanceRate * 100).toInt()

    val rateColor = when {
        totalSessions == 0 -> MaterialTheme.colorScheme.primary
        ratePercentage >= 75 -> Color(0xFF2E7D32)
        ratePercentage >= 60 -> Color(0xFFF57C00)
        else -> Color(0xFFC62828)
    }

    // --- 2. Streak Calculation ---
    val sortedRecords = remember(attendanceRecords) {
        attendanceRecords.sortedByDescending { it.date }
    }
    val currentStreakStatus = sortedRecords.firstOrNull()?.status
    val streakCount = if (currentStreakStatus != null) {
        sortedRecords.takeWhile { it.status == currentStreakStatus }.size
    } else 0

    val streakBadgeText = when {
        totalSessions == 0 -> "✨ New Student"
        currentStreakStatus == "P" -> "🔥 $streakCount Session Present Streak"
        currentStreakStatus == "A" -> "⚠️ $streakCount Session Absence Streak"
        else -> "No history"
    }

    // --- 3. Last Recorded Status ---
    val lastRecord = sortedRecords.firstOrNull()
    val lastSessionText = if (lastRecord != null) {
        val formattedLastDate = formatDisplayDate(lastRecord.date)
        val statusName = if (lastRecord.status == "P") "Present" else "Absent"
        "Last Status: $statusName ($formattedLastDate)"
    } else {
        "First attendance entry for this student"
    }

    Column(modifier = modifier) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.weight(1f),
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
                Box(
                    Modifier.fillMaxSize().background(color, MaterialTheme.shapes.large).padding(24.dp),
                    contentAlignment = alignment
                ) {
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
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Section: Roll Number Pill Badge & Streak Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "Roll #${student.roll}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Streak Badge (Insight 2)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (currentStreakStatus == "A") Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = streakBadgeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (currentStreakStatus == "A") Color(0xFFC62828) else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Center Section: Student Name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Bottom Section: Student Stats Box (Insight 1 & Insight 3)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Insight 1: Overall Attendance Rate Progress Bar & Ratio
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Attendance Rate",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (totalSessions > 0) "$ratePercentage% ($presentCount/$totalSessions Days)" else "N/A",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = rateColor
                                )
                            }

                            LinearProgressIndicator(
                                progress = { if (totalSessions > 0) attendanceRate else 1f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = rateColor,
                                trackColor = rateColor.copy(alpha = 0.2f)
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Insight 3: Last Recorded Status
                            Text(
                                text = lastSessionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tap action buttons for Present/Absent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        dismissState.dismiss(SwipeToDismissBoxValue.StartToEnd)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f).height(50.dp).padding(end = 8.dp)
            ) {
                Text("Present (Swipe Right)", color = Color.White)
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        dismissState.dismiss(SwipeToDismissBoxValue.EndToStart)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.weight(1f).height(50.dp).padding(start = 8.dp)
            ) {
                Text("Absent (Swipe Left)", color = Color.White)
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
