package com.aistudio.classroll.jkmxlp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.classroll.jkmxlp.ui.screens.*

@Composable
fun ClassRollApp(viewModel: ClassRollViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.Home

    val navigateTo = { route: String ->
        navController.navigate(route) {
            popUpTo(Routes.Home) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.Home,
                    onClick = { navigateTo(Routes.Home) },
                    icon = { Icon(Icons.Default.Done, "Take Attendance") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.Register,
                    onClick = { navigateTo(Routes.Register) },
                    icon = { Icon(Icons.Default.List, "Register") },
                    label = { Text("Register") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.Summary,
                    onClick = { navigateTo(Routes.Summary) },
                    icon = { Icon(Icons.Default.DateRange, "Summary") },
                    label = { Text("Summary") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.ImportStudents,
                    onClick = { navigateTo(Routes.ImportStudents) },
                    icon = { Icon(Icons.Default.Add, "Import") },
                    label = { Text("Import") }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.Settings,
                    onClick = { navigateTo(Routes.Settings) },
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Routes.Home, Modifier.padding(innerPadding)) {
            composable(Routes.Home) { HomeScreen(viewModel) }
            composable(Routes.Register) { RegisterScreen(viewModel) }
            composable(Routes.Summary) { SummaryDashboardScreen(viewModel) }
            composable(Routes.ImportStudents) { ImportStudentsScreen(viewModel) }
            composable(Routes.Settings) { SettingsScreen(viewModel) }
        }
    }
}
