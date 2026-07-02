package com.gatekeeper.app.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onConfigureApp: (String) -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val strictMode by viewModel.strictMode.collectAsStateWithLifecycle()
    var confirmUngate by remember { mutableStateOf<InstalledApp?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gated apps") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let {
                            Image(
                                painter = rememberDrawablePainter(it),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (app.gated) {
                            IconButton(onClick = { onConfigureApp(app.packageName) }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Configure gate")
                            }
                        }
                        Switch(
                            checked = app.gated,
                            onCheckedChange = { gated ->
                                if (!gated && strictMode) {
                                    // R-11.3: strict mode adds friction before un-gating.
                                    confirmUngate = app
                                } else {
                                    viewModel.setGated(app, gated)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    confirmUngate?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmUngate = null },
            title = { Text("Remove the gate on ${app.label}?") },
            text = {
                Text(
                    "Strict mode is on. Are you sure you want to make ${app.label} " +
                        "freely accessible again? Your future self is watching."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGated(app, false)
                    confirmUngate = null
                }) { Text("Remove gate") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUngate = null }) { Text("Keep it gated") }
            },
        )
    }
}
