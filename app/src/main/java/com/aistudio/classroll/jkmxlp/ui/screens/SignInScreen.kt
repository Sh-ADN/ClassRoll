package com.aistudio.classroll.jkmxlp.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.classroll.jkmxlp.data.AuthManager
import com.google.firebase.auth.FirebaseUser

@Composable
fun SignInScreen(
    authManager: AuthManager,
    onAuthSuccess: (FirebaseUser) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(Unit) {
        authManager.attemptAutoSignIn(
            scope = coroutineScope,
            onSuccess = onAuthSuccess,
            onUnauthenticated = { /* user will click button */ }
        )
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "ClassRoll App Icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome to ClassRoll",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in to securely backup, sync, and access your attendance registers anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureRow(icon = Icons.Default.Check, title = "Cloud Sync", subtitle = "Never lose student records or daily attendance")
                        FeatureRow(icon = Icons.Default.Refresh, title = "Multi-Device", subtitle = "Use on your phone or classroom tablet seamlessly")
                        FeatureRow(icon = Icons.Default.Done, title = "Private & Secure", subtitle = "Your roster is encrypted and tied to your Google account")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                errorMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { showDiagnosticsDialog = true },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("View Signing SHA-1 & Fix", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        val activity = context as? Activity
                        if (activity == null) {
                            isLoading = false
                            errorMessage = "Activity not found"
                            return@Button
                        }
                        authManager.signInWithGoogle(
                            activity = activity,
                            scope = coroutineScope,
                            onSuccess = { user ->
                                isLoading = false
                                onAuthSuccess(user)
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                            },
                            onCancelled = {
                                isLoading = false
                                errorMessage = "Sign-in dismissed or interrupted. If you picked your account, the app's signing key SHA-1 fingerprint needs to be added in Firebase Console."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_in_google_button"),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountBox,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { showDiagnosticsDialog = true },
                    modifier = Modifier.testTag("diagnostics_button")
                ) {
                    Text(
                        text = "Trouble signing in? View SHA-1 Certificate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        val currentSha1 = com.aistudio.classroll.jkmxlp.util.SigningUtils.getSha1Fingerprint(context)
        val currentSha256 = com.aistudio.classroll.jkmxlp.util.SigningUtils.getSha256Fingerprint(context)
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Google Sign-In Setup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Google Sign-In requires your signing key's SHA-1 fingerprint to be registered in your Firebase Console project.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Divider()
                    Text(
                        text = "Package Name:\n${context.packageName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Installed APK SHA-1:\n$currentSha1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Installed APK SHA-256:\n$currentSha256",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Divider()
                    Text(
                        text = "How to fix in 1 minute:\n1. Copy the SHA-1 below.\n2. Open Firebase Console -> Project Settings -> General -> Your Android App.\n3. Click 'Add fingerprint' and paste it.\n4. Sign-in works immediately without rebuilding!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentSha1))
                        android.widget.Toast.makeText(context, "SHA-1 copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy SHA-1")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
