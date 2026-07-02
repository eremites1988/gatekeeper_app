package com.gatekeeper.app.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.gatekeeper.app.data.db.GateConfig

/**
 * Shared editor for gate thresholds (Section 4) — used for the global default
 * (Settings) and per-app overrides. All amounts are fully user-editable
 * (R-4.2/R-4.3), plus optional daily cap (R-4.4) and cooldown (R-4.5).
 */
@Composable
fun GateConfigEditor(
    config: GateConfig,
    onChange: (GateConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Allowed gate types", style = MaterialTheme.typography.titleSmall)
        ToggleRow("Tasks", config.allowTasks) { onChange(config.copy(allowTasks = it)) }
        ToggleRow("Exercises", config.allowExercises) { onChange(config.copy(allowExercises = it)) }
        ToggleRow("Reading", config.allowReading) { onChange(config.copy(allowReading = it)) }
        if (!config.allowTasks && !config.allowExercises && !config.allowReading) {
            Text(
                "At least one gate type should stay enabled, or the gate can never be passed.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.padding(6.dp))
        Text("Required amounts", style = MaterialTheme.typography.titleSmall)
        NumberRow("Tasks to complete", config.tasksRequired, min = 1) {
            onChange(config.copy(tasksRequired = it))
        }
        NumberRow("Exercises to complete", config.exercisesRequired, min = 1) {
            onChange(config.copy(exercisesRequired = it))
        }
        NumberRow("Pages to read", config.pagesRequired, min = 1) {
            onChange(config.copy(pagesRequired = it))
        }

        Spacer(Modifier.padding(6.dp))
        Text("Access session", style = MaterialTheme.typography.titleSmall)
        NumberRow("Session length (minutes)", config.sessionDurationMinutes, min = 1) {
            onChange(config.copy(sessionDurationMinutes = it))
        }
        NumberRow("Daily session cap (0 = unlimited)", config.dailyCap, min = 0) {
            onChange(config.copy(dailyCap = it))
        }
        NumberRow("Cooldown between sessions (minutes, 0 = none)", config.cooldownMinutes, min = 0) {
            onChange(config.copy(cooldownMinutes = it))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NumberRow(label: String, value: Int, min: Int, onValue: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = if (value == 0 && min == 0) "0" else value.toString(),
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.take(4).toIntOrNull() ?: min
                onValue(parsed.coerceAtLeast(min))
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
