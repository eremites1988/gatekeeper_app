package com.gatekeeper.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.width
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.AppSettings
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SettingsRepository
import com.gatekeeper.app.data.db.GateConfig
import com.gatekeeper.app.ui.apps.GateConfigEditor
import com.gatekeeper.app.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Global settings (Section 11) — default gate config, friction, strict modes. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gateRepository: GateRepository,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val globalConfig = MutableStateFlow<GateConfig?>(null)

    init {
        viewModelScope.launch { globalConfig.value = gateRepository.globalConfig() }
    }

    fun updateGlobalConfig(config: GateConfig) {
        globalConfig.value = config
        viewModelScope.launch { gateRepository.updateConfig(config) }
    }

    fun setGrantsSurviveReboot(v: Boolean) = viewModelScope.launch {
        settingsRepository.setGrantsSurviveReboot(v)
    }

    fun setExerciseFriction(seconds: Int) = viewModelScope.launch {
        settingsRepository.setExerciseFrictionSeconds(seconds)
    }

    fun setReadingStrict(v: Boolean) = viewModelScope.launch {
        settingsRepository.setReadingStrictMode(v)
    }

    fun setReadingDwell(seconds: Int) = viewModelScope.launch {
        settingsRepository.setReadingDwellSeconds(seconds)
    }

    fun setStrictMode(v: Boolean) = viewModelScope.launch {
        settingsRepository.setStrictMode(v)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Default gate settings", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used for every gated app that doesn't have its own override.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            globalConfig?.let { config ->
                GateConfigEditor(config = config, onChange = viewModel::updateGlobalConfig)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Friction & strictness", style = MaterialTheme.typography.titleMedium)
            Text(
                "All gates are honor-system by default. These optional switches add friction.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            SwitchRow(
                title = "Exercise confirmation timer",
                subtitle = "Wait through a countdown before an exercise can be marked done.",
                checked = settings.exerciseFrictionSeconds > 0,
                onChecked = { viewModel.setExerciseFriction(if (it) 30 else 0) },
            )
            if (settings.exerciseFrictionSeconds > 0) {
                NumberRow(
                    "Timer length (seconds)",
                    settings.exerciseFrictionSeconds,
                ) { viewModel.setExerciseFriction(it) }
            }

            SwitchRow(
                title = "Strict reading mode",
                subtitle = "Require a minimum time per page and only count never-before-read pages.",
                checked = settings.readingStrictMode,
                onChecked = viewModel::setReadingStrict,
            )
            if (settings.readingStrictMode) {
                NumberRow(
                    "Minimum seconds per page",
                    settings.readingDwellSeconds,
                ) { viewModel.setReadingDwell(it) }
            }

            SwitchRow(
                title = "Strict mode",
                subtitle = "Ask for confirmation before a gate can be removed from an app. " +
                    "(Android can't fully stop a determined user from force-stopping or " +
                    "uninstalling Gatekeeper — this only adds friction.)",
                checked = settings.strictMode,
                onChecked = viewModel::setStrictMode,
            )

            SwitchRow(
                title = "Keep sessions across reboot",
                subtitle = "If off, all active unlock sessions expire when the device restarts.",
                checked = settings.grantsSurviveReboot,
                onChecked = viewModel::setGrantsSurviveReboot,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PermissionStatusRow(
                "Accessibility service (primary detection)",
                Permissions.isAccessibilityServiceEnabled(context),
            ) { context.startActivity(Permissions.accessibilitySettingsIntent()) }
            PermissionStatusRow(
                "Battery optimization exemption",
                Permissions.isIgnoringBatteryOptimizations(context),
            ) { context.startActivity(Permissions.batteryOptimizationIntent(context)) }
            PermissionStatusRow(
                "Display over other apps (compatibility mode)",
                Permissions.canDrawOverlays(context),
            ) { context.startActivity(Permissions.overlaySettingsIntent(context)) }
            PermissionStatusRow(
                "Usage access (compatibility mode)",
                Permissions.hasUsageStatsAccess(context),
            ) { context.startActivity(Permissions.usageAccessSettingsIntent()) }
            Text(
                "If the accessibility service is blocked on your device (e.g. by a work " +
                    "profile policy), grant the two compatibility-mode permissions instead — " +
                    "Gatekeeper switches between detection modes automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Privacy: Gatekeeper works entirely on this device. It has no internet " +
                    "access, no analytics, and none of your data ever leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text ->
                text.filter { it.isDigit() }.take(3).toIntOrNull()?.let(onValue)
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = onOpen) { Text("Open settings") }
    }
}
