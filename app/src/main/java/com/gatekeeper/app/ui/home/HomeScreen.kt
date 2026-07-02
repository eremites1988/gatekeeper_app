package com.gatekeeper.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatekeeper.app.util.Permissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gatekeeper") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // R-9.4: the app is non-functional without the accessibility service.
            if (!state.accessibilityEnabled) {
                item {
                    Card(
                        onClick = {
                            context.startActivity(Permissions.accessibilitySettingsIntent())
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Spacer(Modifier.padding(6.dp))
                            Column {
                                Text(
                                    "Accessibility service is off",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "Gatekeeper can't detect apps without it. Tap to re-enable.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Today", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Tasks", state.tasksToday, Modifier.weight(1f))
                    StatCard("Exercises", state.exercisesToday, Modifier.weight(1f))
                    StatCard("Pages", state.pagesToday, Modifier.weight(1f))
                    StatCard("Unlocks", state.sessionsToday, Modifier.weight(1f))
                }
            }
            item {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Tasks", state.tasksWeek, Modifier.weight(1f))
                    StatCard("Exercises", state.exercisesWeek, Modifier.weight(1f))
                    StatCard("Pages", state.pagesWeek, Modifier.weight(1f))
                    StatCard("Unlocks", state.sessionsWeek, Modifier.weight(1f))
                }
            }

            if (state.activeGrants.isNotEmpty()) {
                item { Text("Active sessions", style = MaterialTheme.typography.titleMedium) }
                items(state.activeGrants, key = { it.packageName }) { grant ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(grant.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Expires at ${formatTime(grant.expiresAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            if (state.recentActivity.isNotEmpty()) {
                item { Text("Recent activity", style = MaterialTheme.typography.titleMedium) }
                items(state.recentActivity) { entry ->
                    Text(
                        entry,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(
            Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
