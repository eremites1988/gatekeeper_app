package com.gatekeeper.app.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.db.GateConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppConfigUiState(
    val label: String = "",
    val hasOverride: Boolean = false,
    val config: GateConfig? = null,
)

/** Per-app gate override: inherit the global config or customize it (R-3.3). */
@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val gateRepository: GateRepository,
) : ViewModel() {

    val uiState = MutableStateFlow(AppConfigUiState())
    private var packageName = ""

    fun load(pkg: String) {
        packageName = pkg
        viewModelScope.launch {
            val app = gateRepository.getApp(pkg)
            uiState.value = AppConfigUiState(
                label = app?.label ?: pkg,
                hasOverride = app?.configOverrideId != null,
                config = gateRepository.effectiveConfig(pkg),
            )
        }
    }

    fun setOverrideEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val cfg = gateRepository.ensureOverrideConfig(packageName)
                uiState.value = uiState.value.copy(hasOverride = true, config = cfg)
            } else {
                gateRepository.clearOverrideConfig(packageName)
                uiState.value = uiState.value.copy(
                    hasOverride = false,
                    config = gateRepository.effectiveConfig(packageName),
                )
            }
        }
    }

    fun updateConfig(config: GateConfig) {
        if (!uiState.value.hasOverride) return
        uiState.value = uiState.value.copy(config = config)
        viewModelScope.launch { gateRepository.updateConfig(config) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(packageName) { viewModel.load(packageName) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.label) },
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
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Custom gate settings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.hasOverride) "This app uses its own gate settings."
                        else "This app inherits the global default gate settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.hasOverride,
                    onCheckedChange = viewModel::setOverrideEnabled,
                )
            }

            state.config?.let { config ->
                if (state.hasOverride) {
                    GateConfigEditor(config = config, onChange = viewModel::updateConfig)
                } else {
                    Text(
                        "Current effective settings — session: ${config.sessionDurationMinutes} min, " +
                            "tasks: ${config.tasksRequired}, exercises: ${config.exercisesRequired}, " +
                            "pages: ${config.pagesRequired}." +
                            (if (config.dailyCap > 0) " Daily cap: ${config.dailyCap}." else "") +
                            (if (config.cooldownMinutes > 0) " Cooldown: ${config.cooldownMinutes} min." else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
