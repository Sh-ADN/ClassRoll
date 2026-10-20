package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.data.StudentEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel

enum class StatSortOption(val displayName: String) {
    ROLL("Roll (Default)"),
    PERCENT_DESC("Attendance: High to Low"),
    PERCENT_ASC("Attendance: Low to High"),
    CONSECUTIVE_ABSENCE("Consecutive Absences (Risk)")
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

@Composable
fun SummaryDashboardScreen(
    viewModel: ClassRollViewModel
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendanceForYear.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(StatSortOption.ROLL) }
    var showConsecutiveAlertsOnly by remember { mutableStateOf(false) }
    var selectedStudentRoll by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Compute stats per student
    val attendanceByRoll = remember(allAttendance) {
        allAttendance.groupBy { it.roll }
    }

    val studentStats = remember(students, attendanceByRoll) {
        students.map { student ->
            val records = (attendanceByRoll[student.roll] ?: emptyList()).sortedBy { it.date }
            val present = records.count { it.status == "PRESENT" }
            val absent = records.count { it.status == "ABSENT" }
            val total = present + absent
            val pct = if (total > 0) (present * 100f / total) else 0f

            var maxConsecutive = 0
            var currentConsecutive = 0
            var run = 0
            for (r in records) {
                if (r.status == "ABSENT") {
                    run++
                    if (run > maxConsecutive) maxConsecutive = run
                } else {
                    run = 0
                }
            }
            // Current consecutive from end
            for (r in records.reversed()) {
                if (r.status == "ABSENT") {
                    currentConsecutive++
                } else {
                    break
                }
            }

            StudentStat(
                roll = student.roll,
                name = student.name,
                presentCount = present,
                absentCount = absent,
                percentage = pct,
                totalClasses = total,
                maxConsecutiveAbsences = maxConsecutive,
                currentConsecutiveAbsences = currentConsecutive
            )
        }
    }

    val riskStudentsCount = studentStats.count { it.currentConsecutiveAbsences >= 2 }
    val overallTotalPresent = studentStats.sumOf { it.presentCount }
    val overallTotalClasses = studentStats.sumOf { it.totalClasses }
    val overallRate = if (overallTotalClasses > 0) (overallTotalPresent * 100f / overallTotalClasses) else 0f

    val filteredStats = studentStats
        .filter { s ->
            (searchQuery.isBlank() || s.name.contains(searchQuery, ignoreCase = true) || s.roll.contains(searchQuery)) &&
                    (!showConsecutiveAlertsOnly || s.currentConsecutiveAbsences >= 2)
        }
        .let { list ->
            when (sortOption) {
                StatSortOption.ROLL -> list.sortedBy { it.roll.toIntOrNull() ?: 9999 }
                StatSortOption.PERCENT_DESC -> list.sortedByDescending { it.percentage }
                StatSortOption.PERCENT_ASC -> list.sortedBy { it.percentage }
                StatSortOption.CONSECUTIVE_ABSENCE -> list.sortedByDescending { it.currentConsecutiveAbsences }
            }
        }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Attendance Summary ($currentYear)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Overview metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Overall Rate", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "%.1f%%".format(overallRate),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Students", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${students.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (riskStudentsCount > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Risk Alerts", style = MaterialTheme.typography.labelSmall, color = if (riskStudentsCount > 0) Color(0xFFC62828) else Color.Unspecified)
                        Text(
                            "$riskStudentsCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (riskStudentsCount > 0) Color(0xFFB71C1C) else Color.Unspecified
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summary_search_input"),
                placeholder = { Text("Filter by student name or roll...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filters & Sort row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showConsecutiveAlertsOnly,
                    onClick = { showConsecutiveAlertsOnly = !showConsecutiveAlertsOnly },
                    label = { Text("2+ Consecutive Abs.") },
                    leadingIcon = if (showConsecutiveAlertsOnly) {
                        { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.testTag("filter_consecutive_chip")
                )

                Box {
                    TextButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_menu_button")
                    ) {
                        Text("Sort: ${sortOption.displayName}", maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        StatSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Students stats list
            if (filteredStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No students found.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStats, key = { it.roll }) { stat ->
                        val isExpanded = selectedStudentRoll == stat.roll
                        val records = attendanceByRoll[stat.roll] ?: emptyList()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStudentRoll = if (isExpanded) null else stat.roll
                                }
                                .testTag("stat_card_${stat.roll}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (stat.currentConsecutiveAbsences >= 2) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stat.roll,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(stat.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${stat.presentCount} Present / ${stat.totalClasses} Classes",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val pctColor = when {
                                            stat.percentage >= 75f -> Color(0xFF2E7D32)
                                            stat.percentage >= 50f -> Color(0xFFE65100)
                                            else -> Color(0xFFC62828)
                                        }
                                        Text(
                                            "%.1f%%".format(stat.percentage),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = pctColor
                                        )
                                        if (stat.currentConsecutiveAbsences >= 2) {
                                            Text(
                                                "${stat.currentConsecutiveAbsences} Abs. Streak",
                                                color = Color(0xFFC62828),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val progressColor = when {
                                    stat.percentage >= 75f -> Color(0xFF4CAF50)
                                    stat.percentage >= 50f -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                                LinearProgressIndicator(
                                    progress = { (stat.percentage / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Attendance History Timeline:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (records.isEmpty()) {
                                            Text("No attendance recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        } else {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(records.sortedByDescending { it.date }) { r ->
                                                    val isP = r.status == "PRESENT"
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isP) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                r.date.substringAfter("-"),
                                                                fontSize = 10.sp,
                                                                color = if (isP) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                            )
                                                            Text(
                                                                if (isP) "P" else "A",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isP) Color(0xFF1B5E20) else Color(0xFFB71C1C)
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
                    }
                }
            }
        }
    }
}
