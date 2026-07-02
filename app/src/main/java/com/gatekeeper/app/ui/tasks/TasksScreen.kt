package com.gatekeeper.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.db.CompletionLog
import com.gatekeeper.app.data.db.CompletionLogDao
import com.gatekeeper.app.data.db.LogType
import com.gatekeeper.app.data.db.Task
import com.gatekeeper.app.data.db.TaskDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** To-do CRUD (Section 5). Completing here counts in history like gate completions. */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: CompletionLogDao,
) : ViewModel() {

    val incomplete = taskDao.observeIncomplete()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completed = taskDao.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(task: Task) {
        viewModelScope.launch {
            if (task.id == 0L) taskDao.insert(task) else taskDao.update(task)
        }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch {
            taskDao.update(
                task.copy(
                    isDone = done,
                    completedAt = if (done) System.currentTimeMillis() else null,
                )
            )
            if (done) {
                logDao.insert(CompletionLog(type = LogType.TASK, refId = task.id, detail = task.title))
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch { taskDao.delete(task) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val incomplete by viewModel.incomplete.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tasks") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("To do (${incomplete.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Done (${completed.size})") })
            }
            val list = if (tab == 0) incomplete else completed
            if (list.isEmpty()) {
                Text(
                    if (tab == 0) "No open tasks. Add some so the task gate stays passable."
                    else "Nothing completed yet.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn {
                items(list, key = { it.id }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { viewModel.setDone(task, it) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title + if (task.isRecurring) "  ↻" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                            )
                            if (!task.notes.isNullOrBlank()) {
                                Text(task.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                        if (task.priority > 0) {
                            Text(
                                listOf("", "!", "!!", "!!!")[task.priority.coerceIn(0, 3)],
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        IconButton(onClick = { editing = task; showEditor = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.delete(task) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            task = editing,
            onDismiss = { showEditor = false },
            onSave = { viewModel.save(it); showEditor = false },
        )
    }
}

@Composable
private fun TaskEditorDialog(task: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var notes by remember { mutableStateOf(task?.notes ?: "") }
    var priority by remember { mutableIntStateOf(task?.priority ?: 0) }
    var recurring by remember { mutableStateOf(task?.isRecurring ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "New task" else "Edit task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Priority:", Modifier.padding(end = 8.dp))
                    listOf("None", "!", "!!", "!!!").forEachIndexed { index, label ->
                        TextButton(onClick = { priority = index }) {
                            Text(
                                label,
                                color = if (priority == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                    Text("Repeats daily (resets every midnight)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            (task ?: Task(title = "")).copy(
                                title = title.trim(),
                                notes = notes.ifBlank { null },
                                priority = priority,
                                isRecurring = recurring,
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
