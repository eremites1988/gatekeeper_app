package com.gatekeeper.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Primary detection path (Section 2). Event-driven: we react to
 * TYPE_WINDOW_STATE_CHANGED only — no polling, negligible battery cost
 * (R-2.1/R-2.2). Activities started from an AccessibilityService are exempt
 * from Android 10+ background-launch restrictions (R-2.4).
 *
 * When this service is unavailable (e.g. a work-profile admin blocks
 * third-party accessibility services device-wide), the UsageStats poller in
 * [GatekeeperForegroundService] takes over automatically.
 */
@AndroidEntryPoint
class GatekeeperAccessibilityService : AccessibilityService() {

    @Inject lateinit var gateCoordinator: GateCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        // Keep the process resilient while detection is armed (R-9.1).
        GatekeeperForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        gateCoordinator.onForegroundApp(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        /** Cheap liveness signal for R-9.4 and for the fallback poller handoff. */
        @Volatile var isRunning: Boolean = false
            private set
    }
}
