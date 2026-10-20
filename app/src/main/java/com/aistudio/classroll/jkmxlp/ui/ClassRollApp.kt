package com.aistudio.classroll.jkmxlp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.classroll.jkmxlp.data.AuthManager
import com.aistudio.classroll.jkmxlp.ui.screens.HomeScreen
import com.aistudio.classroll.jkmxlp.ui.screens.ImportStudentsScreen
import com.aistudio.classroll.jkmxlp.ui.screens.RegisterScreen
import com.aistudio.classroll.jkmxlp.ui.screens.Routes
import com.aistudio.classroll.jkmxlp.ui.screens.SettingsScreen
import com.aistudio.classroll.jkmxlp.ui.screens.SummaryDashboardScreen

@Composable
fun ClassRollApp(
    viewModel: ClassRollViewModel,
    authManager: AuthManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.Home

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                NavigationBarItem(
                    selected = currentRoute == Routes.Home,
                    onClick = {
                        navController.navigate(Routes.Home) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.Register,
                    onClick = {
                        navController.navigate(Routes.Register) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = "Register") },
                    label = { Text("Register") },
                    modifier = Modifier.testTag("nav_register")
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.Summary,
                    onClick = {
                        navController.navigate(Routes.Summary) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Summary") },
                    label = { Text("Summary") },
                    modifier = Modifier.testTag("nav_summary")
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.ImportStudents,
                    onClick = {
                        navController.navigate(Routes.ImportStudents) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Import") },
                    label = { Text("Import") },
                    modifier = Modifier.testTag("nav_import")
                )

                NavigationBarItem(
                    selected = currentRoute == Routes.Settings,
                    onClick = {
                        navController.navigate(Routes.Settings) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.Home) {
                HomeScreen(viewModel = viewModel)
            }
            composable(Routes.Register) {
                RegisterScreen(viewModel = viewModel)
            }
            composable(Routes.Summary) {
                SummaryDashboardScreen(viewModel = viewModel)
            }
            composable(Routes.ImportStudents) {
                ImportStudentsScreen(viewModel = viewModel)
            }
            composable(Routes.Settings) {
                SettingsScreen(viewModel = viewModel, authManager = authManager)
            }
        }
    }
}
