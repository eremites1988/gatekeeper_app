package com.gatekeeper.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gatekeeper.app.data.SettingsRepository
import com.gatekeeper.app.ui.apps.AppConfigScreen
import com.gatekeeper.app.ui.apps.AppsScreen
import com.gatekeeper.app.ui.exercises.ExercisesScreen
import com.gatekeeper.app.ui.home.HomeScreen
import com.gatekeeper.app.ui.library.LibraryScreen
import com.gatekeeper.app.ui.onboarding.OnboardingScreen
import com.gatekeeper.app.ui.settings.SettingsScreen
import com.gatekeeper.app.ui.tasks.TasksScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /** null while loading, then true/false. */
    val onboardingComplete = settingsRepository.settings
        .map { it.onboardingComplete as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun GatekeeperApp(shellViewModel: AppShellViewModel = hiltViewModel()) {
    val onboardingComplete by shellViewModel.onboardingComplete.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    if (onboardingComplete == null) return
    if (onboardingComplete == false) {
        OnboardingScreen()
        return
    }

    val items = listOf(
        NavItem("home", "Home", Icons.Filled.Home),
        NavItem("apps", "Apps", Icons.Filled.Apps),
        NavItem("tasks", "Tasks", Icons.Filled.Checklist),
        NavItem("exercises", "Exercises", Icons.Filled.FitnessCenter),
        NavItem("library", "Books", Icons.AutoMirrored.Filled.MenuBook),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(onOpenSettings = { navController.navigate("settings") })
            }
            composable("apps") {
                AppsScreen(onConfigureApp = { pkg -> navController.navigate("appConfig/$pkg") })
            }
            composable("appConfig/{pkg}") { entry ->
                AppConfigScreen(
                    packageName = entry.arguments?.getString("pkg") ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
            composable("tasks") { TasksScreen() }
            composable("exercises") { ExercisesScreen() }
            composable("library") { LibraryScreen() }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
