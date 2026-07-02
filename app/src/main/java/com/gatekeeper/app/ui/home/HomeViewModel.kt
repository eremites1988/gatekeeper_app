package com.gatekeeper.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.data.db.CompletionLogDao
import com.gatekeeper.app.data.db.LogType
import com.gatekeeper.app.data.db.SessionGrantDao
import com.gatekeeper.app.util.DetectionMode
import com.gatekeeper.app.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ActiveGrantUi(val packageName: String, val label: String, val expiresAt: Long)

data class HomeUiState(
    val detectionMode: DetectionMode = DetectionMode.ACCESSIBILITY,
    val tasksToday: Int = 0,
    val exercisesToday: Int = 0,
    val pagesToday: Int = 0,
    val sessionsToday: Int = 0,
    val tasksWeek: Int = 0,
    val exercisesWeek: Int = 0,
    val pagesWeek: Int = 0,
    val sessionsWeek: Int = 0,
    val activeGrants: List<ActiveGrantUi> = emptyList(),
    val recentActivity: List<String> = emptyList(),
)

/** Simple stats screen fed by the completion logs (R-11.2). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logDao: CompletionLogDao,
    private val grantDao: SessionGrantDao,
    private val sessionManager: SessionManager,
    private val gateRepository: GateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val dayStart = SessionManager.startOfTodayMillis()
            val weekStart = dayStart - 6 * 24 * 3600_000L
            val labels = gateRepository.blockedApps.value.associate { it.packageName to it.label }

            val grants = sessionManager.activeGrants.value.values
                .filter { it.expiresAt > System.currentTimeMillis() }
                .map { ActiveGrantUi(it.packageName, labels[it.packageName] ?: it.packageName, it.expiresAt) }
                .sortedBy { it.expiresAt }

            val fmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            val recent = logDao.observeRecent(15).first().map { log ->
                val time = fmt.format(Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()))
                when (log.type) {
                    LogType.TASK -> "$time — completed task \"${log.detail}\""
                    LogType.EXERCISE -> "$time — did ${log.detail}"
                    LogType.READING -> "$time — read ${log.amount} pages of \"${log.detail}\""
                    LogType.GATE_SESSION -> "$time — unlocked ${labels[log.detail] ?: log.detail}"
                }
            }

            _uiState.value = HomeUiState(
                detectionMode = Permissions.detectionMode(context),
                tasksToday = logDao.sumSince(LogType.TASK, dayStart),
                exercisesToday = logDao.sumSince(LogType.EXERCISE, dayStart),
                pagesToday = logDao.sumSince(LogType.READING, dayStart),
                sessionsToday = grantDao.totalCountSince(dayStart),
                tasksWeek = logDao.sumSince(LogType.TASK, weekStart),
                exercisesWeek = logDao.sumSince(LogType.EXERCISE, weekStart),
                pagesWeek = logDao.sumSince(LogType.READING, weekStart),
                sessionsWeek = grantDao.totalCountSince(weekStart),
                activeGrants = grants,
                recentActivity = recent,
            )
        }
    }
}
