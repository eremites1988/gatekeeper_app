package com.gatekeeper.app.ui.exercises

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.db.Exercise
import com.gatekeeper.app.data.db.ExerciseDao
import com.gatekeeper.app.data.db.ExerciseUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exercise library CRUD (Section 6). */
@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ViewModel() {

    val exercises = exerciseDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(exercise: Exercise) {
        viewModelScope.launch {
            if (exercise.id == 0L) exerciseDao.insert(exercise) else exerciseDao.update(exercise)
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch { exerciseDao.delete(exercise) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(viewModel: ExercisesViewModel = hiltViewModel()) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercises") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add exercise")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (exercises.isEmpty()) {
                Text(
                    "No exercises yet. Add a few (e.g. \"15 pushups\", \"60s plank\") so the " +
                        "exercise gate has something to offer.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn {
                items(exercises, key = { it.id }) { exercise ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${exercise.targetAmount} " +
                                    if (exercise.unit == ExerciseUnit.REPS) "reps" else "seconds",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!exercise.description.isNullOrBlank()) {
                                Text(
                                    exercise.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                )
                            }
                        }
                        IconButton(onClick = { editing = exercise; showEditor = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.delete(exercise) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ExerciseEditorDialog(
            exercise = editing,
            onDismiss = { showEditor = false },
            onSave = { viewModel.save(it); showEditor = false },
        )
    }
}

@Composable
private fun ExerciseEditorDialog(
    exercise: Exercise?,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit,
) {
    var name by remember { mutableStateOf(exercise?.name ?: "") }
    var amount by remember { mutableStateOf((exercise?.targetAmount ?: 10).toString()) }
    var unit by remember { mutableStateOf(exercise?.unit ?: ExerciseUnit.REPS) }
    var description by remember { mutableStateOf(exercise?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise == null) "New exercise" else "Edit exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Pushups)") },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Amount") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    FilterChip(
                        selected = unit == ExerciseUnit.REPS,
                        onClick = { unit = ExerciseUnit.REPS },
                        label = { Text("reps") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    FilterChip(
                        selected = unit == ExerciseUnit.SECONDS,
                        onClick = { unit = ExerciseUnit.SECONDS },
                        label = { Text("seconds") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Instructions (optional)") },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        (exercise ?: Exercise(name = "")).copy(
                            name = name.trim(),
                            targetAmount = amount.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            unit = unit,
                            description = description.ifBlank { null },
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
