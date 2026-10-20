package com.aistudio.classroll.jkmxlp.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AuthManager
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ClassRollViewModel,
    authManager: AuthManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val currentThemeMode by viewModel.appTheme.collectAsStateWithLifecycle()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val cloudProgress by viewModel.cloudProgress.collectAsStateWithLifecycle()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()
    val currentUser by authManager.authStateFlow.collectAsStateWithLifecycle(initialValue = authManager.currentUser)

    var yearInput by remember { mutableStateOf(currentYear) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showRestoreBackupDialog by remember { mutableStateOf(false) }
    var backupJsonText by remember { mutableStateOf("") }
    var restoreInputText by remember { mutableStateOf("") }

    var showClearAttendanceDialog by remember { mutableStateOf(false) }
    var showClearStudentsDialog by remember { mutableStateOf(false) }

    val daysSinceBackup = if (lastBackupTimestamp > 0L) {
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastBackupTimestamp)
    } else {
        -1L
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Academic Year Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Academic Year Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Current active year: $currentYear", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("2024", "2025", "2026", "2027").forEach { yr ->
                            FilterChip(
                                selected = currentYear == yr,
                                onClick = { viewModel.updateSettings(yr) },
                                label = { Text(yr) },
                                modifier = Modifier.testTag("year_chip_$yr")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = yearInput,
                            onValueChange = { yearInput = it },
                            label = { Text("Custom Year") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_year_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (yearInput.isNotBlank()) {
                                    viewModel.updateSettings(yearInput.trim())
                                }
                            },
                            modifier = Modifier.testTag("apply_year_button")
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Theme Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Theme & Color Palette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "system" to "System",
                            "light" to "Light",
                            "dark" to "Dark",
                            "forest" to "Forest",
                            "ocean" to "Ocean"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = currentThemeMode == mode,
                                onClick = { viewModel.updateTheme(mode) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("theme_chip_$mode")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Cloud Sync & Backup Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloud Firestore Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentUser != null) {
                        Text("Connected: ${currentUser?.email}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                    } else {
                        Text("Running in Offline Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isCloudSyncing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(cloudProgress, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (currentUser == null) {
                                        statusMessage = "Please sign in with Google to sync with cloud."
                                    } else {
                                        viewModel.syncToCloud { success, msg ->
                                            statusMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_to_cloud_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Up")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (currentUser == null) {
                                        statusMessage = "Please sign in with Google to pull from cloud."
                                    } else {
                                        viewModel.restoreFromCloud { success, msg ->
                                            statusMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("restore_from_cloud_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (currentUser != null) {
                            OutlinedButton(
                                onClick = {
                                    authManager.signOut(scope) {
                                        viewModel.setOfflineMode(false)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign Out of Google Account")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        authManager.signInWithGoogle(
                                            activity = activity,
                                            scope = scope,
                                            onSuccess = { user ->
                                                viewModel.setOfflineMode(false)
                                                statusMessage = "Signed in as ${user.email}"
                                            },
                                            onError = { err -> statusMessage = err }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign in with Google")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Daily Attendance Reminder Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Daily Attendance Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { viewModel.setReminderEnabled(it) },
                            modifier = Modifier.testTag("reminder_toggle")
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Send notification if attendance not recorded by:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("07:00", "08:00", "09:00", "10:00", "12:00").forEach { time ->
                                FilterChip(
                                    selected = reminderTime == time,
                                    onClick = { viewModel.setReminderTime(time) },
                                    label = { Text(time) },
                                    modifier = Modifier.testTag("reminder_time_$time")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Local JSON Backup & Restore Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Local Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val backupStatus = when {
                        daysSinceBackup == -1L -> "No backup created yet."
                        daysSinceBackup == 0L -> "Last backup: Today"
                        else -> "Last backup: $daysSinceBackup days ago"
                    }
                    Text(backupStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackupJson { json ->
                                    backupJsonText = json
                                    showExportBackupDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_json_button")
                        ) {
                            Text("Export JSON")
                        }

                        OutlinedButton(
                            onClick = {
                                restoreInputText = ""
                                showRestoreBackupDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("restore_json_button")
                        ) {
                            Text("Restore JSON")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Danger Zone
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Danger Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearAttendanceDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("clear_attendance_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear Attendance Records for Year '$currentYear'")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showClearStudentsDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("clear_students_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear Students for Year '$currentYear'")
                    }
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = statusMessage ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Export Dialog
    if (showExportBackupDialog) {
        AlertDialog(
            onDismissRequest = { showExportBackupDialog = false },
            title = { Text("Backup JSON") },
            text = {
                Column {
                    Text("Copy this backup text to keep a safe copy:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ClassRoll Backup", backupJsonText))
                    Toast.makeText(context, "Backup copied to Clipboard!", Toast.LENGTH_SHORT).show()
                    viewModel.markBackupDone()
                    showExportBackupDialog = false
                }) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportBackupDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Restore Dialog
    if (showRestoreBackupDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreBackupDialog = false },
            title = { Text("Restore Backup JSON") },
            text = {
                Column {
                    Text("Paste your exported ClassRoll JSON text below:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreInputText,
                        onValueChange = { restoreInputText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("{\n  \"version\": 2,\n  ...") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (restoreInputText.isNotBlank()) {
                        viewModel.restoreBackupJson(restoreInputText) { success, msg ->
                            statusMessage = msg
                        }
                        showRestoreBackupDialog = false
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreBackupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear attendance confirm
    if (showClearAttendanceDialog) {
        AlertDialog(
            onDismissRequest = { showClearAttendanceDialog = false },
            title = { Text("Clear Attendance Records?") },
            text = { Text("Are you sure you want to delete all attendance records for year '$currentYear'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAttendance()
                        statusMessage = "Attendance records cleared for $currentYear"
                        showClearAttendanceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Attendance")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAttendanceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear students confirm
    if (showClearStudentsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStudentsDialog = false },
            title = { Text("Clear Students?") },
            text = { Text("Are you sure you want to delete all students for year '$currentYear'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStudents()
                        statusMessage = "Students cleared for $currentYear"
                        showClearStudentsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Students")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStudentsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
