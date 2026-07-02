package com.gatekeeper.app.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.data.SettingsRepository
import com.gatekeeper.app.data.db.Book
import com.gatekeeper.app.data.db.BookDao
import com.gatekeeper.app.data.db.CompletionLog
import com.gatekeeper.app.data.db.CompletionLogDao
import com.gatekeeper.app.data.db.Exercise
import com.gatekeeper.app.data.db.ExerciseDao
import com.gatekeeper.app.data.db.GateConfig
import com.gatekeeper.app.data.db.GateType
import com.gatekeeper.app.data.db.LogType
import com.gatekeeper.app.data.db.Task
import com.gatekeeper.app.data.db.TaskDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GateUiState {
    data object Loading : GateUiState

    /** Daily cap reached or cooldown still running (R-4.4/R-4.5). */
    data class Blocked(val reason: String) : GateUiState

    /** R-4.1a: the user picks which gate type to do right now. */
    data class ChooseType(val types: List<GateType>) : GateUiState

    data class TaskGate(val required: Int, val completed: Int) : GateUiState
    data class ExerciseGate(val required: Int, val completed: Int) : GateUiState
    data class ReadingGate(val required: Int, val pagesRead: Int) : GateUiState

    /** Gate passed — session granted; the activity relaunches the target app. */
    data object Passed : GateUiState
}

@HiltViewModel
class GateViewModel @Inject constructor(
    private val gateRepository: GateRepository,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val taskDao: TaskDao,
    private val exerciseDao: ExerciseDao,
    bookDao: BookDao,
    private val logDao: CompletionLogDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GateUiState>(GateUiState.Loading)
    val uiState: StateFlow<GateUiState> = _uiState.asStateFlow()

    var packageName: String = ""
        private set
    var appLabel: String = ""
        private set

    private var config: GateConfig? = null
    private var tasksCompleted = 0
    private var exercisesCompleted = 0
    private var pagesRead = 0

    val incompleteTasks: StateFlow<List<Task>> = taskDao.observeIncomplete()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<Exercise>> = exerciseDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<Book>> = bookDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exerciseFrictionSeconds: StateFlow<Int> = MutableStateFlow(0).also { flow ->
        viewModelScope.launch {
            flow.value = settingsRepository.settings.first().exerciseFrictionSeconds
        }
    }

    fun start(pkg: String, label: String) {
        if (pkg == packageName && _uiState.value != GateUiState.Loading) return
        packageName = pkg
        appLabel = label
        tasksCompleted = 0
        exercisesCompleted = 0
        pagesRead = 0
        _uiState.value = GateUiState.Loading

        viewModelScope.launch {
            // The app may have been unblocked or unlocked since the event fired.
            if (sessionManager.isUnlocked(pkg)) {
                _uiState.value = GateUiState.Passed
                return@launch
            }
            val cfg = gateRepository.effectiveConfig(pkg)
            config = cfg

            if (cfg.dailyCap > 0 && sessionManager.sessionsToday(pkg) >= cfg.dailyCap) {
                _uiState.value = GateUiState.Blocked(
                    "Daily limit reached: you've already used $appLabel ${cfg.dailyCap} " +
                        "time(s) today. It unlocks again tomorrow."
                )
                return@launch
            }
            if (cfg.cooldownMinutes > 0) {
                val since = sessionManager.millisSinceLastGrant(pkg)
                val cooldownMs = cfg.cooldownMinutes * 60_000L
                if (since != null && since < cooldownMs) {
                    val remaining = ((cooldownMs - since) / 60_000L) + 1
                    _uiState.value = GateUiState.Blocked(
                        "Cooldown active: try again in about $remaining minute(s)."
                    )
                    return@launch
                }
            }

            val types = cfg.allowedTypes()
            when {
                types.isEmpty() -> _uiState.value = GateUiState.Blocked(
                    "No gate types are enabled for this app. Enable one in Gatekeeper settings."
                )
                types.size == 1 -> chooseType(types.single())
                else -> _uiState.value = GateUiState.ChooseType(types)
            }
        }
    }

    fun chooseType(type: GateType) {
        val cfg = config ?: return
        _uiState.value = when (type) {
            GateType.TASKS -> GateUiState.TaskGate(cfg.tasksRequired, tasksCompleted)
            GateType.EXERCISES -> GateUiState.ExerciseGate(cfg.exercisesRequired, exercisesCompleted)
            GateType.READING -> GateUiState.ReadingGate(cfg.pagesRequired, pagesRead)
        }
    }

    /** R-5.5 fallback: other enabled gate types the user can switch to. */
    fun otherTypes(current: GateType): List<GateType> =
        config?.allowedTypes()?.filter { it != current } ?: emptyList()

    fun backToChooser() {
        val types = config?.allowedTypes() ?: return
        _uiState.value =
            if (types.size > 1) GateUiState.ChooseType(types) else GateUiState.Loading
    }

    /** Task gate: mark a task done; archived with timestamp, never deleted (R-5.3). */
    fun completeTask(task: Task) {
        val cfg = config ?: return
        viewModelScope.launch {
            if (task.isDone) return@launch
            taskDao.update(task.copy(isDone = true, completedAt = System.currentTimeMillis()))
            logDao.insert(CompletionLog(type = LogType.TASK, refId = task.id, detail = task.title))
            tasksCompleted += 1
            if (tasksCompleted >= cfg.tasksRequired) pass()
            else _uiState.value = GateUiState.TaskGate(cfg.tasksRequired, tasksCompleted)
        }
    }

    fun quickAddTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskDao.insert(Task(title = title.trim())) }
    }

    /** Exercise gate: log a completed exercise (R-6.4). */
    fun completeExercise(exercise: Exercise) {
        val cfg = config ?: return
        viewModelScope.launch {
            logDao.insert(
                CompletionLog(
                    type = LogType.EXERCISE,
                    refId = exercise.id,
                    detail = "${exercise.name} (${exercise.targetAmount} ${exercise.unit.name.lowercase()})",
                )
            )
            exercisesCompleted += 1
            if (exercisesCompleted >= cfg.exercisesRequired) pass()
            else _uiState.value = GateUiState.ExerciseGate(cfg.exercisesRequired, exercisesCompleted)
        }
    }

    /** Reading gate: pages from a finished reader session (may be partial, R-7.9). */
    fun onReadingResult(pagesReadInSession: Int, passed: Boolean) {
        val cfg = config ?: return
        pagesRead += pagesReadInSession
        if (passed || pagesRead >= cfg.pagesRequired) {
            viewModelScope.launch { pass() }
        } else {
            _uiState.value = GateUiState.ReadingGate(cfg.pagesRequired, pagesRead)
        }
    }

    fun pagesStillNeeded(): Int {
        val cfg = config ?: return 0
        return (cfg.pagesRequired - pagesRead).coerceAtLeast(0)
    }

    fun pagesAlreadyRead(): Int = pagesRead

    private suspend fun pass() {
        val cfg = config ?: return
        sessionManager.grant(packageName, cfg.sessionDurationMinutes)
        _uiState.value = GateUiState.Passed
    }
}
