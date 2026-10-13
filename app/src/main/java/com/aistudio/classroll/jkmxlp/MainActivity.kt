package com.aistudio.classroll.jkmxlp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

            MyApplicationTheme(themeMode = themeMode) {
                if (currentUser == null) {
                    SignInScreen(
                        authManager = authManager,
                        onAuthSuccess = { /* authStateFlow will emit the signed-in user */ }
                    )
                } else {
                    ClassRollApp(
                        viewModel = viewModel,
                        authManager = authManager
                    )
                }
            }
        }
    }
}
