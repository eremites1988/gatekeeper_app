package com.gatekeeper.app.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val gated: Boolean,
)

/** Blacklist management (Section 3): all launchable apps, searchable, toggleable. */
@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateRepository: GateRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    private val installed = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val icons = MutableStateFlow<Map<String, Drawable?>>(emptyMap())

    val apps: StateFlow<List<InstalledApp>> = combine(
        installed, icons, gateRepository.blockedPackages, query,
    ) { installedApps, iconMap, blocked, q ->
        installedApps
            .filter { (_, label) -> q.isBlank() || label.contains(q, ignoreCase = true) }
            .map { (pkg, label) ->
                InstalledApp(pkg, label, iconMap[pkg], gated = pkg in blocked)
            }
            .sortedWith(compareByDescending<InstalledApp> { it.gated }.thenBy { it.label.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val strictMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.strictMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadInstalledApps()
    }

    /** R-3.1: launchable apps via PackageManager. Tolerates stale entries (R-3.4). */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = pm.queryIntentActivities(intent, 0)
                resolved
                    .mapNotNull { info ->
                        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                        if (pkg == context.packageName) return@mapNotNull null
                        val label = runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: pkg
                        pkg to label
                    }
                    .distinctBy { it.first }
            }
            installed.value = result
            // Icons loaded separately: they're the slow part.
            val iconMap = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                result.associate { (pkg, _) ->
                    pkg to runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                }
            }
            icons.value = iconMap
        }
    }

    fun setGated(app: InstalledApp, gated: Boolean) {
        viewModelScope.launch {
            gateRepository.setBlocked(app.packageName, app.label, gated)
        }
    }
}
