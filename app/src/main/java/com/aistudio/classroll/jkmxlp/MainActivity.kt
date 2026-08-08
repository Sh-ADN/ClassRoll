package com.aistudio.classroll.jkmxlp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aistudio.classroll.jkmxlp.ui.ClassRollApp
import com.aistudio.classroll.jkmxlp.ui.ClassRollViewModel
import com.aistudio.classroll.jkmxlp.ui.theme.MyApplicationTheme

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: ClassRollViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.appTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                ClassRollApp(viewModel = viewModel)
            }
        }
    }
}
