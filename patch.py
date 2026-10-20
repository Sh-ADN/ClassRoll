import re

with open("app/src/main/java/com/aistudio/classroll/jkmxlp/ui/screens/RegisterScreen.kt", "r") as f:
    code = f.read()

# 1. Add var showExportMenu by remember { mutableStateOf(false) }
code = code.replace(
    "var showCsvExportDialog by remember { mutableStateOf(false) }",
    "var showExportMenu by remember { mutableStateOf(false) }"
)

# 2. Add PDF launcher
pdf_launcher_code = """
    var pendingPdfContent by remember { mutableStateOf<ByteArray?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pendingPdfContent != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingPdfContent!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
"""
code = code.replace(
    "val filteredStudents = remember(students, searchQuery) {",
    pdf_launcher_code + "\n    val filteredStudents = remember(students, searchQuery) {"
)

# 3. Replace the old AlertDialog with a DropdownMenu in the top bar
export_logic = """
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Export")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Download Register Grid (CSV)") },
                                    onClick = {
                                        showExportMenu = false
                                        val header = "Roll,Name," + dates.joinToString(",") { it.substringAfterLast("-") }
                                        val rows = filteredStudents.map { student ->
                                            val attValues = dates.joinToString(",") { date ->
                                                attendanceMap[student.roll]?.get(date)?.status ?: ""
                                            }
                                            "${student.roll},\"${student.name}\",$attValues"
                                        }
                                        val csvContent = (listOf(header) + rows).joinToString("\n")
                                        pendingCsvContent = csvContent
                                        saveCsvLauncher.launch("register_${selectedMonthStr}.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download Summary (CSV)") },
                                    onClick = {
                                        showExportMenu = false
                                        val workingDays = dates.count { !isWeekend(it) && !holidays.contains(it) }
                                        val header = "Roll,Name,Present,Absent,Percentage\n"
                                        val rows = filteredStudents.joinToString("\n") { student ->
                                            val present = dates.count { attendanceMap[student.roll]?.get(it)?.status == "P" }
                                            val absent = dates.count { attendanceMap[student.roll]?.get(it)?.status == "A" }
                                            val pct = if (workingDays > 0) (present * 100) / workingDays else 0
                                            "${student.roll},\"${student.name}\",$present,$absent,$pct%"
                                        }
                                        pendingCsvContent = header + rows
                                        saveCsvLauncher.launch("summary_${selectedMonthStr}.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download Summary (PDF)") },
                                    onClick = {
                                        showExportMenu = false
                                        pendingPdfContent = generateSummaryPdf(selectedMonthTitle, filteredStudents, attendanceMap, dates, holidays)
                                        savePdfLauncher.launch("summary_${selectedMonthStr}.pdf")
                                    }
                                )
                            }
                        }
"""

code = code.replace(
    """                        IconButton(onClick = { showCsvExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }""",
    export_logic
)

# Remove the old if (showCsvExportDialog) { ... }
code = re.sub(r'    if \(showCsvExportDialog\) \{.*?(?=    val horizontalScrollState = rememberScrollState\(\))', '', code, flags=re.DOTALL)

# Add generateSummaryPdf function at the end
pdf_function = """

private fun generateSummaryPdf(
    monthTitle: String,
    students: List<com.aistudio.classroll.jkmxlp.data.StudentEntity>,
    attendanceMap: Map<String, Map<String, com.aistudio.classroll.jkmxlp.data.AttendanceRecordEntity>>,
    dates: List<String>,
    holidays: Set<String>
): ByteArray {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    val paint = android.graphics.Paint()

    paint.textSize = 18f
    paint.isFakeBoldText = true
    canvas.drawText("Attendance Summary - $monthTitle", 50f, 50f, paint)

    paint.textSize = 12f
    paint.isFakeBoldText = true
    canvas.drawText("Roll", 50f, 100f, paint)
    canvas.drawText("Name", 120f, 100f, paint)
    canvas.drawText("Present", 350f, 100f, paint)
    canvas.drawText("Absent", 420f, 100f, paint)
    canvas.drawText("Percentage", 490f, 100f, paint)

    paint.isFakeBoldText = false
    var y = 130f
    val workingDays = dates.count { !isWeekend(it) && !holidays.contains(it) }

    var pageNumber = 1
    for (student in students) {
        if (y > 800f) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50f
        }
        val present = dates.count { attendanceMap[student.roll]?.get(it)?.status == "P" }
        val absent = dates.count { attendanceMap[student.roll]?.get(it)?.status == "A" }
        val pct = if (workingDays > 0) (present * 100) / workingDays else 0

        canvas.drawText(student.roll, 50f, y, paint)
        canvas.drawText(student.name, 120f, y, paint)
        canvas.drawText(present.toString(), 350f, y, paint)
        canvas.drawText(absent.toString(), 420f, y, paint)
        canvas.drawText("$pct%", 490f, y, paint)
        y += 20f
    }

    pdfDocument.finishPage(page)
    val outputStream = java.io.ByteArrayOutputStream()
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
    return outputStream.toByteArray()
}
"""
code += pdf_function

with open("app/src/main/java/com/aistudio/classroll/jkmxlp/ui/screens/RegisterScreen.kt", "w") as f:
    f.write(code)

