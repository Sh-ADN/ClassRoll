package com.aistudio.classroll.jkmxlp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.classroll.jkmxlp.data.AuthManager
import com.aistudio.classroll.jkmxlp.ui.ClassRollApp
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import com.aistudio.classroll.jkmxlp.ui.screens.SignInScreen
import com.aistudio.classroll.jkmxlp.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ClassRollViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authManager = AuthManager(this)

        setContent {
            val themeMode by viewModel.appTheme.collectAsStateWithLifecycle()
            val currentUser by authManager.authStateFlow.collectAsStateWithLifecycle(initialValue = authManager.currentUser)
            val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                if (currentUser != null || offlineMode == true) {
                    ClassRollApp(viewModel = viewModel, authManager = authManager)
                } else if (offlineMode == null) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    SignInScreen(
                        authManager = authManager,
                        onSignInSuccess = {},
                        onContinueOffline = {
                            viewModel.setOfflineMode(true)
                        }
                    )
                }
            }
        }
    }
}
