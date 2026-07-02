package com.gatekeeper.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.ui.gate.GateActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Core detection (Section 2). Event-driven: we react to
 * TYPE_WINDOW_STATE_CHANGED only — no polling, negligible battery cost
 * (R-2.1/R-2.2). When a gated package foregrounds without an active session
 * grant, the gate Activity is launched immediately. Activities started from
 * an AccessibilityService are exempt from Android 10+ background-launch
 * restrictions (R-2.4).
 */
@AndroidEntryPoint
class GatekeeperAccessibilityService : AccessibilityService() {

    @Inject lateinit var gateRepository: GateRepository
    @Inject lateinit var sessionManager: SessionManager

    /** Debounce so rapid window events for one package launch a single gate (R-2.6). */
    private var lastGatedPackage: String? = null
    private var lastGateLaunchAt: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        // Keep the process resilient while detection is armed (R-9.1).
        GatekeeperForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (shouldIgnore(pkg)) return
        // Hot path: both checks are in-memory reads (R-2.3 < 300 ms budget).
        if (!gateRepository.isBlocked(pkg)) return
        if (sessionManager.isUnlocked(pkg)) return

        val now = System.currentTimeMillis()
        if (pkg == lastGatedPackage && now - lastGateLaunchAt < GATE_DEBOUNCE_MS) return
        lastGatedPackage = pkg
        lastGateLaunchAt = now

        startActivity(GateActivity.intent(this, pkg))
    }

    /** R-2.5: never gate ourselves, the launcher, System UI, the IME, or call screens. */
    private fun shouldIgnore(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg == defaultLauncherPackage()) return true
        if (pkg == defaultImePackage()) return true
        if (pkg == defaultDialerPackage()) return true
        return false
    }

    // These resolve via system services; results are cheap lookups but we cache them
    // briefly because window events can arrive in bursts.
    private var cachedLauncher: Pair<String?, Long> = null to 0L
    private fun defaultLauncherPackage(): String? {
        val (value, at) = cachedLauncher
        if (System.currentTimeMillis() - at < CACHE_MS) return value
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        cachedLauncher = resolved to System.currentTimeMillis()
        return resolved
    }

    private fun defaultImePackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private var cachedDialer: Pair<String?, Long> = null to 0L
    private fun defaultDialerPackage(): String? {
        val (value, at) = cachedDialer
        if (System.currentTimeMillis() - at < CACHE_MS) return value
        val resolved = runCatching {
            val telecom = getSystemService(TELECOM_SERVICE) as android.telecom.TelecomManager
            telecom.defaultDialerPackage
        }.getOrNull()
        cachedDialer = resolved to System.currentTimeMillis()
        return resolved
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val GATE_DEBOUNCE_MS = 1_500L
        private const val CACHE_MS = 30_000L

        /** Cheap liveness signal for R-9.4 ("service disabled — prompt to re-enable"). */
        @Volatile var isRunning: Boolean = false
            private set
    }
}
