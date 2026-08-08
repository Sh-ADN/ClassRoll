package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.util.Locale

enum class StatSortOption {
    ROLL, NAME, LOWEST_ATTENDANCE, HIGHEST_ATTENDANCE, CONSECUTIVE_ABSENCES
}

@Composable
fun SummaryDashboardScreen(viewModel: ClassRollViewModel) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendanceForYear.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(StatSortOption.ROLL) }
    var showConsecutiveAlertsOnly by remember { mutableStateOf(false) }
    var selectedStudentRoll by remember { mutableStateOf<String?>(null) }

    if (students.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No students found.")
        }
        return
    }

    // Calculate present, absent counts, and consecutive absences for each student
    val studentStats = remember(students, allAttendance) {
        students.map { student ->
            val records = allAttendance.filter { it.roll == student.roll }.sortedBy { it.date }
            val presentCount = records.count { it.status == "P" }
            val absentCount = records.count { it.status == "A" }
            val total = presentCount + absentCount
            val percentage = if (total > 0) (presentCount.toFloat() / total) * 100f else 100f

            var maxConsecutive = 0
            var run = 0
            for (r in records) {
                if (r.status == "A") {
                    run++
                    if (run > maxConsecutive) maxConsecutive = run
                } else if (r.status == "P") {
                    run = 0
                }
            }

            StudentStat(
                roll = student.roll,
                name = student.name,
                presentCount = presentCount,
                absentCount = absentCount,
                percentage = percentage,
                totalClasses = total,
                maxConsecutiveAbsences = maxConsecutive,
                currentConsecutiveAbsences = run
            )
        }
    }

    // Class Overall Stats
    val totalStudents = studentStats.size
    val totalClassPresent = studentStats.sumOf { it.presentCount }
    val totalClassAbsent = studentStats.sumOf { it.absentCount }
    val totalClassRecords = totalClassPresent + totalClassAbsent
    val classAvgPct = if (totalClassRecords > 0) (totalClassPresent.toFloat() / totalClassRecords) * 100f else 100f
    val consecutiveAbsenceCount = studentStats.count { it.maxConsecutiveAbsences >= 2 }

    val filteredAndSortedStats = remember(studentStats, searchQuery, sortOption, showConsecutiveAlertsOnly) {
        var filtered = if (searchQuery.isBlank()) {
            studentStats
        } else {
            studentStats.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.roll.contains(searchQuery, ignoreCase = true)
            }
        }

        if (showConsecutiveAlertsOnly) {
            filtered = filtered.filter { it.maxConsecutiveAbsences >= 2 }
        }

        when (sortOption) {
            StatSortOption.ROLL -> filtered.sortedBy { it.roll.toIntOrNull() ?: Int.MAX_VALUE }
            StatSortOption.NAME -> filtered.sortedBy { it.name }
            StatSortOption.LOWEST_ATTENDANCE -> filtered.sortedBy { it.percentage }
            StatSortOption.HIGHEST_ATTENDANCE -> filtered.sortedByDescending { it.percentage }
            StatSortOption.CONSECUTIVE_ABSENCES -> filtered.sortedByDescending { it.maxConsecutiveAbsences }
        }
    }

    val maxTotal = studentStats.maxOfOrNull { it.totalClasses } ?: 1
    val chartMax = if (maxTotal == 0) 1 else maxTotal

    // Student Attendance History Timeline Modal Dialog
    if (selectedStudentRoll != null) {
        val selStudent = studentStats.find { it.roll == selectedStudentRoll }
        val studentTimelineRecords = remember(allAttendance, selectedStudentRoll) {
            allAttendance.filter { it.roll == selectedStudentRoll && it.status.isNotBlank() }
                .sortedByDescending { it.date }
        }

        AlertDialog(
            onDismissRequest = { selectedStudentRoll = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selStudent?.roll} - ${selStudent?.name}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { selectedStudentRoll = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Summary Header Box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Attendance", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", selStudent?.percentage ?: 100f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Present / Total", style = MaterialTheme.typography.labelSmall)
                                Text("${selStudent?.presentCount} / ${selStudent?.totalClasses}", style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Max Streak Abs.", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${selStudent?.maxConsecutiveAbsences}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if ((selStudent?.maxConsecutiveAbsences ?: 0) >= 2) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Attendance History Timeline:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))

                    if (studentTimelineRecords.isEmpty()) {
                        Text("No recorded attendance dates for this student.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(studentTimelineRecords) { record ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (record.status == "P") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(record.date, style = MaterialTheme.typography.titleSmall)
                                            Text("Academic Year $currentYear", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (record.status == "P") Color(0xFF4CAF50) else Color(0xFFF44336),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = if (record.status == "P") "Present (P)" else "Absent (A)",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            TextButton(onClick = {
                                                val nextStatus = if (record.status == "P") "A" else "P"
                                                viewModel.updateCell(record.date, record.roll, nextStatus)
                                            }) {
                                                Text("Toggle", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStudentRoll = null }) {
                    Text("Done")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Attendance Summary", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))

        // KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Enrolled", style = MaterialTheme.typography.labelMedium)
                    Text("$totalStudents", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Class Average", style = MaterialTheme.typography.labelMedium)
                    Text(
                        String.format(Locale.US, "%.1f%%", classAvgPct),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (classAvgPct < 75f) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f).clickable {
                    showConsecutiveAlertsOnly = !showConsecutiveAlertsOnly
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (consecutiveAbsenceCount > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("2+ Consecutive Abs.", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$consecutiveAbsenceCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (consecutiveAbsenceCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Consecutive Absence Banner if flagged
        if (consecutiveAbsenceCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    showConsecutiveAlertsOnly = !showConsecutiveAlertsOnly
                },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color(0xFFB71C1C)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Consecutive Absence Alert!",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFB71C1C)
                        )
                        Text(
                            "$consecutiveAbsenceCount student(s) have been absent for 2 or more consecutive days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB71C1C)
                        )
                    }
                    TextButton(onClick = { showConsecutiveAlertsOnly = !showConsecutiveAlertsOnly }) {
                        Text(if (showConsecutiveAlertsOnly) "Show All" else "View Alerts", color = Color(0xFFB71C1C))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter summary by name or roll...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // Sort Options Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Sort:", style = MaterialTheme.typography.bodySmall)
            FilterChip(
                selected = sortOption == StatSortOption.ROLL && !showConsecutiveAlertsOnly,
                onClick = { 
                    sortOption = StatSortOption.ROLL
                    showConsecutiveAlertsOnly = false
                },
                label = { Text("Roll") }
            )
            FilterChip(
                selected = sortOption == StatSortOption.LOWEST_ATTENDANCE && !showConsecutiveAlertsOnly,
                onClick = { 
                    sortOption = StatSortOption.LOWEST_ATTENDANCE 
                    showConsecutiveAlertsOnly = false
                },
                label = { Text("Lowest %") }
            )
            FilterChip(
                selected = sortOption == StatSortOption.CONSECUTIVE_ABSENCES || showConsecutiveAlertsOnly,
                onClick = { 
                    sortOption = StatSortOption.CONSECUTIVE_ABSENCES
                    showConsecutiveAlertsOnly = true
                },
                label = { Text("⚠️ 2+ Absences") }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(14.dp).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(6.dp))
            Text("Present", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(20.dp))
            Box(Modifier.size(14.dp).background(Color(0xFFF44336)))
            Spacer(Modifier.width(6.dp))
            Text("Absent", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(20.dp))
            Text("(Tap card for Timeline)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(filteredAndSortedStats) { stat ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        selectedStudentRoll = stat.roll
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (stat.maxConsecutiveAbsences >= 2) Color(0xFFFFEBEE) 
                                         else if (stat.totalClasses > 0 && stat.percentage < 75f) Color(0xFFFFF8E1) 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${stat.roll} - ${stat.name}", style = MaterialTheme.typography.titleMedium)
                                if (stat.maxConsecutiveAbsences >= 2) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFD32F2F),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ ${stat.maxConsecutiveAbsences} Abs. Streak",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stat.totalClasses > 0 && stat.percentage < 75f && stat.maxConsecutiveAbsences < 2) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Low Attendance",
                                        tint = Color(0xFFF57F17),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (stat.totalClasses > 0) String.format(Locale.US, "%.1f%%", stat.percentage) else "N/A",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (stat.maxConsecutiveAbsences >= 2 || (stat.totalClasses > 0 && stat.percentage < 75f)) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(20.dp)) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val presentRatio = stat.presentCount.toFloat() / chartMax.toFloat()
                                    val absentRatio = stat.absentCount.toFloat() / chartMax.toFloat()
                                    
                                    val presentWidth = size.width * presentRatio
                                    val absentWidth = size.width * absentRatio
                                    
                                    drawRoundRect(
                                        color = Color.LightGray,
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(4.dp.toPx())
                                    )
                                    
                                    if (presentWidth > 0) {
                                        drawRoundRect(
                                            color = Color(0xFF4CAF50),
                                            size = Size(presentWidth, size.height),
                                            cornerRadius = CornerRadius(4.dp.toPx())
                                        )
                                    }
                                    
                                    if (absentWidth > 0) {
                                        drawRoundRect(
                                            color = Color(0xFFF44336),
                                            topLeft = Offset(presentWidth, 0f),
                                            size = Size(absentWidth, size.height),
                                            cornerRadius = CornerRadius(4.dp.toPx())
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("P:${stat.presentCount} A:${stat.absentCount}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

data class StudentStat(
    val roll: String,
    val name: String,
    val presentCount: Int,
    val absentCount: Int,
    val percentage: Float,
    val totalClasses: Int,
    val maxConsecutiveAbsences: Int,
    val currentConsecutiveAbsences: Int
)
