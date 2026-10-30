package com.aistudio.classroll.jkmxlp.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class StatSortOption {
    ROLL, NAME, LOWEST_ATTENDANCE, HIGHEST_FINE, CONSECUTIVE_ABSENCES
}

enum class SummaryViewMode {
    OVERVIEW_CARDS, MONTHLY_FINE_SHEET
}

enum class StudentLogFilter {
    ABSENT_ONLY, PRESENT_ONLY, ALL_DATES
}

data class StudentStat(
    val roll: String,
    val name: String,
    val presentCount: Int,
    val absentCount: Int,
    val dueFine: Int,
    val percentage: Float,
    val totalClasses: Int,
    val maxConsecutiveAbsences: Int,
    val currentConsecutiveAbsences: Int,
    val monthlyAbsent: Map<String, Int>,
    val monthlyPresent: Map<String, Int>,
    val statusText: String,
    val isActiveInLatestMonth: Boolean
)

private fun isWeekendDate(dateStr: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(dateStr) ?: return false
        val cal = Calendar.getInstance()
        cal.time = parsed
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        dow == Calendar.FRIDAY || dow == Calendar.SATURDAY
    } catch (_: Exception) {
        false
    }
}

private fun formatShortMonth(yearMonth: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM", Locale.US)
        val date = parser.parse(yearMonth) ?: return yearMonth
        SimpleDateFormat("MMM", Locale.US).format(date)
    } catch (_: Exception) {
        yearMonth
    }
}

private fun formatCutoffDisplay(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(dateStr) ?: return dateStr
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatDayAndDate(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(date)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatShortDayMonth(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(dateStr) ?: return dateStr
        SimpleDateFormat("dd MMM", Locale.US).format(date)
    } catch (_: Exception) {
        dateStr
    }
}

private fun escapeCsvCell(value: String): String {
    return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDashboardScreen(viewModel: ClassRollViewModel) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendanceForYear.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val finePerAbsentDay by viewModel.finePerAbsentDay.collectAsStateWithLifecycle()

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var cutoffDate by remember { mutableStateOf(todayStr) }
    var showCutoffDatePicker by remember { mutableStateOf(false) }
    var showFineRateDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    var viewMode by remember { mutableStateOf(SummaryViewMode.MONTHLY_FINE_SHEET) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(StatSortOption.ROLL) }
    var showConsecutiveAlertsOnly by remember { mutableStateOf(false) }
    var selectedStudentRoll by remember { mutableStateOf<String?>(null) }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alertCardBg = if (isDark) Color(0xFF3E1A1E) else Color(0xFFFFEBEE)
    val alertBannerBg = if (isDark) Color(0xFF4D1F24) else Color(0xFFFFCDD2)
    val alertTextColor = if (isDark) Color(0xFFFFB4AB) else Color(0xFFB71C1C)
    val alertAccentColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)
    val warningCardBg = if (isDark) Color(0xFF382D14) else Color(0xFFFFF8E1)
    val warningIconColor = if (isDark) Color(0xFFFFD54F) else Color(0xFFF57F17)
    val presentCardBg = if (isDark) Color(0xFF193623) else Color(0xFFE8F5E9)
    val goodAccentColor = if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
    val fineBadgeBg = if (isDark) Color(0xFF3B2314) else Color(0xFFFFF3E0)
    val fineAccentColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
    val tableHeaderBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE3F2FD)
    val schoolDaysRowBg = if (isDark) Color(0xFF1A2E26) else Color(0xFFE8F5E9)
    val totalFooterBg = if (isDark) Color(0xFF2D231E) else Color(0xFFFFF8E1)
    val chartTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.18f else 0.12f)

    // Filter attendance records up to cutoffDate and excluding weekends/holidays
    val validAttendanceTillCutoff = remember(allAttendance, cutoffDate, holidays) {
        // A weekend check parses a date, which is slow. Do it once per distinct date (about
        // 100-200) instead of once per record (10,000+ for a class this size).
        val weekendByDate = HashMap<String, Boolean>()
        allAttendance.filter { record ->
            val d = record.date
            // The shape check also guarantees d.substring(0, 7) further down can never crash.
            d.length == 10 && d[4] == '-' && d[7] == '-' &&
                d <= cutoffDate &&
                (record.status == "P" || record.status == "A") &&
                !holidays.contains(d) &&
                !weekendByDate.getOrPut(d) { isWeekendDate(d) }
        }
    }

    // Active months (yyyy-MM) sorted chronologically where at least one attendance record exists
    val activeMonths = remember(validAttendanceTillCutoff) {
        validAttendanceTillCutoff
            .mapNotNull { r -> if (r.date.length >= 7) r.date.substring(0, 7) else null }
            .distinct()
            .sorted()
    }

    // School days counted per month = distinct dates in that month where attendance was logged
    val schoolDaysByMonth = remember(validAttendanceTillCutoff, activeMonths) {
        val byMonth = validAttendanceTillCutoff.groupBy { it.date.substring(0, 7) }
        activeMonths.associateWith { ym ->
            byMonth[ym]?.map { it.date }?.distinct()?.size ?: 0
        }
    }

    val totalSchoolDaysCounted = remember(schoolDaysByMonth) {
        schoolDaysByMonth.values.sum()
    }

    // Latest active month in class to detect students whose records ended earlier
    val latestClassMonth = activeMonths.lastOrNull()

    // Calculate student analytical stats up to cutoffDate
    val studentStats = remember(students, validAttendanceTillCutoff, activeMonths, finePerAbsentDay, latestClassMonth) {
        val recordsByStudent = validAttendanceTillCutoff.groupBy { it.roll }
        students.map { student ->
            val records = (recordsByStudent[student.roll] ?: emptyList()).sortedBy { it.date }
            val presentCount = records.count { it.status == "P" }
            val absentCount = records.count { it.status == "A" }
            val total = presentCount + absentCount
            val dueFine = absentCount * finePerAbsentDay
            val percentage = if (total > 0) (presentCount.toFloat() / total) * 100f else 0f

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

            val recordsByMonth = records.groupBy { it.date.substring(0, 7) }
            val monthlyAbsent = activeMonths.associateWith { ym ->
                recordsByMonth[ym]?.count { it.status == "A" } ?: 0
            }
            val monthlyPresent = activeMonths.associateWith { ym ->
                recordsByMonth[ym]?.count { it.status == "P" } ?: 0
            }

            val lastRecordDate = records.lastOrNull()?.date
            val lastRecordMonth = lastRecordDate?.takeIf { it.length >= 7 }?.substring(0, 7)

            val (statusText, isActive) = when {
                !student.active && lastRecordDate != null ->
                    "Inactive (ends ${formatShortDayMonth(lastRecordDate)})" to false
                !student.active ->
                    "Inactive" to false
                records.isEmpty() -> "Not in ClassRoll" to false
                presentCount == 0 && lastRecordMonth != null && latestClassMonth != null && lastRecordMonth < latestClassMonth ->
                    "No attendance (ends ${formatShortDayMonth(lastRecordDate)})" to false
                lastRecordMonth != null && latestClassMonth != null && lastRecordMonth < latestClassMonth ->
                    "ClassRoll ends ${formatShortDayMonth(lastRecordDate)}" to false
                else -> "Active" to true
            }

            StudentStat(
                roll = student.roll,
                name = student.name,
                presentCount = presentCount,
                absentCount = absentCount,
                dueFine = dueFine,
                percentage = percentage,
                totalClasses = total,
                maxConsecutiveAbsences = maxConsecutive,
                currentConsecutiveAbsences = run,
                monthlyAbsent = monthlyAbsent,
                monthlyPresent = monthlyPresent,
                statusText = statusText,
                isActiveInLatestMonth = isActive
            )
        }
    }

    // Class Overall Totals
    val totalStudents = studentStats.size
    val totalClassPresent = studentStats.sumOf { it.presentCount }
    val totalClassAbsent = studentStats.sumOf { it.absentCount }
    val totalClassDueFine = studentStats.sumOf { it.dueFine }
    val totalClassRecords = totalClassPresent + totalClassAbsent
    val classAvgPct = if (totalClassRecords > 0) (totalClassPresent.toFloat() / totalClassRecords) * 100f else 0f
    val consecutiveAbsenceCount = studentStats.count { it.maxConsecutiveAbsences >= 2 }

    val monthlyClassAbsentTotals = remember(studentStats, activeMonths) {
        activeMonths.associateWith { ym ->
            studentStats.sumOf { it.monthlyAbsent[ym] ?: 0 }
        }
    }

    val filteredAndSortedStats = remember(studentStats, searchQuery, sortOption, showConsecutiveAlertsOnly) {
        var filtered = if (searchQuery.isBlank()) {
            studentStats
        } else {
            studentStats.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.roll.contains(searchQuery, ignoreCase = true) ||
                    it.statusText.contains(searchQuery, ignoreCase = true)
            }
        }

        if (showConsecutiveAlertsOnly) {
            filtered = filtered.filter { it.maxConsecutiveAbsences >= 2 }
        }

        when (sortOption) {
            StatSortOption.ROLL -> filtered.sortedBy { it.roll.toIntOrNull() ?: Int.MAX_VALUE }
            StatSortOption.NAME -> filtered.sortedBy { it.name }
            StatSortOption.LOWEST_ATTENDANCE -> filtered.sortedWith(
                compareBy<StudentStat> { if (it.totalClasses == 0) 1 else 0 }.thenBy { it.percentage }
            )
            StatSortOption.HIGHEST_FINE -> filtered.sortedByDescending { it.dueFine }
            StatSortOption.CONSECUTIVE_ABSENCES -> filtered.sortedByDescending { it.maxConsecutiveAbsences }
        }
    }

    // Exports always cover the WHOLE class in roll order, whatever search, sort or "alerts
    // only" filter is active on screen. Before, a filter silently shrank the export and its
    // TOTAL row, which matters when this sheet is used to collect money.
    val exportStats = remember(studentStats) {
        studentStats.sortedBy { it.roll.toIntOrNull() ?: Int.MAX_VALUE }
    }

    val maxTotal = maxOf(totalSchoolDaysCounted, studentStats.maxOfOrNull { it.totalClasses } ?: 1, 1)

    // CSV Export Launcher (matches Class9B_Absent_Due_Fine spreadsheet format)
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val sb = StringBuilder()
                val numCols = 2 + activeMonths.size + 4 // Roll, Name, Months..., Total Present, Total Absent, Due Fine (৳), Status
                val trailingCommas = ",".repeat((numCols - 1).coerceAtLeast(1))
                val formattedCutoff = formatCutoffDisplay(cutoffDate)
                val yearLabel = currentYear.ifBlank { "Academic Year" }

                // Row 1: Title Banner
                sb.append(escapeCsvCell("$yearLabel — Absent days & due fine (till $formattedCutoff)"))
                sb.append(trailingCommas).append("\n")

                // Row 2: Fine per absent day
                val fineRowPad = ",".repeat((numCols - 4).coerceAtLeast(1))
                sb.append(",Fine per absent day (৳),$finePerAbsentDay,← change here to recalculate every due")
                sb.append(fineRowPad).append("\n")

                // Row 3: School days counted
                sb.append(",School days counted")
                activeMonths.forEach { ym ->
                    sb.append(",").append(schoolDaysByMonth[ym] ?: 0)
                }
                sb.append(",").append(totalSchoolDaysCounted) // under Total Present
                sb.append(",").append(totalSchoolDaysCounted) // under Total Absent
                sb.append(",,\n")

                // Row 4: Column Headers
                sb.append("Roll,Name")
                activeMonths.forEach { ym ->
                    sb.append(",").append(escapeCsvCell(formatShortMonth(ym)))
                }
                sb.append(",Total Present,Total Absent,Due Fine (৳),Status\n")

                // Student Rows (sorted by Roll)
                val sortedForExport = exportStats
                sortedForExport.forEach { st ->
                    sb.append(escapeCsvCell(st.roll)).append(",")
                    sb.append(escapeCsvCell(st.name))
                    activeMonths.forEach { ym ->
                        sb.append(",").append(st.monthlyAbsent[ym] ?: 0)
                    }
                    sb.append(",").append(st.presentCount)
                    sb.append(",").append(st.absentCount)
                    sb.append(",").append(st.dueFine)
                    sb.append(",").append(escapeCsvCell(st.statusText)).append("\n")
                }

                // Footer Row: TOTAL
                sb.append(",TOTAL")
                activeMonths.forEach { ym ->
                    val monthTotal = sortedForExport.sumOf { it.monthlyAbsent[ym] ?: 0 }
                    sb.append(",").append(monthTotal)
                }
                val sumPresent = sortedForExport.sumOf { it.presentCount }
                val sumAbsent = sortedForExport.sumOf { it.absentCount }
                val sumFine = sortedForExport.sumOf { it.dueFine }
                sb.append(",").append(sumPresent)
                sb.append(",").append(sumAbsent)
                // Plain number (no thousands separator) so it stays a number in Excel/Sheets.
                sb.append(",").append(sumFine)
                sb.append(",\n")

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    // Write UTF-8 BOM so Excel/Sheets renders Bengali & Currency symbols properly
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Saved Analytical Fine Sheet CSV!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "CSV Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // PDF Export Launcher (Landscape A4 Analytical Fine Sheet)
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val pdfDocument = PdfDocument()
            try {
                val pageWidth = 842 // A4 Landscape
                val pageHeight = 595
                val margin = 28f
                val usableWidth = pageWidth - (margin * 2)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.rgb(25, 42, 86)
                }
                val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    color = android.graphics.Color.rgb(70, 70, 70)
                }
                val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.rgb(20, 35, 60)
                }
                val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    color = android.graphics.Color.rgb(30, 30, 30)
                }
                val boldCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.rgb(20, 20, 20)
                }
                val redCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.rgb(183, 28, 28)
                }
                val greenCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = android.graphics.Color.rgb(27, 94, 32)
                }
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(200, 205, 215)
                    strokeWidth = 0.7f
                    style = Paint.Style.STROKE
                }
                val headerBgPaint = Paint().apply {
                    color = android.graphics.Color.rgb(227, 242, 253)
                    style = Paint.Style.FILL
                }
                val schoolDaysBgPaint = Paint().apply {
                    color = android.graphics.Color.rgb(232, 245, 233)
                    style = Paint.Style.FILL
                }
                val totalRowBgPaint = Paint().apply {
                    color = android.graphics.Color.rgb(255, 248, 225)
                    style = Paint.Style.FILL
                }
                val zebraBgPaint = Paint().apply {
                    color = android.graphics.Color.rgb(248, 250, 252)
                    style = Paint.Style.FILL
                }

                // Dynamic Column Widths
                val rollW = 34f
                val nameW = 135f
                val presentW = 46f
                val absentW = 48f
                val fineW = 62f
                val statusW = 105f
                val fixedW = rollW + nameW + presentW + absentW + fineW + statusW
                val monthCount = activeMonths.size.coerceAtLeast(1)
                val monthW = ((usableWidth - fixedW) / monthCount).coerceIn(26f, 55f)

                val colWidths = mutableListOf<Float>()
                colWidths.add(rollW)
                colWidths.add(nameW)
                activeMonths.forEach { _ -> colWidths.add(monthW) }
                colWidths.add(presentW)
                colWidths.add(absentW)
                colWidths.add(fineW)
                colWidths.add(statusW)
                val tableWidth = colWidths.sum()

                val rowHeight = 18f
                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var y = margin

                fun drawTableHeaders(startY: Float): Float {
                    var curY = startY
                    // Header Banner
                    val formattedCutoff = formatCutoffDisplay(cutoffDate)
                    val yearLabel = currentYear.ifBlank { "Academic Year" }
                    canvas.drawText(
                        "$yearLabel — Absent Days & Due Fine Report (Till $formattedCutoff)",
                        margin,
                        curY + 14f,
                        titlePaint
                    )
                    curY += 20f
                    canvas.drawText(
                        "Fine per absent day: ৳$finePerAbsentDay   |   Total School Days Counted: $totalSchoolDaysCounted   |   Students: ${exportStats.size}   |   Page $pageNumber",
                        margin,
                        curY + 10f,
                        subtitlePaint
                    )
                    curY += 18f

                    // School days counted row
                    canvas.drawRect(margin, curY, margin + tableWidth, curY + rowHeight, schoolDaysBgPaint)
                    canvas.drawRect(margin, curY, margin + tableWidth, curY + rowHeight, linePaint)
                    var x = margin
                    canvas.drawText("Days", x + 4f, curY + 12.5f, greenCellPaint)
                    x += rollW
                    canvas.drawText("School days counted", x + 4f, curY + 12.5f, greenCellPaint)
                    x += nameW
                    activeMonths.forEach { ym ->
                        canvas.drawLine(x, curY, x, curY + rowHeight, linePaint)
                        val daysStr = (schoolDaysByMonth[ym] ?: 0).toString()
                        canvas.drawText(daysStr, x + 6f, curY + 12.5f, greenCellPaint)
                        x += monthW
                    }
                    canvas.drawLine(x, curY, x, curY + rowHeight, linePaint)
                    canvas.drawText("$totalSchoolDaysCounted", x + 6f, curY + 12.5f, greenCellPaint)
                    x += presentW
                    canvas.drawLine(x, curY, x, curY + rowHeight, linePaint)
                    canvas.drawText("$totalSchoolDaysCounted", x + 6f, curY + 12.5f, greenCellPaint)
                    curY += rowHeight

                    // Column headers row
                    canvas.drawRect(margin, curY, margin + tableWidth, curY + rowHeight, headerBgPaint)
                    canvas.drawRect(margin, curY, margin + tableWidth, curY + rowHeight, linePaint)
                    val headers = mutableListOf("Roll", "Name")
                    activeMonths.forEach { headers.add(formatShortMonth(it)) }
                    headers.addAll(listOf("Present", "Absent", "Fine (৳)", "Status"))

                    x = margin
                    headers.forEachIndexed { idx, h ->
                        if (idx > 0) {
                            canvas.drawLine(x, curY, x, curY + rowHeight, linePaint)
                        }
                        canvas.drawText(h, x + 4f, curY + 12.5f, headerTextPaint)
                        x += colWidths[idx]
                    }
                    return curY + rowHeight
                }

                y = drawTableHeaders(y)

                exportStats.forEachIndexed { index, st ->
                    if (y + rowHeight * 2 > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = drawTableHeaders(margin)
                    }

                    if (index % 2 == 1) {
                        canvas.drawRect(margin, y, margin + tableWidth, y + rowHeight, zebraBgPaint)
                    }
                    canvas.drawRect(margin, y, margin + tableWidth, y + rowHeight, linePaint)

                    var x = margin
                    canvas.drawText(st.roll, x + 4f, y + 12.5f, boldCellPaint)
                    x += rollW

                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                    val truncatedName = if (st.name.length > 22) st.name.take(21) + "…" else st.name
                    canvas.drawText(truncatedName, x + 4f, y + 12.5f, cellPaint)
                    x += nameW

                    activeMonths.forEach { ym ->
                        canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                        val mAbs = st.monthlyAbsent[ym] ?: 0
                        canvas.drawText(
                            mAbs.toString(),
                            x + 6f,
                            y + 12.5f,
                            if (mAbs > 0) redCellPaint else cellPaint
                        )
                        x += monthW
                    }

                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                    canvas.drawText(st.presentCount.toString(), x + 6f, y + 12.5f, greenCellPaint)
                    x += presentW

                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                    canvas.drawText(
                        st.absentCount.toString(),
                        x + 6f,
                        y + 12.5f,
                        if (st.absentCount > 0) redCellPaint else cellPaint
                    )
                    x += absentW

                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                    canvas.drawText(
                        "৳${st.dueFine}",
                        x + 4f,
                        y + 12.5f,
                        if (st.dueFine > 0) redCellPaint else cellPaint
                    )
                    x += fineW

                    canvas.drawLine(x, y, x, y + rowHeight, linePaint)
                    val shortStatus = if (st.statusText.length > 20) st.statusText.take(19) + "…" else st.statusText
                    canvas.drawText(shortStatus, x + 4f, y + 12.5f, cellPaint)

                    y += rowHeight
                }

                // Draw TOTAL Footer Row
                if (y + rowHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = drawTableHeaders(margin)
                }

                canvas.drawRect(margin, y, margin + tableWidth, y + rowHeight, totalRowBgPaint)
                canvas.drawRect(margin, y, margin + tableWidth, y + rowHeight, linePaint)
                var fx = margin + rollW
                canvas.drawLine(fx, y, fx, y + rowHeight, linePaint)
                canvas.drawText("TOTAL", fx + 4f, y + 12.5f, boldCellPaint)
                fx += nameW

                activeMonths.forEach { ym ->
                    canvas.drawLine(fx, y, fx, y + rowHeight, linePaint)
                    val sumM = exportStats.sumOf { it.monthlyAbsent[ym] ?: 0 }
                    canvas.drawText(sumM.toString(), fx + 6f, y + 12.5f, redCellPaint)
                    fx += monthW
                }

                val sumP = exportStats.sumOf { it.presentCount }
                val sumA = exportStats.sumOf { it.absentCount }
                val sumFine = exportStats.sumOf { it.dueFine }
                val formattedFine = NumberFormat.getIntegerInstance(Locale.US).format(sumFine)

                canvas.drawLine(fx, y, fx, y + rowHeight, linePaint)
                canvas.drawText(sumP.toString(), fx + 6f, y + 12.5f, greenCellPaint)
                fx += presentW

                canvas.drawLine(fx, y, fx, y + rowHeight, linePaint)
                canvas.drawText(sumA.toString(), fx + 6f, y + 12.5f, redCellPaint)
                fx += absentW

                canvas.drawLine(fx, y, fx, y + rowHeight, linePaint)
                canvas.drawText("৳$formattedFine", fx + 4f, y + 12.5f, redCellPaint)

                pdfDocument.finishPage(page)

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    pdfDocument.writeTo(out)
                }
                Toast.makeText(context, "Saved Analytical Fine Report PDF!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "PDF Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                pdfDocument.close()
            }
        }
    }

    // Cutoff DatePicker Dialog
    if (showCutoffDatePicker) {
        val initialMillis = remember(cutoffDate) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(cutoffDate)?.time
            } catch (_: Exception) {
                null
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showCutoffDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            cutoffDate = sdf.format(Date(millis))
                        }
                        showCutoffDatePicker = false
                    }
                ) {
                    Text("Apply Cutoff")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            cutoffDate = todayStr
                            showCutoffDatePicker = false
                        }
                    ) {
                        Text("Reset to Today")
                    }
                    TextButton(onClick = { showCutoffDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Edit Fine Rate per Absent Day Dialog
    if (showFineRateDialog) {
        var fineInput by remember(finePerAbsentDay) { mutableStateOf(finePerAbsentDay.toString()) }
        AlertDialog(
            onDismissRequest = { showFineRateDialog = false },
            title = { Text("Fine per Absent Day (৳)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Change the fine rate per absent day to automatically recalculate every student's due fine up to the selected cutoff date. " +
                            "This re-prices every past absence too, so a report you printed earlier at the old rate will no longer match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = fineInput,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() } && value.length <= 5) {
                                fineInput = value
                            }
                        },
                        label = { Text("Fine per absent day (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("fine_rate_input")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Presets:", style = MaterialTheme.typography.labelMedium)
                        listOf(0, 2, 5, 10, 20).forEach { preset ->
                            SuggestionChip(
                                onClick = { fineInput = preset.toString() },
                                label = { Text("৳$preset") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // An empty box must never silently change the money rate.
                        val parsed = fineInput.toIntOrNull()
                        if (parsed != null) {
                            viewModel.updateFinePerAbsentDay(parsed)
                        }
                        showFineRateDialog = false
                    },
                    enabled = fineInput.isNotBlank(),
                    modifier = Modifier.testTag("save_fine_rate_button")
                ) {
                    Text("Recalculate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFineRateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Student Analytical Detail & Absent Log Modal Dialog
    if (selectedStudentRoll != null) {
        val selStudent = studentStats.find { it.roll == selectedStudentRoll }
        var logFilter by remember { mutableStateOf(StudentLogFilter.ABSENT_ONLY) }

        val studentAllCutoffRecords = remember(validAttendanceTillCutoff, selectedStudentRoll) {
            validAttendanceTillCutoff
                .filter { it.roll == selectedStudentRoll }
                .sortedByDescending { it.date }
        }

        val displayedRecords = remember(studentAllCutoffRecords, logFilter) {
            when (logFilter) {
                StudentLogFilter.ABSENT_ONLY -> studentAllCutoffRecords.filter { it.status == "A" }
                StudentLogFilter.PRESENT_ONLY -> studentAllCutoffRecords.filter { it.status == "P" }
                StudentLogFilter.ALL_DATES -> studentAllCutoffRecords
            }
        }

        AlertDialog(
            onDismissRequest = { selectedStudentRoll = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Roll ${selStudent?.roll} — ${selStudent?.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Till ${formatCutoffDisplay(cutoffDate)} • ${selStudent?.statusText ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selectedStudentRoll = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 4-Metric Analytical Summary Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Present", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${selStudent?.presentCount ?: 0}/${selStudent?.totalClasses ?: 0}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = goodAccentColor
                                )
                                Text(
                                    String.format(Locale.US, "%.1f%%", selStudent?.percentage ?: 0f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Absent", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${selStudent?.absentCount ?: 0} days",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((selStudent?.absentCount ?: 0) > 0) alertAccentColor else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "School: $totalSchoolDaysCounted d",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Due Fine", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "৳${selStudent?.dueFine ?: 0}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((selStudent?.dueFine ?: 0) > 0) fineAccentColor else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "@ ৳$finePerAbsentDay/day",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Max Streak", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${selStudent?.maxConsecutiveAbsences ?: 0}d",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if ((selStudent?.maxConsecutiveAbsences ?: 0) >= 2) alertAccentColor else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Cur: ${selStudent?.currentConsecutiveAbsences ?: 0}d",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // Month-by-Month Breakdown Strip
                    if (activeMonths.isNotEmpty() && selStudent != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Monthly Breakdown (Absent / Present):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeMonths) { ym ->
                                val mAbs = selStudent.monthlyAbsent[ym] ?: 0
                                val mPres = selStudent.monthlyPresent[ym] ?: 0
                                val mSchool = schoolDaysByMonth[ym] ?: 0
                                Surface(
                                    color = if (mAbs > 0) alertCardBg else presentCardBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${formatShortMonth(ym)} ($mSchool d)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "A: $mAbs  •  P: $mPres",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (mAbs > 0) alertAccentColor else goodAccentColor
                                        )
                                        if (mAbs > 0) {
                                            Text(
                                                "৳${mAbs * finePerAbsentDay}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fineAccentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Filter Tabs for Absent Log / Present Log / All Dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = logFilter == StudentLogFilter.ABSENT_ONLY,
                            onClick = { logFilter = StudentLogFilter.ABSENT_ONLY },
                            label = { Text("Absent Log (${selStudent?.absentCount ?: 0})") }
                        )
                        FilterChip(
                            selected = logFilter == StudentLogFilter.PRESENT_ONLY,
                            onClick = { logFilter = StudentLogFilter.PRESENT_ONLY },
                            label = { Text("Present (${selStudent?.presentCount ?: 0})") }
                        )
                        FilterChip(
                            selected = logFilter == StudentLogFilter.ALL_DATES,
                            onClick = { logFilter = StudentLogFilter.ALL_DATES },
                            label = { Text("All (${selStudent?.totalClasses ?: 0})") }
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    if (displayedRecords.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (logFilter) {
                                    StudentLogFilter.ABSENT_ONLY -> "Zero absences recorded up to ${formatCutoffDisplay(cutoffDate)}!"
                                    StudentLogFilter.PRESENT_ONLY -> "No present dates recorded up to ${formatCutoffDisplay(cutoffDate)}."
                                    StudentLogFilter.ALL_DATES -> "No attendance records logged for this student up to ${formatCutoffDisplay(cutoffDate)}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                            items(displayedRecords, key = { it.date }) { record ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (record.status == "P") presentCardBg else alertCardBg,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                formatDayAndDate(record.date),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                if (record.status == "A") "Fine: ৳$finePerAbsentDay" else "On time / Present",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (record.status == "A") fineAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (record.status == "P") Color(0xFF4CAF50) else Color(0xFFF44336),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = if (record.status == "P") "Present (P)" else "Absent (A)",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }

                                            Spacer(Modifier.width(6.dp))

                                            TextButton(
                                                onClick = {
                                                    val nextStatus = if (record.status == "P") "A" else "P"
                                                    viewModel.updateCell(record.date, record.roll, nextStatus)
                                                }
                                            ) {
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

    if (students.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No students found. Import or add students first.")
        }
        return
    }

    val formattedTotalFine = remember(totalClassDueFine) {
        NumberFormat.getIntegerInstance(Locale.US).format(totalClassDueFine)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Header Row with Title & Export Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${currentYear.ifBlank { "Class" }} — Analytical & Fine Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Absent days, school days counted & due fine till ${formatCutoffDisplay(cutoffDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                FilledTonalButton(
                    onClick = { showExportMenu = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("export_report_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export", style = MaterialTheme.typography.labelMedium)
                }

                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export Fine Sheet (CSV)") },
                        leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = "CSV") },
                        onClick = {
                            showExportMenu = false
                            val safeYear = currentYear.ifBlank { "ClassRoll" }.replace(" ", "_")
                            exportCsvLauncher.launch("${safeYear}_Absent_Due_Fine_till_${cutoffDate}.csv")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Analytical Report (PDF)") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF") },
                        onClick = {
                            showExportMenu = false
                            val safeYear = currentYear.ifBlank { "ClassRoll" }.replace(" ", "_")
                            exportPdfLauncher.launch("${safeYear}_Absent_Due_Fine_till_${cutoffDate}.pdf")
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Control Bar: Till-Date Cutoff Picker + Fine Rate Chip + Reset to Today
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { showCutoffDatePicker = true },
                leadingIcon = {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = "Select cutoff date",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        "Till: ${formatCutoffDisplay(cutoffDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag("cutoff_date_chip")
            )

            if (cutoffDate != todayStr) {
                AssistChip(
                    onClick = { cutoffDate = todayStr },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to Today",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Today", style = MaterialTheme.typography.labelSmall) }
                )
            }

            AssistChip(
                onClick = { showFineRateDialog = true },
                leadingIcon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit fine per absent day",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        "Fine: ৳$finePerAbsentDay / absent",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag("edit_fine_chip")
            )
        }

        Spacer(Modifier.height(8.dp))

        // 4 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = schoolDaysRowBg)
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("School Days", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "$totalSchoolDaysCounted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = goodAccentColor
                    )
                    Text(
                        "${activeMonths.size} month(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Class Avg", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format(Locale.US, "%.1f%%", classAvgPct),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (classAvgPct < 75f) alertAccentColor else goodAccentColor
                    )
                    Text(
                        "$totalStudents students",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1.15f).clickable { showFineRateDialog = true },
                colors = CardDefaults.cardColors(containerColor = fineBadgeBg)
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Due Fine", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "৳$formattedTotalFine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = fineAccentColor
                    )
                    Text(
                        "$totalClassAbsent total abs.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.weight(0.95f).clickable {
                    showConsecutiveAlertsOnly = !showConsecutiveAlertsOnly
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (consecutiveAbsenceCount > 0) alertCardBg else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("2+ Streak", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "$consecutiveAbsenceCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (consecutiveAbsenceCount > 0) alertAccentColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (showConsecutiveAlertsOnly) "Filtered" else "Alerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Segmented Switcher: Monthly Fine Sheet vs. Overview Cards
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = viewMode == SummaryViewMode.MONTHLY_FINE_SHEET,
                onClick = { viewMode = SummaryViewMode.MONTHLY_FINE_SHEET },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("monthly_fine_sheet_tab")
            ) {
                Text("Monthly Fine Sheet", style = MaterialTheme.typography.labelMedium)
            }
            SegmentedButton(
                selected = viewMode == SummaryViewMode.OVERVIEW_CARDS,
                onClick = { viewMode = SummaryViewMode.OVERVIEW_CARDS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        Icons.Default.ViewAgenda,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("overview_cards_tab")
            ) {
                Text("Overview Cards", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search and Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search roll or name...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(6.dp))

        // Sort Chips Row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Sort:", style = MaterialTheme.typography.labelSmall)
            FilterChip(
                selected = sortOption == StatSortOption.ROLL && !showConsecutiveAlertsOnly,
                onClick = {
                    sortOption = StatSortOption.ROLL
                    showConsecutiveAlertsOnly = false
                },
                label = { Text("Roll", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = sortOption == StatSortOption.HIGHEST_FINE && !showConsecutiveAlertsOnly,
                onClick = {
                    sortOption = StatSortOption.HIGHEST_FINE
                    showConsecutiveAlertsOnly = false
                },
                label = { Text("Highest Fine (৳)", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = sortOption == StatSortOption.LOWEST_ATTENDANCE && !showConsecutiveAlertsOnly,
                onClick = {
                    sortOption = StatSortOption.LOWEST_ATTENDANCE
                    showConsecutiveAlertsOnly = false
                },
                label = { Text("Lowest %", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = sortOption == StatSortOption.CONSECUTIVE_ABSENCES || showConsecutiveAlertsOnly,
                onClick = {
                    if (showConsecutiveAlertsOnly) {
                        showConsecutiveAlertsOnly = false
                        sortOption = StatSortOption.ROLL
                    } else {
                        sortOption = StatSortOption.CONSECUTIVE_ABSENCES
                        showConsecutiveAlertsOnly = true
                    }
                },
                label = { Text("⚠️ 2+ Streak", style = MaterialTheme.typography.labelSmall) }
            )
        }

        Spacer(Modifier.height(6.dp))

        if (viewMode == SummaryViewMode.MONTHLY_FINE_SHEET) {
            MonthlyFineSheetTable(
                students = filteredAndSortedStats,
                activeMonths = activeMonths,
                schoolDaysByMonth = schoolDaysByMonth,
                totalSchoolDays = totalSchoolDaysCounted,
                monthlyClassAbsentTotals = monthlyClassAbsentTotals,
                tableHeaderBg = tableHeaderBg,
                schoolDaysRowBg = schoolDaysRowBg,
                totalFooterBg = totalFooterBg,
                alertAccentColor = alertAccentColor,
                goodAccentColor = goodAccentColor,
                fineAccentColor = fineAccentColor,
                onSelectStudent = { selectedStudentRoll = it }
            )
        } else {
            OverviewStudentCardsList(
                students = filteredAndSortedStats,
                activeMonths = activeMonths,
                totalSchoolDays = totalSchoolDaysCounted,
                chartMax = maxTotal,
                alertCardBg = alertCardBg,
                warningCardBg = warningCardBg,
                fineBadgeBg = fineBadgeBg,
                alertAccentColor = alertAccentColor,
                warningIconColor = warningIconColor,
                goodAccentColor = goodAccentColor,
                fineAccentColor = fineAccentColor,
                chartTrackColor = chartTrackColor,
                onSelectStudent = { selectedStudentRoll = it }
            )
        }
    }
}

@Composable
private fun MonthlyFineSheetTable(
    students: List<StudentStat>,
    activeMonths: List<String>,
    schoolDaysByMonth: Map<String, Int>,
    totalSchoolDays: Int,
    monthlyClassAbsentTotals: Map<String, Int>,
    tableHeaderBg: Color,
    schoolDaysRowBg: Color,
    totalFooterBg: Color,
    alertAccentColor: Color,
    goodAccentColor: Color,
    fineAccentColor: Color,
    onSelectStudent: (String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val stickyColWidth = 152.dp
    val monthColWidth = 52.dp
    val metricColWidth = 72.dp
    val fineColWidth = 86.dp
    val statusColWidth = 136.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    val sumPresent = remember(students) { students.sumOf { it.presentCount } }
    val sumAbsent = remember(students) { students.sumOf { it.absentCount } }
    val sumDueFine = remember(students) { students.sumOf { it.dueFine } }
    val formattedSumFine = remember(sumDueFine) {
        NumberFormat.getIntegerInstance(Locale.US).format(sumDueFine)
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned Header Row: Column Titles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tableHeaderBg)
                    .border(width = 0.5.dp, color = borderColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(stickyColWidth)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Roll & Name",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeMonths.forEach { ym ->
                        TableHeaderCell(text = formatShortMonth(ym), width = monthColWidth)
                    }
                    TableHeaderCell(text = "Present", width = metricColWidth)
                    TableHeaderCell(text = "Total Abs.", width = metricColWidth)
                    TableHeaderCell(text = "Due Fine (৳)", width = fineColWidth)
                    TableHeaderCell(text = "Status", width = statusColWidth, alignLeft = true)
                }
            }

            // Pinned Row 2: School days counted per month + total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(schoolDaysRowBg)
                    .border(width = 0.5.dp, color = borderColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(stickyColWidth)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "School days counted",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = goodAccentColor
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeMonths.forEach { ym ->
                        TableValueCell(
                            text = "${schoolDaysByMonth[ym] ?: 0}",
                            width = monthColWidth,
                            textColor = goodAccentColor,
                            bold = true
                        )
                    }
                    TableValueCell(
                        text = "$totalSchoolDays",
                        width = metricColWidth,
                        textColor = goodAccentColor,
                        bold = true
                    )
                    TableValueCell(
                        text = "$totalSchoolDays",
                        width = metricColWidth,
                        textColor = goodAccentColor,
                        bold = true
                    )
                    TableValueCell(
                        text = "—",
                        width = fineColWidth,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TableValueCell(
                        text = "Tap row for log",
                        width = statusColWidth,
                        textColor = MaterialTheme.colorScheme.primary,
                        alignLeft = true
                    )
                }
            }

            // Scrollable Student Rows
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(students, key = { it.roll }) { st ->
                    val rowBg = if (!st.isActiveInLatestMonth) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .clickable { onSelectStudent(st.roll) }
                            .border(width = 0.5.dp, color = borderColor),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sticky Left Cell: Roll + Name
                        Row(
                            modifier = Modifier
                                .width(stickyColWidth)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = st.roll,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = st.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Horizontally Scrollable Monthly Absences + Totals
                        Row(
                            modifier = Modifier.horizontalScroll(horizontalScrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeMonths.forEach { ym ->
                                val mAbs = st.monthlyAbsent[ym] ?: 0
                                TableValueCell(
                                    text = "$mAbs",
                                    width = monthColWidth,
                                    textColor = if (mAbs > 0) alertAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    bold = mAbs > 0
                                )
                            }
                            TableValueCell(
                                text = "${st.presentCount}",
                                width = metricColWidth,
                                textColor = goodAccentColor,
                                bold = true
                            )
                            TableValueCell(
                                text = "${st.absentCount}",
                                width = metricColWidth,
                                textColor = if (st.absentCount > 0) alertAccentColor else MaterialTheme.colorScheme.onSurface,
                                bold = true
                            )
                            TableValueCell(
                                text = "৳${st.dueFine}",
                                width = fineColWidth,
                                textColor = if (st.dueFine > 0) fineAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                bold = st.dueFine > 0
                            )
                            TableValueCell(
                                text = st.statusText,
                                width = statusColWidth,
                                textColor = if (st.isActiveInLatestMonth) goodAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                alignLeft = true
                            )
                        }
                    }
                }
            }

            // Pinned Bottom Row: TOTAL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(totalFooterBg)
                    .border(width = 1.dp, color = borderColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(stickyColWidth)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        "TOTAL (${students.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeMonths.forEach { ym ->
                        val monthSum = students.sumOf { it.monthlyAbsent[ym] ?: 0 }
                        TableValueCell(
                            text = "$monthSum",
                            width = monthColWidth,
                            textColor = alertAccentColor,
                            bold = true
                        )
                    }
                    TableValueCell(
                        text = "$sumPresent",
                        width = metricColWidth,
                        textColor = goodAccentColor,
                        bold = true
                    )
                    TableValueCell(
                        text = "$sumAbsent",
                        width = metricColWidth,
                        textColor = alertAccentColor,
                        bold = true
                    )
                    TableValueCell(
                        text = "৳$formattedSumFine",
                        width = fineColWidth,
                        textColor = fineAccentColor,
                        bold = true
                    )
                    TableValueCell(
                        text = "",
                        width = statusColWidth,
                        alignLeft = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: Dp,
    alignLeft: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = if (alignLeft) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TableValueCell(
    text: String,
    width: Dp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
    alignLeft: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = if (alignLeft) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OverviewStudentCardsList(
    students: List<StudentStat>,
    activeMonths: List<String>,
    totalSchoolDays: Int,
    chartMax: Int,
    alertCardBg: Color,
    warningCardBg: Color,
    fineBadgeBg: Color,
    alertAccentColor: Color,
    warningIconColor: Color,
    goodAccentColor: Color,
    fineAccentColor: Color,
    chartTrackColor: Color,
    onSelectStudent: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(students, key = { it.roll }) { stat ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectStudent(stat.roll) },
                colors = CardDefaults.cardColors(
                    containerColor = if (stat.maxConsecutiveAbsences >= 2) alertCardBg
                    else if (stat.totalClasses > 0 && stat.percentage < 75f) warningCardBg
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                "${stat.roll} - ${stat.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (stat.maxConsecutiveAbsences >= 2) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "⚠️ ${stat.maxConsecutiveAbsences}d Streak",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = fineBadgeBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Fine: ৳${stat.dueFine}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = fineAccentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            if (stat.totalClasses > 0 && stat.percentage < 75f && stat.maxConsecutiveAbsences < 2) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Low Attendance",
                                    tint = warningIconColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (stat.totalClasses > 0) String.format(Locale.US, "%.1f%%", stat.percentage) else "N/A",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (stat.maxConsecutiveAbsences >= 2) alertAccentColor
                                else if (stat.totalClasses > 0 && stat.percentage < 75f) warningIconColor
                                else goodAccentColor
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Progress bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val presentRatio = stat.presentCount.toFloat() / chartMax.toFloat()
                                val absentRatio = stat.absentCount.toFloat() / chartMax.toFloat()

                                val presentWidth = size.width * presentRatio
                                val absentWidth = size.width * absentRatio

                                drawRoundRect(
                                    color = chartTrackColor,
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
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "P:${stat.presentCount}  A:${stat.absentCount}  (of $totalSchoolDays d)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Compact Month-by-Month Absence Pills + Status
                    if (activeMonths.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeMonths.forEach { ym ->
                                val mAbs = stat.monthlyAbsent[ym] ?: 0
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${formatShortMonth(ym)}: $mAbs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mAbs > 0) alertAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (!stat.isActiveInLatestMonth) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = stat.statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
