package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RegisterScreen(viewModel: ClassRollViewModel) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val monthTitleFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val selectedMonthStr = remember(selectedCalendar) { monthFormat.format(selectedCalendar.time) }
    val selectedMonthTitle = remember(selectedCalendar) { monthTitleFormat.format(selectedCalendar.time) }
    
    val attendanceRecordsFlow = remember(selectedMonthStr, currentYear) { viewModel.getAttendanceForMonth(selectedMonthStr) }
    val attendanceRecords by attendanceRecordsFlow.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var showStudentDialog by remember { mutableStateOf(false) }
    var editingStudentRoll by remember { mutableStateOf("") }
    var editingStudentName by remember { mutableStateOf("") }
    
    var showCsvExportDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var copyMessage by remember { mutableStateOf("") }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.roll.contains(searchQuery, ignoreCase = true) 
        }
    }
    
    if (showStudentDialog) {
        AlertDialog(
            onDismissRequest = { showStudentDialog = false },
            title = { Text(if (editingStudentRoll.isNotBlank() && students.any { it.roll == editingStudentRoll }) "Edit Student" else "Add Student") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingStudentRoll,
                        onValueChange = { editingStudentRoll = it },
                        label = { Text("Roll Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingStudentName,
                        onValueChange = { editingStudentName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingStudentRoll.isNotBlank()) {
                        viewModel.addOrUpdateStudent(editingStudentRoll, editingStudentName)
                    }
                    showStudentDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (editingStudentRoll.isNotBlank() && students.any { it.roll == editingStudentRoll }) {
                        TextButton(onClick = {
                            viewModel.deleteStudent(editingStudentRoll)
                            showStudentDialog = false
                        }) {
                            Text("Delete", color = Color.Red)
                        }
                    }
                    TextButton(onClick = { showStudentDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Get all dates in selected month
    val daysInMonth = remember(selectedCalendar) {
        val cal = selectedCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val dates = remember(selectedCalendar, selectedMonthStr, daysInMonth) {
        (1..daysInMonth).map { day ->
            String.format(Locale.US, "%s-%02d", selectedMonthStr, day)
        }
    }

    if (showCsvExportDialog) {
        val csvContent = remember(filteredStudents, dates, attendanceRecords) {
            val header = "Roll,Name," + dates.joinToString(",") { it.substringAfterLast("-") }
            val rows = filteredStudents.map { student ->
                val attValues = dates.map { date ->
                    attendanceRecords.find { it.roll == student.roll && it.date == date }?.status ?: ""
                }.joinToString(",")
                "${student.roll},\"${student.name}\",$attValues"
            }
            (listOf(header) + rows).joinToString("\n")
        }

        AlertDialog(
            onDismissRequest = { 
                showCsvExportDialog = false
                copyMessage = ""
            },
            title = { Text("Export Register (CSV)") },
            text = {
                Column {
                    OutlinedTextField(
                        value = csvContent,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                    if (copyMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(copyMessage, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(csvContent))
                    copyMessage = "Copied CSV to Clipboard!"
                }) {
                    Text("Copy CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCsvExportDialog = false
                    copyMessage = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingStudentRoll = ""
                editingStudentName = ""
                showStudentDialog = true 
            }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // Top Bar with Navigation & Export
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    val cal = selectedCalendar.clone() as Calendar
                    cal.add(Calendar.MONTH, -1)
                    selectedCalendar = cal
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
                }

                Text("Register: $selectedMonthTitle", style = MaterialTheme.typography.titleLarge)

                Row {
                    IconButton(onClick = { showCsvExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = {
                        val cal = selectedCalendar.clone() as Calendar
                        cal.add(Calendar.MONTH, 1)
                        selectedCalendar = cal
                    }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name or roll...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true
            )
            
            if (filteredStudents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching students found.")
                }
            } else {
                Row(Modifier.weight(1f)) {
                    // Sticky Left Column (Roll / Name)
                    Column(Modifier.width(130.dp)) {
                        // Sticky Header Cell
                        Box(
                            Modifier
                                .height(48.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Roll / Name", style = MaterialTheme.typography.labelSmall)
                        }

                        // Scrollable Student Names
                        Column(Modifier.verticalScroll(verticalScrollState)) {
                            filteredStudents.forEach { student ->
                                Box(
                                    Modifier
                                        .height(48.dp)
                                        .fillMaxWidth()
                                        .border(1.dp, Color.Gray)
                                        .padding(4.dp)
                                        .clickable {
                                            editingStudentRoll = student.roll
                                            editingStudentName = student.name
                                            showStudentDialog = true
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text("${student.roll} ${student.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // Horizontally Scrollable Grid (Dates & Attendance Cells)
                    Column(Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
                        // Header Row (Dates)
                        Row(Modifier.height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            dates.forEach { date ->
                                val dayStr = date.substringAfterLast("-")
                                Box(
                                    Modifier
                                        .width(48.dp)
                                        .fillMaxHeight()
                                        .border(1.dp, Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(dayStr, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Attendance Cells Column (synced vertically with left column)
                        Column(Modifier.verticalScroll(verticalScrollState)) {
                            filteredStudents.forEach { student ->
                                Row(Modifier.height(48.dp)) {
                                    dates.forEach { date ->
                                        val record = attendanceRecords.find { it.roll == student.roll && it.date == date }
                                        val status = record?.status ?: ""
                                        val cellColor = when (status) {
                                            "P" -> Color(0xFFC8E6C9)
                                            "A" -> Color(0xFFFFCDD2)
                                            else -> Color.Transparent
                                        }

                                        Box(
                                            Modifier
                                                .width(48.dp)
                                                .fillMaxHeight()
                                                .background(cellColor)
                                                .border(1.dp, Color.Gray)
                                                .clickable {
                                                    val nextStatus = when (status) {
                                                        "" -> "P"
                                                        "P" -> "A"
                                                        else -> ""
                                                    }
                                                    viewModel.updateCell(date, student.roll, nextStatus)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(status, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
