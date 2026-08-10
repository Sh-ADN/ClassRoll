package com.aistudio.classroll.jkmxlp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(viewModel: ClassRollViewModel) {
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val currentThemeMode by viewModel.appTheme.collectAsStateWithLifecycle()
    var yearInput by remember(currentYear) { mutableStateOf(currentYear) }
    var showClearAttendanceDialog by remember { mutableStateOf(false) }
    var showClearStudentsDialog by remember { mutableStateOf(false) }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showRestoreBackupDialog by remember { mutableStateOf(false) }
    
    var backupJsonText by remember { mutableStateOf("") }
    var restoreInputText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var backupCopyMsg by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // FIX: was a hardcoded list that would need a manual code edit every
    // few years. Now derived from years that actually have data, loaded
    // once when this screen appears.
    val presetYears by viewModel.availableYears.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.loadAvailableYears()
    }

    // NEW: backup reminder + daily attendance reminder state.
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            statusMessage = "Reminder needs notification permission to work. Not enabled."
        }
    }

    val themeOptions = listOf(
        "SYSTEM" to "System",
        "LIGHT" to "Light",
        "DARK" to "Dark Mode",
        "FOREST" to "Forest Green",
        "OCEAN" to "Deep Ocean"
    )

    // NEW: proper file-based backup via the system file picker (Storage
    // Access Framework), instead of only a clipboard blob that gets
    // unwieldy once you've got months of records.
    var pendingExportJson by remember { mutableStateOf("") }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportJson.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportJson.toByteArray())
                }
                statusMessage = "Backup saved to file."
                viewModel.markBackupDone()
            } catch (e: Exception) {
                statusMessage = "Failed to save backup file: ${e.message}"
            }
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (json.isNotBlank()) {
                    viewModel.restoreBackupJson(json) { success, msg ->
                        statusMessage = msg
                    }
                } else {
                    statusMessage = "Selected file was empty."
                }
            } catch (e: Exception) {
                statusMessage = "Failed to read backup file: ${e.message}"
            }
        }
    }

    if (showExportBackupDialog) {
        AlertDialog(
            onDismissRequest = { 
                showExportBackupDialog = false
                backupCopyMsg = ""
            },
            title = { Text("Offline Backup (JSON)") },
            text = {
                Column {
                    Text("Copy or save your full offline database backup:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                    if (backupCopyMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(backupCopyMsg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(backupJsonText))
                    backupCopyMsg = "Backup copied to Clipboard!"
                }) {
                    Text("Copy Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExportBackupDialog = false
                    backupCopyMsg = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRestoreBackupDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreBackupDialog = false },
            title = { Text("Restore Offline Backup") },
            text = {
                Column {
                    Text(
                        "Paste your backup JSON below to restore students and attendance records. WARNING: Existing local records will be overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreInputText,
                        onValueChange = { restoreInputText = it },
                        placeholder = { Text("Paste JSON backup here...") },
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreInputText.isNotBlank()) {
                            viewModel.restoreBackupJson(restoreInputText) { success, msg ->
                                statusMessage = msg
                                if (success) {
                                    showRestoreBackupDialog = false
                                    restoreInputText = ""
                                }
                            }
                        }
                    },
                    enabled = restoreInputText.isNotBlank()
                ) {
                    Text("Restore Database")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreBackupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAttendanceDialog) {
        AlertDialog(
            onDismissRequest = { showClearAttendanceDialog = false },
            title = { Text("Clear Attendance Records?") },
            text = { Text("Are you sure you want to delete all attendance records for year '$currentYear'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAttendance()
                        showClearAttendanceDialog = false
                        statusMessage = "Attendance records cleared for $currentYear"
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

    if (showClearStudentsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStudentsDialog = false },
            title = { Text("Clear Student Roster?") },
            text = { Text("Are you sure you want to delete all students for year '$currentYear'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStudents()
                        showClearStudentsDialog = false
                        statusMessage = "Student roster cleared for $currentYear"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Roster")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStudentsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        // App Theme Selector Section
        Text("App Theme & Dark Mode", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themeOptions.take(3).forEach { (code, label) ->
                FilterChip(
                    selected = currentThemeMode == code,
                    onClick = { viewModel.updateTheme(code) },
                    label = { Text(label) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themeOptions.drop(3).forEach { (code, label) ->
                FilterChip(
                    selected = currentThemeMode == code,
                    onClick = { viewModel.updateTheme(code) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Academic Year Config Section
        Text("Academic Year Configuration", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = yearInput,
            onValueChange = { yearInput = it },
            label = { Text("Academic Year (e.g. 2026)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetYears.forEach { yr ->
                FilterChip(
                    selected = yearInput == yr,
                    onClick = {
                        yearInput = yr
                        viewModel.updateSettings(yr)
                        statusMessage = "Switched to year $yr"
                    },
                    label = { Text(yr) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { 
                viewModel.updateSettings(yearInput) 
                statusMessage = "Settings saved"
            }, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Year Settings")
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Offline Backup & Restore Section
        Text("Offline Backup & Restore", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Save to a file regularly \u2014 this is your only copy of the data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // NEW: nudges you if it's been a while since your last backup,
        // since local storage is now the only copy of this year's data.
        val daysSinceBackup = if (lastBackupTimestamp == 0L) {
            -1
        } else {
            ((System.currentTimeMillis() - lastBackupTimestamp) / (1000 * 60 * 60 * 24)).toInt()
        }
        if (daysSinceBackup == -1 || daysSinceBackup >= 14) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (daysSinceBackup == -1) {
                        "No backup has been saved yet. Save one below to protect this year's data."
                    } else {
                        "Last backup was $daysSinceBackup days ago. Consider saving a new one."
                    },
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.exportBackupJson { json ->
                        pendingExportJson = json
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        saveFileLauncher.launch("classroll_backup_$stamp.json")
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Backup to File")
            }

            OutlinedButton(
                onClick = { openFileLauncher.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Load Backup from File")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = {
                    viewModel.exportBackupJson { json ->
                        backupJsonText = json
                        showExportBackupDialog = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy as Text Instead")
            }

            TextButton(
                onClick = { showRestoreBackupDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Paste as Text Instead")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // NEW: Daily Reminder Section
        Text("Daily Reminder", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Get a notification if attendance hasn't been taken yet by this time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enabled")
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setReminderEnabled(true)
                        }
                    } else {
                        viewModel.setReminderEnabled(false)
                    }
                }
            )
        }

        if (reminderEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            val timeOptions = listOf("07:00", "08:00", "09:00", "10:00", "12:00")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeOptions.forEach { t ->
                    val label = SimpleDateFormat("h:mm a", Locale.US).format(
                        SimpleDateFormat("HH:mm", Locale.US).parse(t) ?: Date()
                    )
                    FilterChip(
                        selected = reminderTime == t,
                        onClick = { viewModel.setReminderTime(t) },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Danger Zone Section
        Text("Data & Database Management", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showClearAttendanceDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
        ) {
            Text("Clear Attendance Records ($currentYear)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showClearStudentsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
        ) {
            Text("Clear Student Roster ($currentYear)")
        }
    }
}
