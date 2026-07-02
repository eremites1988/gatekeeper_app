package com.gatekeeper.app.data

import com.gatekeeper.app.data.db.CompletionLog
import com.gatekeeper.app.data.db.CompletionLogDao
import com.gatekeeper.app.data.db.LogType
import com.gatekeeper.app.data.db.SessionGrant
import com.gatekeeper.app.data.db.SessionGrantDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access-session grants (Section 8). Grants are persisted in Room (so they
 * survive process death) and mirrored into an in-memory map so the
 * AccessibilityService hot path never touches the DB.
 */
@Singleton
class SessionManager @Inject constructor(
    private val grantDao: SessionGrantDao,
    private val logDao: CompletionLogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeGrants = MutableStateFlow<Map<String, SessionGrant>>(emptyMap())
    val activeGrants: StateFlow<Map<String, SessionGrant>> = _activeGrants.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun reload() {
        val now = System.currentTimeMillis()
        _activeGrants.value = grantDao.active(now).associateBy { it.packageName }
    }

    /** Hot-path check used by the AccessibilityService (R-8.2). */
    fun isUnlocked(packageName: String): Boolean {
        val grant = _activeGrants.value[packageName] ?: return false
        if (grant.expiresAt <= System.currentTimeMillis()) {
            // Lazily drop the expired grant; next foreground event re-gates (R-8.3).
            _activeGrants.value = _activeGrants.value - packageName
            return false
        }
        return true
    }

    /** Called when a gate is passed (R-8.1). Unlocks only [packageName]. */
    suspend fun grant(packageName: String, durationMinutes: Int): SessionGrant {
        val now = System.currentTimeMillis()
        val grant = SessionGrant(
            packageName = packageName,
            grantedAt = now,
            expiresAt = now + durationMinutes * 60_000L,
        )
        val id = grantDao.insert(grant)
        val saved = grant.copy(id = id)
        _activeGrants.value = _activeGrants.value + (packageName to saved)
        logDao.insert(CompletionLog(type = LogType.GATE_SESSION, detail = packageName))
        return saved
    }

    suspend fun revoke(packageName: String) {
        grantDao.expireFor(packageName, System.currentTimeMillis())
        _activeGrants.value = _activeGrants.value - packageName
    }

    /** R-8.4: on reboot, expire everything unless the user opted to persist grants. */
    suspend fun onBoot(grantsSurviveReboot: Boolean) {
        if (!grantsSurviveReboot) grantDao.expireAll(System.currentTimeMillis())
        reload()
    }

    fun pruneExpired() {
        val now = System.currentTimeMillis()
        val pruned = _activeGrants.value.filterValues { it.expiresAt > now }
        if (pruned.size != _activeGrants.value.size) _activeGrants.value = pruned
    }

    /** Sessions granted today for [packageName] — daily-cap check (R-4.4). */
    suspend fun sessionsToday(packageName: String): Int =
        grantDao.countSince(packageName, startOfTodayMillis())

    /** Millis since the last grant for [packageName], or null — cooldown check (R-4.5). */
    suspend fun millisSinceLastGrant(packageName: String): Long? =
        grantDao.lastGrantedAt(packageName)?.let { System.currentTimeMillis() - it }

    companion object {
        fun startOfTodayMillis(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
