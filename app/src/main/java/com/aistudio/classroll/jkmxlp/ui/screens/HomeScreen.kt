package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ClassRollViewModel
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendanceForYear.collectAsStateWithLifecycle()

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormat = remember { SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US) }
    val currentDateStr = dateFormat.format(selectedCalendar.time)

    val dateRecords by viewModel.getAttendanceForDate(currentDateStr)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val attendanceMap = dateRecords.associateBy { it.roll }

    val dayOfWeek = selectedCalendar.get(Calendar.DAY_OF_WEEK)
    val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.FRIDAY
    val isHoliday = holidays.contains(currentDateStr)
    val isLocked = isWeekend || isHoliday

    val presentCount = dateRecords.count { it.status == "PRESENT" }
    val absentCount = dateRecords.count { it.status == "ABSENT" }
    val totalRecorded = presentCount + absentCount
    val attendanceRate = if (totalRecorded > 0) (presentCount * 100f / totalRecorded) else 0f

    val filteredStudents = students.filter { s ->
        searchQuery.isBlank() || s.name.contains(searchQuery, ignoreCase = true) || s.roll.contains(searchQuery)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Date navigation bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = selectedCalendar.clone() as Calendar
                            newCal.add(Calendar.DAY_OF_YEAR, -1)
                            selectedCalendar = newCal
                        },
                        modifier = Modifier.testTag("prev_day_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showDatePicker = true }
                            .padding(8.dp)
                            .testTag("date_picker_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = displayDateFormat.format(selectedCalendar.time),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLocked) {
                                Text(
                                    text = if (isHoliday) "Custom Holiday" else "Weekend",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val newCal = selectedCalendar.clone() as Calendar
                            newCal.add(Calendar.DAY_OF_YEAR, 1)
                            selectedCalendar = newCal
                        },
                        modifier = Modifier.testTag("next_day_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attendance overview stat pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Present", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                        Text(
                            "$presentCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Absent", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC62828))
                        Text(
                            "$absentCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Rate", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "%.0f%%".format(attendanceRate),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Mark all present / Mark all absent
            if (!isLocked && students.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val newRecords = students.map {
                                AttendanceRecordEntity(
                                    year = currentYear,
                                    date = currentDateStr,
                                    roll = it.roll,
                                    status = "PRESENT"
                                )
                            }
                            viewModel.submitAttendance(newRecords)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mark_all_present_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Present")
                    }

                    OutlinedButton(
                        onClick = {
                            val newRecords = students.map {
                                AttendanceRecordEntity(
                                    year = currentYear,
                                    date = currentDateStr,
                                    roll = it.roll,
                                    status = "ABSENT"
                                )
                            }
                            viewModel.submitAttendance(newRecords)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mark_all_absent_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Absent", color = Color(0xFFC62828))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLocked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHoliday) "Today is marked as Holiday. Attendance taking is disabled." else "Today is a Weekend. Attendance taking is disabled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input"),
                placeholder = { Text("Search by student name or roll...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Student list
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (students.isEmpty()) "No students found for year $currentYear.\nGo to 'Import Students' to add students." else "No students match '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStudents, key = { it.roll }) { student ->
                        val record = attendanceMap[student.roll]
                        val status = record?.status ?: "UNRECORDED"

                        // Calculate student streak
                        val studentRecords = allAttendance
                            .filter { it.roll == student.roll && it.date <= currentDateStr }
                            .sortedByDescending { it.date }

                        var streakCount = 0
                        var streakStatus = ""
                        for (r in studentRecords) {
                            if (streakStatus.isEmpty()) {
                                streakStatus = r.status
                                streakCount = 1
                            } else if (r.status == streakStatus) {
                                streakCount++
                            } else {
                                break
                            }
                        }

                        SwipeableStudentCard(
                            student = student,
                            status = status,
                            streakCount = streakCount,
                            streakStatus = streakStatus,
                            isLocked = isLocked,
                            onStatusSelected = { newStatus ->
                                if (!isLocked) {
                                    viewModel.updateCell(
                                        roll = student.roll,
                                        status = newStatus,
                                        date = currentDateStr
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedCalendar.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newCal = Calendar.getInstance()
                        newCal.timeInMillis = millis
                        selectedCalendar = newCal
                    }
                    showDatePicker = false
                }) {
                    Text("Select")
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
}

@Composable
fun SwipeableStudentCard(
    student: StudentEntity,
    status: String,
    streakCount: Int,
    streakStatus: String,
    isLocked: Boolean,
    onStatusSelected: (String) -> Unit
) {
    val cardColor = when (status) {
        "PRESENT" -> Color(0xFFF1F8E9)
        "ABSENT" -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.roll}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Roll avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.roll,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streakCount >= 2) {
                            Text(
                                text = "$streakCount $streakStatus Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (streakStatus == "PRESENT") Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Status: $status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Quick P / A action buttons
            if (!isLocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onStatusSelected("PRESENT") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status == "PRESENT") Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (status == "PRESENT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(width = 46.dp, height = 40.dp)
                            .testTag("present_btn_${student.roll}")
                    ) {
                        Text("P", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Button(
                        onClick = { onStatusSelected("ABSENT") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status == "ABSENT") Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (status == "ABSENT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(width = 46.dp, height = 40.dp)
                            .testTag("absent_btn_${student.roll}")
                    ) {
                        Text("A", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
