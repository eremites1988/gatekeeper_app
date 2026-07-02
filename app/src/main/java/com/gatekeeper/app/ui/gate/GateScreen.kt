package com.gatekeeper.app.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gatekeeper.app.data.db.Exercise
import com.gatekeeper.app.data.db.GateType
import com.gatekeeper.app.data.db.Task
import kotlinx.coroutines.delay

@Composable
fun GateScreen(
    viewModel: GateViewModel,
    onOpenBook: (Long) -> Unit,
    onPassed: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    if (state is GateUiState.Passed) {
        LaunchedEffect(Unit) { onPassed() }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
                .padding(20.dp),
        ) {
            GateHeader(appLabel = viewModel.appLabel, onGiveUp = onGiveUp)
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is GateUiState.Loading, GateUiState.Passed -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                is GateUiState.Blocked -> BlockedContent(s.reason, onGiveUp)

                is GateUiState.ChooseType -> TypeChooser(
                    types = s.types,
                    onChoose = viewModel::chooseType,
                )

                is GateUiState.TaskGate -> TaskGateContent(viewModel, s)
                is GateUiState.ExerciseGate -> ExerciseGateContent(viewModel, s)
                is GateUiState.ReadingGate -> ReadingGateContent(viewModel, s, onOpenBook)
            }
        }
    }
}

@Composable
private fun GateHeader(appLabel: String, onGiveUp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Gatekeeper", style = MaterialTheme.typography.labelMedium)
            Text(
                "$appLabel is gated",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        TextButton(onClick = onGiveUp) { Text("Never mind") }
    }
}

@Composable
private fun BlockedContent(reason: String, onGiveUp: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(reason, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGiveUp) { Text("Go home") }
    }
}

@Composable
private fun TypeChooser(types: List<GateType>, onChoose: (GateType) -> Unit) {
    Text(
        "Earn your way in. Pick a gate:",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(16.dp))
    types.forEach { type ->
        val (icon, title, subtitle) = when (type) {
            GateType.TASKS -> Triple(
                Icons.Filled.Checklist, "Finish tasks", "Check off items from your to-do list"
            )
            GateType.EXERCISES -> Triple(
                Icons.Filled.FitnessCenter, "Do exercises", "Complete exercises from your library"
            )
            GateType.READING -> Triple(
                Icons.AutoMirrored.Filled.MenuBook, "Read pages", "Read in the built-in book reader"
            )
        }
        ElevatedCard(
            onClick = { onChoose(type) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun GateProgress(label: String, completed: Int, required: Int) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                if (required == 0) 1f else (completed.toFloat() / required).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text("$completed / $required", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FallbackRow(viewModel: GateViewModel, current: GateType) {
    val others = viewModel.otherTypes(current)
    if (others.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { type ->
                OutlinedButton(onClick = { viewModel.chooseType(type) }) {
                    Text(
                        when (type) {
                            GateType.TASKS -> "Switch to tasks"
                            GateType.EXERCISES -> "Switch to exercises"
                            GateType.READING -> "Switch to reading"
                        }
                    )
                }
            }
        }
    }
}

// ---------- Task gate (Section 5) ----------

@Composable
private fun TaskGateContent(viewModel: GateViewModel, state: GateUiState.TaskGate) {
    val tasks by viewModel.incompleteTasks.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    val remaining = state.required - state.completed

    GateProgress("Complete $remaining more task(s) to unlock", state.completed, state.required)
    Spacer(Modifier.height(16.dp))

    if (tasks.size < remaining) {
        // R-5.5: never a permanent lockout — offer quick-add and other gate types.
        Text(
            "You don't have enough open tasks (${tasks.size} left, $remaining needed). " +
                "Add a task below or switch to another gate type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        FallbackRow(viewModel, GateType.TASKS)
        Spacer(Modifier.height(8.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            label = { Text("Quick-add a task") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        IconButton(onClick = {
            viewModel.quickAddTask(newTitle)
            newTitle = ""
        }) { Icon(Icons.Filled.Add, contentDescription = "Add task") }
    }
    Spacer(Modifier.height(8.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(task = task, onDone = { viewModel.completeTask(task) })
        }
    }
}

@Composable
private fun TaskRow(task: Task, onDone: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                if (!task.notes.isNullOrBlank()) {
                    Text(task.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

// ---------- Exercise gate (Section 6) ----------

@Composable
private fun ExerciseGateContent(viewModel: GateViewModel, state: GateUiState.ExerciseGate) {
    val exercises by viewModel.exercises.collectAsState()
    val frictionSeconds by viewModel.exerciseFrictionSeconds.collectAsState()
    val remaining = state.required - state.completed

    GateProgress("Complete $remaining more exercise(s) to unlock", state.completed, state.required)
    Spacer(Modifier.height(16.dp))

    if (exercises.isEmpty()) {
        Text(
            "Your exercise library is empty. Add exercises in Gatekeeper, or switch gate type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        FallbackRow(viewModel, GateType.EXERCISES)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(exercises, key = { it.id }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                frictionSeconds = frictionSeconds,
                onDone = { viewModel.completeExercise(exercise) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, frictionSeconds: Int, onDone: () -> Unit) {
    // R-6.3: honor system by default; optional confirmation countdown for friction.
    var countdown by remember(exercise.id) { mutableIntStateOf(-1) }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${exercise.targetAmount} ${if (exercise.unit.name == "REPS") "reps" else "seconds"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!exercise.description.isNullOrBlank()) {
                    Text(exercise.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            when {
                frictionSeconds <= 0 -> Button(onClick = onDone) { Text("Done") }
                countdown < 0 -> Button(onClick = { countdown = frictionSeconds }) { Text("Start") }
                countdown > 0 -> OutlinedButton(onClick = {}, enabled = false) { Text("${countdown}s") }
                else -> Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

// ---------- Reading gate (Section 7) ----------

@Composable
private fun ReadingGateContent(
    viewModel: GateViewModel,
    state: GateUiState.ReadingGate,
    onOpenBook: (Long) -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val remaining = state.required - state.pagesRead

    GateProgress("Read $remaining more page(s) to unlock", state.pagesRead, state.required)
    Spacer(Modifier.height(16.dp))

    if (books.isEmpty()) {
        Text(
            "Your book library is empty. Import an EPUB in Gatekeeper, or switch gate type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        FallbackRow(viewModel, GateType.READING)
    } else {
        Text("Pick a book to read:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { it.id }) { book ->
                ElevatedCard(onClick = { onOpenBook(book.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (book.author != null) {
                                Text(book.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
