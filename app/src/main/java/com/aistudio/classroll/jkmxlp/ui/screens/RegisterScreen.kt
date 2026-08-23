package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RegisterScreen(viewModel: ClassRollViewModel) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val monthTitleFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }

    val selectedMonthStr = remember(selectedCalendar) { monthFormat.format(selectedCalendar.time) }
    val selectedMonthTitle = remember(selectedCalendar) { monthTitleFormat.format(selectedCalendar.time) }
    
    val attendanceRecordsFlow = remember(selectedMonthStr, currentYear) { viewModel.getAttendanceForMonth(selectedMonthStr) }
    val attendanceRecords by attendanceRecordsFlow.collectAsStateWithLifecycle()

    val attendanceMap = remember(attendanceRecords) {
        attendanceRecords.groupBy { it.roll }.mapValues { (_, records) -> records.associateBy { it.date } }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var showStudentDialog by remember { mutableStateOf(false) }
    var editingStudentRoll by remember { mutableStateOf("") }
    var editingStudentName by remember { mutableStateOf("") }
    
    var showCsvExportDialog by remember { mutableStateOf(false) }
    var summaryDate by remember { mutableStateOf<String?>(null) }
    
    // Holiday toggle confirmation & 5-second undo state
    var holidayConfirmDate by remember { mutableStateOf<String?>(null) }
    var undoDate by remember { mutableStateOf<String?>(null) }
    var undoMessage by remember { mutableStateOf("") }
    var undoRemainingSeconds by remember { mutableIntStateOf(5) }

    val coroutineScope = rememberCoroutineScope()

    // 5-second timer countdown for undo
    LaunchedEffect(undoDate) {
        if (undoDate != null) {
            undoRemainingSeconds = 5
            while (undoRemainingSeconds > 0) {
                delay(1000L)
                undoRemainingSeconds--
            }
            undoDate = null
            undoMessage = ""
        }
    }

    fun triggerHolidayToggleWithUndo(date: String, wasHoliday: Boolean) {
        viewModel.toggleHoliday(date)
        undoDate = date
        undoMessage = if (wasHoliday) "Removed holiday for $date" else "Marked $date as Holiday"
    }

    val clipboardManager = LocalClipboardManager.current
    var copyMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    var pendingCsvContent by remember { mutableStateOf("") }
    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && pendingCsvContent.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingCsvContent.toByteArray())
                }
                copyMessage = "Saved to file."
            } catch (e: Exception) {
                copyMessage = "Failed to save: ${e.message}"
            }
        }
    }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.roll.contains(searchQuery, ignoreCase = true) 
        }
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

    // Add / Edit Student Dialog
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

    // Holiday Toggle Confirmation Dialog
    if (holidayConfirmDate != null) {
        val confirmDate = holidayConfirmDate!!
        val activeStudents = remember(students) { students.filter { it.name.isNotBlank() } }
        val presentCount = activeStudents.count { attendanceMap[it.roll]?.get(confirmDate)?.status == "P" }
        val absentCount = activeStudents.count { attendanceMap[it.roll]?.get(confirmDate)?.status == "A" }
        val isAlreadyHoliday = holidays.contains(confirmDate)

        AlertDialog(
            onDismissRequest = { holidayConfirmDate = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Confirmation Warning",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = {
                Text(
                    text = if (isAlreadyHoliday) "Clear Holiday Status?" else "Mark as Holiday (Off Day)?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Date: ${formatFullDisplayDate(confirmDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!isAlreadyHoliday && (presentCount > 0 || absentCount > 0)) {
                        Text(
                            text = "⚠️ Attendance has already been taken on this day ($presentCount Present, $absentCount Absent).\n\nMarking it as a Holiday will display 'OFF' in the monthly register column.\n\n(Don't worry: your attendance records are safely kept and you will have 5 seconds to Undo).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (!isAlreadyHoliday) {
                        Text(
                            text = "This will mark this day as a School Holiday (OFF) in the monthly register. You will have 5 seconds to undo.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "This will re-open this day as a regular school day in the register. Any previously recorded attendance will be shown.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wasHoliday = isAlreadyHoliday
                        holidayConfirmDate = null
                        summaryDate = null
                        triggerHolidayToggleWithUndo(confirmDate, wasHoliday)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlreadyHoliday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(if (isAlreadyHoliday) "Clear Holiday" else "Yes, Mark Holiday")
                }
            },
            dismissButton = {
                TextButton(onClick = { holidayConfirmDate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Date Press-and-Hold Summary Dialog
    if (summaryDate != null && holidayConfirmDate == null) {
        val selDate = summaryDate!!
        val isCustomHoliday = holidays.contains(selDate)
        val isWeekendDay = isWeekend(selDate)
        val activeStudents = remember(students) { students.filter { it.name.isNotBlank() } }
        val totalStudents = activeStudents.size
        
        val presentList = remember(activeStudents, attendanceMap, selDate) {
            activeStudents.filter { attendanceMap[it.roll]?.get(selDate)?.status == "P" }
        }
        val absentList = remember(activeStudents, attendanceMap, selDate) {
            activeStudents.filter { attendanceMap[it.roll]?.get(selDate)?.status == "A" }
        }
        val unmarkedCount = (totalStudents - presentList.size - absentList.size).coerceAtLeast(0)

        AlertDialog(
            onDismissRequest = { summaryDate = null },
            title = {
                Column {
                    Text(
                        text = formatFullDisplayDate(selDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isCustomHoliday -> MaterialTheme.colorScheme.tertiaryContainer
                            isWeekendDay -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when {
                                isCustomHoliday -> "🌴 School Holiday (Off Day)"
                                isWeekendDay -> "📅 Weekend (Friday / Saturday - Fixed Off)"
                                else -> "📖 Regular School Day"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = when {
                                isCustomHoliday -> MaterialTheme.colorScheme.onTertiaryContainer
                                isWeekendDay -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Summary Metrics Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Present", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                    Text("${presentList.size}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Absent", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                                    Text("${absentList.size}", style = MaterialTheme.typography.titleMedium, color = Color(0xFFC62828))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Unmarked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("$unmarkedCount", style = MaterialTheme.typography.titleMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Total", style = MaterialTheme.typography.labelSmall)
                                    Text("$totalStudents", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            if (totalStudents > 0 && (presentList.isNotEmpty() || absentList.isNotEmpty())) {
                                Spacer(Modifier.height(8.dp))
                                val presentRatio = presentList.size.toFloat() / totalStudents.toFloat()
                                val presentPct = (presentRatio * 100).toInt()
                                LinearProgressIndicator(
                                    progress = { presentRatio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Color(0xFF2E7D32),
                                    trackColor = Color(0xFFFFCDD2)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "$presentPct% Present Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.End),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Absent Students List
                    if (absentList.isNotEmpty()) {
                        Text(
                            text = "Absent Students (${absentList.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFC62828)
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                            items(absentList, key = { it.roll }) { student ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Roll #${student.roll}  ${student.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFC62828)
                                        )
                                        Text(
                                            text = "Absent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (presentList.size == totalStudents && totalStudents > 0) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎉 100% Attendance! All students were present.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (presentList.isEmpty() && absentList.isEmpty()) {
                        Text(
                            text = if (isCustomHoliday || isWeekendDay) "School was off on this day." else "No attendance recorded for this date yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isWeekendDay) {
                        OutlinedButton(onClick = {
                            holidayConfirmDate = selDate
                        }) {
                            Text(if (isCustomHoliday) "Clear Holiday" else "🌴 Toggle Holiday")
                        }
                    }
                    TextButton(onClick = { summaryDate = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    if (showCsvExportDialog) {
        val csvContent = remember(filteredStudents, dates, attendanceMap) {
            val header = "Roll,Name," + dates.joinToString(",") { it.substringAfterLast("-") }
            val rows = filteredStudents.map { student ->
                val attValues = dates.joinToString(",") { date ->
                    attendanceMap[student.roll]?.get(date)?.status ?: ""
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        pendingCsvContent = csvContent
                        saveCsvLauncher.launch("register_${selectedMonthStr}.csv")
                    }) {
                        Text("Save to File")
                    }
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(csvContent))
                        copyMessage = "Copied CSV to Clipboard!"
                    }) {
                        Text("Copy Instead")
                    }
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(Modifier.fillMaxSize()) {
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

                // Hint row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Fri & Sat are fixed weekends. Press & hold date header for summary & holiday.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (filteredStudents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching students found.")
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        // Header Row (Sticky Roll/Name Header + Scrollable Dates)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Box(
                                Modifier
                                    .width(130.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Roll / Name", style = MaterialTheme.typography.labelSmall)
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .horizontalScroll(horizontalScrollState)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                dates.forEach { date ->
                                    val dayStr = date.substringAfterLast("-")
                                    val dayOfWeek = getDayOfWeekShort(date)
                                    val isWeekendDay = isWeekend(date)
                                    val isCustomHoliday = holidays.contains(date)

                                    val headerBg = when {
                                        isCustomHoliday -> MaterialTheme.colorScheme.tertiaryContainer
                                        isWeekendDay -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }

                                    Box(
                                        Modifier
                                            .width(48.dp)
                                            .fillMaxHeight()
                                            .background(headerBg)
                                            .border(1.dp, Color.Gray)
                                            .pointerInput(date) {
                                                detectTapGestures(
                                                    onTap = { summaryDate = date },
                                                    onLongPress = { summaryDate = date }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                dayStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isWeekendDay || isCustomHoliday) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = if (isCustomHoliday) "Off" else dayOfWeek,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    isCustomHoliday -> MaterialTheme.colorScheme.onTertiaryContainer
                                                    isWeekendDay -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    else -> Color.Gray
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // LazyColumn for Student Rows
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(filteredStudents, key = { it.roll }) { student ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    // Sticky Left Student Roll/Name Cell
                                    Box(
                                        Modifier
                                            .width(130.dp)
                                            .fillMaxHeight()
                                            .border(1.dp, Color.Gray)
                                            .padding(4.dp)
                                            .clickable {
                                                editingStudentRoll = student.roll
                                                editingStudentName = student.name
                                                showStudentDialog = true
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            "${student.roll} ${student.name}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    // Attendance Cells Row
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .horizontalScroll(horizontalScrollState)
                                    ) {
                                        dates.forEach { date ->
                                            val record = attendanceMap[student.roll]?.get(date)
                                            val status = record?.status ?: ""
                                            val isWeekendDay = isWeekend(date)
                                            val isCustomHoliday = holidays.contains(date)

                                            // Styling for cell
                                            val cellColor = when {
                                                isWeekendDay -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                                isCustomHoliday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                                status == "P" -> Color(0xFFC8E6C9)
                                                status == "A" -> Color(0xFFFFCDD2)
                                                else -> Color.Transparent
                                            }

                                            val displayText = when {
                                                isWeekendDay -> "W"
                                                isCustomHoliday -> "OFF"
                                                status.isNotBlank() -> status
                                                else -> ""
                                            }

                                            val textColor = when {
                                                isWeekendDay -> MaterialTheme.colorScheme.outline
                                                isCustomHoliday -> MaterialTheme.colorScheme.tertiary
                                                status == "P" -> Color(0xFF1B5E20)
                                                status == "A" -> Color(0xFFB71C1C)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }

                                            // Weekend & Custom Holidays are locked/non-editable by tapping the cell
                                            val isCellEditable = !isWeekendDay && !isCustomHoliday

                                            Box(
                                                Modifier
                                                    .width(48.dp)
                                                    .fillMaxHeight()
                                                    .background(cellColor)
                                                    .border(1.dp, Color.Gray.copy(alpha = 0.6f))
                                                    .then(
                                                        if (isCellEditable) {
                                                            Modifier.clickable {
                                                                val nextStatus = when (status) {
                                                                    "" -> "P"
                                                                    "P" -> "A"
                                                                    else -> ""
                                                                }
                                                                viewModel.updateCell(date, student.roll, nextStatus)
                                                            }
                                                        } else {
                                                            Modifier
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = displayText,
                                                    textAlign = TextAlign.Center,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isWeekendDay || isCustomHoliday) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5-Second Animated Undo Floating Banner
            AnimatedVisibility(
                visible = undoDate != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = undoMessage,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Auto-closing in ${undoRemainingSeconds}s...",
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Button(
                            onClick = {
                                val targetDate = undoDate
                                if (targetDate != null) {
                                    viewModel.toggleHoliday(targetDate)
                                    undoDate = null
                                    undoMessage = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.inversePrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("UNDO (${undoRemainingSeconds}s)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun isWeekend(dateStr: String): Boolean {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return false
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY
    } catch (e: Exception) {
        false
    }
}

private fun getDayOfWeekShort(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return ""
        SimpleDateFormat("EEE", Locale.US).format(date)
    } catch (e: Exception) {
        ""
    }
}

private fun formatFullDisplayDate(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        dateStr
    }
}
