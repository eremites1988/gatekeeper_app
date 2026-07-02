package com.gatekeeper.app.data

import com.gatekeeper.app.data.db.BlockedApp
import com.gatekeeper.app.data.db.BlockedAppDao
import com.gatekeeper.app.data.db.GateConfig
import com.gatekeeper.app.data.db.GateConfigDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blacklist + gate-config access. Keeps an in-memory [blockedPackages] set so
 * the AccessibilityService can answer "is this package gated?" without a DB
 * round-trip on every window event (R-2.3 latency budget).
 */
@Singleton
class GateRepository @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
    private val gateConfigDao: GateConfigDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultConfigMutex = Mutex()

    val blockedApps: StateFlow<List<BlockedApp>> = blockedAppDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Enabled gated package names — hot cache for the detection hot path. */
    val blockedPackages: StateFlow<Set<String>> = blockedAppDao.observeAll()
        .map { apps -> apps.filter { it.enabled }.map { it.packageName }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isBlocked(packageName: String): Boolean = packageName in blockedPackages.value

    suspend fun setBlocked(packageName: String, label: String, blocked: Boolean) {
        if (blocked) {
            val existing = blockedAppDao.get(packageName)
            blockedAppDao.upsert(
                existing?.copy(enabled = true, label = label)
                    ?: BlockedApp(packageName = packageName, label = label)
            )
        } else {
            val existing = blockedAppDao.get(packageName) ?: return
            existing.configOverrideId?.let { gateConfigDao.delete(it) }
            blockedAppDao.delete(packageName)
        }
    }

    /** Global defaults row, created lazily on first use. */
    suspend fun globalConfig(): GateConfig = defaultConfigMutex.withLock {
        gateConfigDao.getGlobalDefault() ?: run {
            val id = gateConfigDao.insert(GateConfig(isGlobalDefault = true))
            gateConfigDao.get(id)!!
        }
    }

    suspend fun updateConfig(config: GateConfig) = gateConfigDao.update(config)

    /** Effective config for a package: per-app override or global default (R-3.3). */
    suspend fun effectiveConfig(packageName: String): GateConfig {
        val app = blockedAppDao.get(packageName)
        val overrideId = app?.configOverrideId
        return (overrideId?.let { gateConfigDao.get(it) }) ?: globalConfig()
    }

    /** Creates (or returns) a per-app override config seeded from the global default. */
    suspend fun ensureOverrideConfig(packageName: String): GateConfig? {
        val app = blockedAppDao.get(packageName) ?: return null
        app.configOverrideId?.let { gateConfigDao.get(it)?.let { cfg -> return cfg } }
        val seeded = globalConfig().copy(id = 0, isGlobalDefault = false)
        val id = gateConfigDao.insert(seeded)
        blockedAppDao.upsert(app.copy(configOverrideId = id))
        return gateConfigDao.get(id)
    }

    /** Removes a per-app override so the app inherits the global default again. */
    suspend fun clearOverrideConfig(packageName: String) {
        val app = blockedAppDao.get(packageName) ?: return
        app.configOverrideId?.let { gateConfigDao.delete(it) }
        blockedAppDao.upsert(app.copy(configOverrideId = null))
    }

    suspend fun getApp(packageName: String): BlockedApp? = blockedAppDao.get(packageName)
}
