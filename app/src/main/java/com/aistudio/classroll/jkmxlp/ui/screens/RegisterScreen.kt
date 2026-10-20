package com.aistudio.classroll.jkmxlp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RegisterScreen(
    viewModel: ClassRollViewModel
) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val monthTitleFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val monthPrefix = monthFormat.format(selectedCalendar.time)

    val monthRecords by viewModel.getAttendanceForMonth(monthPrefix)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val attendanceMap = remember(monthRecords) {
        monthRecords.associateBy { "${it.date}_${it.roll}" }
    }

    // Days in current month
    val daysInMonth = remember(selectedCalendar) {
        val cal = selectedCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..maxDay).map { day ->
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayCal.time)
            val dow = dayCal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dow == Calendar.SUNDAY || dow == Calendar.FRIDAY
            Triple(day, dateStr, isWeekend)
        }
    }

    var showStudentDialog by remember { mutableStateOf(false) }
    var editingStudentRoll by remember { mutableStateOf("") }
    var editingStudentName by remember { mutableStateOf("") }
    var isNewStudent by remember { mutableStateOf(true) }

    var showExportMenu by remember { mutableStateOf(false) }
    var holidayConfirmDate by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val nextRoll = (students.mapNotNull { it.roll.toIntOrNull() }.maxOrNull() ?: 0) + 1
                    editingStudentRoll = nextRoll.toString()
                    editingStudentName = ""
                    isNewStudent = true
                    showStudentDialog = true
                },
                modifier = Modifier.testTag("add_student_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            // Month navigation & Export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val newCal = selectedCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            selectedCalendar = newCal
                        },
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Month")
                    }

                    Text(
                        text = monthTitleFormat.format(selectedCalendar.time),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            val newCal = selectedCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, 1)
                            selectedCalendar = newCal
                        },
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                    }
                }

                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.testTag("register_menu_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export to CSV") },
                            onClick = {
                                showExportMenu = false
                                val sb = StringBuilder()
                                sb.append("Roll,Name,").append(daysInMonth.joinToString(",") { "${it.first}" }).append(",Present,Absent,Rate%\n")
                                students.forEach { s ->
                                    var p = 0
                                    var a = 0
                                    val rowDays = daysInMonth.joinToString(",") { d ->
                                        val rec = attendanceMap["${d.second}_${s.roll}"]
                                        when (rec?.status) {
                                            "PRESENT" -> { p++; "P" }
                                            "ABSENT" -> { a++; "A" }
                                            else -> if (holidays.contains(d.second) || d.third) "H" else "-"
                                        }
                                    }
                                    val tot = p + a
                                    val rate = if (tot > 0) "%.1f".format(p * 100f / tot) else "0"
                                    sb.append("${s.roll},\"${s.name}\",$rowDays,$p,$a,$rate%\n")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ClassRoll Register - $monthPrefix")
                                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share CSV"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Register as Text") },
                            onClick = {
                                showExportMenu = false
                                val sb = StringBuilder("Register $monthPrefix ($currentYear)\n")
                                students.forEach { s ->
                                    val pCount = daysInMonth.count { d -> attendanceMap["${d.second}_${s.roll}"]?.status == "PRESENT" }
                                    val aCount = daysInMonth.count { d -> attendanceMap["${d.second}_${s.roll}"]?.status == "ABSENT" }
                                    sb.append("Roll ${s.roll}: ${s.name} - Present: $pCount, Absent: $aCount\n")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Register Summary", sb.toString()))
                                Toast.makeText(context, "Copied summary to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Register table
            if (students.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No students registered for $currentYear.\nTap '+' to add student or import from Excel/CSV.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                val horizontalScroll = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                ) {
                    // Header row with dates
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Roll",
                            modifier = Modifier
                                .width(46.dp)
                                .padding(horizontal = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Name",
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 4.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        daysInMonth.forEach { (day, dateStr, isWknd) ->
                            val isHol = holidays.contains(dateStr)
                            val headerBg = when {
                                isHol -> Color(0xFFFFCC80)
                                isWknd -> Color(0xFFEEEEEE)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(34.dp)
                                    .background(headerBg)
                                    .clickable { holidayConfirmDate = dateStr },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    fontSize = 11.sp,
                                    fontWeight = if (isWknd || isHol) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isHol) Color(0xFFE65100) else Color.Unspecified
                                )
                            }
                        }

                        Text(
                            "P",
                            modifier = Modifier.width(36.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            "A",
                            modifier = Modifier.width(36.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            "%",
                            modifier = Modifier.width(44.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider()

                    // Student rows
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(students, key = { it.roll }) { student ->
                            var presentCount = 0
                            var absentCount = 0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Roll
                                Text(
                                    student.roll,
                                    modifier = Modifier
                                        .width(46.dp)
                                        .padding(horizontal = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )

                                // Name with edit click
                                Row(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            editingStudentRoll = student.roll
                                            editingStudentName = student.name
                                            isNewStudent = false
                                            showStudentDialog = true
                                        }
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        student.name,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Attendance day cells
                                daysInMonth.forEach { (day, dateStr, isWknd) ->
                                    val isHol = holidays.contains(dateStr)
                                    val record = attendanceMap["${dateStr}_${student.roll}"]
                                    val status = record?.status ?: ""
                                    if (status == "PRESENT") presentCount++
                                    if (status == "ABSENT") absentCount++

                                    val cellBg = when {
                                        status == "PRESENT" -> Color(0xFFC8E6C9)
                                        status == "ABSENT" -> Color(0xFFFFCDD2)
                                        isHol -> Color(0xFFFFF3E0)
                                        isWknd -> Color(0xFFF5F5F5)
                                        else -> Color.Transparent
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(34.dp)
                                            .padding(1.dp)
                                            .background(cellBg, RoundedCornerShape(2.dp))
                                            .clickable {
                                                if (!isWknd && !isHol) {
                                                    val nextStatus = when (status) {
                                                        "PRESENT" -> "ABSENT"
                                                        "ABSENT" -> ""
                                                        else -> "PRESENT"
                                                    }
                                                    if (nextStatus.isEmpty()) {
                                                        viewModel.deleteAttendanceRecord(date = dateStr, roll = student.roll)
                                                    } else {
                                                        viewModel.updateCell(roll = student.roll, status = nextStatus, date = dateStr)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when {
                                                status == "PRESENT" -> "P"
                                                status == "ABSENT" -> "A"
                                                isHol -> "H"
                                                isWknd -> "-"
                                                else -> ""
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (status) {
                                                "PRESENT" -> Color(0xFF1B5E20)
                                                "ABSENT" -> Color(0xFFB71C1C)
                                                else -> Color.Gray
                                            }
                                        )
                                    }
                                }

                                // Total P
                                Text(
                                    "$presentCount",
                                    modifier = Modifier.width(36.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF2E7D32)
                                )

                                // Total A
                                Text(
                                    "$absentCount",
                                    modifier = Modifier.width(36.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFFC62828)
                                )

                                // %
                                val tot = presentCount + absentCount
                                val pct = if (tot > 0) (presentCount * 100 / tot) else 0
                                Text(
                                    "$pct%",
                                    modifier = Modifier.width(44.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Student Dialog
    if (showStudentDialog) {
        AlertDialog(
            onDismissRequest = { showStudentDialog = false },
            title = { Text(if (isNewStudent) "Add Student" else "Edit Student") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingStudentRoll,
                        onValueChange = { editingStudentRoll = it },
                        label = { Text("Roll Number") },
                        enabled = isNewStudent,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_roll_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingStudentName,
                        onValueChange = { editingStudentName = it },
                        label = { Text("Student Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingStudentRoll.isNotBlank() && editingStudentName.isNotBlank()) {
                            viewModel.addOrUpdateStudent(editingStudentRoll.trim(), editingStudentName.trim())
                            showStudentDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_student_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (!isNewStudent) {
                        TextButton(
                            onClick = {
                                viewModel.deleteStudent(editingStudentRoll)
                                showStudentDialog = false
                            },
                            modifier = Modifier.testTag("delete_student_button")
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showStudentDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Holiday toggle confirm
    if (holidayConfirmDate != null) {
        val date = holidayConfirmDate ?: ""
        val isAlreadyHoliday = holidays.contains(date)
        AlertDialog(
            onDismissRequest = { holidayConfirmDate = null },
            title = { Text(if (isAlreadyHoliday) "Remove Holiday?" else "Mark as Holiday?") },
            text = {
                Text("Do you want to ${if (isAlreadyHoliday) "remove holiday status from" else "mark"} $date ${if (isAlreadyHoliday) "" else "as a non-working day"}?")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.toggleHoliday(date)
                    holidayConfirmDate = null
                }) {
                    Text(if (isAlreadyHoliday) "Remove Holiday" else "Mark Holiday")
                }
            },
            dismissButton = {
                TextButton(onClick = { holidayConfirmDate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
