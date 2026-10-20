package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.RemoteStudent
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel

@Composable
fun ImportStudentsScreen(
    viewModel: ClassRollViewModel
) {
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var previewStudents by remember { mutableStateOf<List<RemoteStudent>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun parseInput(text: String): List<RemoteStudent> {
        val lines = text.split("\n", "\r\n")
        val result = mutableListOf<RemoteStudent>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.lowercase().startsWith("roll")) continue

            val parts = when {
                trimmed.contains("\t") -> trimmed.split("\t")
                trimmed.contains(",") -> trimmed.split(",")
                trimmed.contains("-") -> trimmed.split("-")
                trimmed.contains(" ") -> {
                    val firstSpace = trimmed.indexOf(' ')
                    listOf(trimmed.substring(0, firstSpace), trimmed.substring(firstSpace + 1))
                }
                else -> listOf(trimmed, "")
            }

            if (parts.isNotEmpty()) {
                val roll = parts[0].trim()
                val name = if (parts.size > 1) parts[1].trim() else "Student $roll"
                if (roll.isNotBlank()) {
                    result.add(RemoteStudent(roll = roll, name = name, active = true))
                }
            }
        }
        return result
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Batch Import Students",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Academic Year: $currentYear",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Format: Enter one student per line as 'Roll, Name' or 'Roll - Name'\nExample:\n1, Ayesha Siddiqua\n2, Tanvir Ahmed\n3, Nusrat Jahan",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    statusMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .testTag("import_text_input"),
                label = { Text("Paste Student List Here") },
                placeholder = { Text("1, Ayesha\n2, Tanvir\n3, Nusrat") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        previewStudents = parseInput(inputText)
                        statusMessage = if (previewStudents.isNotEmpty()) {
                            "Parsed ${previewStudents.size} students. Review below and tap Import."
                        } else {
                            "No valid student entries found."
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_students_button")
                ) {
                    Text("Parse & Preview")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        inputText = ""
                        previewStudents = emptyList()
                        statusMessage = null
                    },
                    modifier = Modifier.testTag("clear_import_button")
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusMessage?.contains("fail", ignoreCase = true) == true)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (previewStudents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preview (${previewStudents.size} students)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            statusMessage = "Importing..."
                            viewModel.importStudents(currentYear, previewStudents) { res ->
                                statusMessage = if (res == "Success") {
                                    "Import successful! Added ${previewStudents.size} students to $currentYear."
                                } else {
                                    "Import failed: $res"
                                }
                            }
                        },
                        modifier = Modifier.testTag("import_confirm_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import to $currentYear")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(previewStudents) { student ->
                        ListItem(
                            headlineContent = { Text(student.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Roll: ${student.roll}") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
