package com.gatekeeper.app.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.ui.gate.GateActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared gate-launch decision logic. Fed foreground-package events by either
 * detection path:
 *  - [GatekeeperAccessibilityService] (primary, event-driven, R-2.1), or
 *  - the UsageStats poller in [GatekeeperForegroundService] (compatibility
 *    fallback for devices where a work profile blocks third-party
 *    accessibility services, R-2.2).
 *
 * All checks on the hot path are in-memory reads (R-2.3 latency budget).
 */
@Singleton
class GateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateRepository: GateRepository,
    private val sessionManager: SessionManager,
) {
    /** Debounce so rapid window events for one package launch a single gate (R-2.6). */
    private var lastGatedPackage: String? = null
    private var lastGateLaunchAt: Long = 0

    fun onForegroundApp(packageName: String) {
        if (shouldIgnore(packageName)) return
        if (!gateRepository.isBlocked(packageName)) return
        if (sessionManager.isUnlocked(packageName)) return

        val now = System.currentTimeMillis()
        if (packageName == lastGatedPackage && now - lastGateLaunchAt < GATE_DEBOUNCE_MS) return
        lastGatedPackage = packageName
        lastGateLaunchAt = now

        // Launched either from an accessibility context (exempt from
        // background-launch restrictions) or while holding SYSTEM_ALERT_WINDOW
        // (also exempt) — see R-2.4.
        runCatching { context.startActivity(GateActivity.intent(context, packageName)) }
    }

    /** R-2.5: never gate ourselves, the launcher, System UI, the IME, or call screens. */
    private fun shouldIgnore(pkg: String): Boolean {
        if (pkg == context.packageName || pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg == defaultLauncherPackage()) return true
        if (pkg == defaultImePackage()) return true
        if (pkg == defaultDialerPackage()) return true
        return false
    }

    // System-service lookups are cheap but window/usage events arrive in
    // bursts, so cache them briefly.
    private var cachedLauncher: Pair<String?, Long> = null to 0L
    private fun defaultLauncherPackage(): String? {
        val (value, at) = cachedLauncher
        if (System.currentTimeMillis() - at < CACHE_MS) return value
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        cachedLauncher = resolved to System.currentTimeMillis()
        return resolved
    }

    private fun defaultImePackage(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private var cachedDialer: Pair<String?, Long> = null to 0L
    private fun defaultDialerPackage(): String? {
        val (value, at) = cachedDialer
        if (System.currentTimeMillis() - at < CACHE_MS) return value
        val resolved = runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
        }.getOrNull()
        cachedDialer = resolved to System.currentTimeMillis()
        return resolved
    }

    companion object {
        private const val GATE_DEBOUNCE_MS = 1_500L
        private const val CACHE_MS = 30_000L
    }
}
