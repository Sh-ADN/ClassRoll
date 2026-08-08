package com.aistudio.classroll.jkmxlp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.RemoteStudent
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ImportStudentsScreen(viewModel: ClassRollViewModel) {
    var previewStudents by remember { mutableStateOf(emptyList<RemoteStudent>()) }
    var statusMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    
    val sampleCsv = "Roll,Name\n1,John Doe\n2,Jane Smith\n3,Alex Johnson"

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    val parsed = mutableListOf<RemoteStudent>()
                    reader.useLines { lines ->
                        var isHeader = true
                        for (line in lines) {
                            if (isHeader) { isHeader = false; continue }
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val roll = parts[0].trim()
                                val name = parts[1].trim()
                                if (roll.isNotEmpty() && name.isNotEmpty()) {
                                    parsed.add(RemoteStudent(roll = roll, name = name))
                                }
                            }
                        }
                    }
                    previewStudents = parsed
                    statusMessage = "Found ${parsed.size} students."
                }
            } catch (e: Exception) {
                statusMessage = "Error reading CSV"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Import Student Roster", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Select a CSV file containing Roll Number and Name in comma-separated format.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch("*/*") }) {
                Text("Select CSV File")
            }

            OutlinedButton(onClick = {
                clipboardManager.setText(AnnotatedString(sampleCsv))
                statusMessage = "Sample CSV copied to clipboard!"
            }) {
                Text("Copy Sample CSV")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Text(statusMessage, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        
        if (previewStudents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Preview (${previewStudents.size} students):", style = MaterialTheme.typography.titleMedium)
            
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(previewStudents) { student ->
                    ListItem(
                        headlineContent = { Text(student.name) },
                        supportingContent = { Text("Roll: ${student.roll}") }
                    )
                }
            }
            
            Button(
                onClick = { 
                    statusMessage = "Importing..."
                    viewModel.importStudents(currentYear, previewStudents) { result ->
                        statusMessage = if (result == "Success") "Import successful!" else "Import failed: $result"
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = currentYear.isNotBlank()
            ) {
                Text("Import to ${if(currentYear.isNotBlank()) currentYear else "Select Year in Settings"}")
            }
        }
    }
}
