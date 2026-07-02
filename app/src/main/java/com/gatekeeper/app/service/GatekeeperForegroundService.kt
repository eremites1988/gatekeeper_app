package com.gatekeeper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gatekeeper.app.MainActivity
import com.gatekeeper.app.R
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.util.DetectionMode
import com.gatekeeper.app.util.Permissions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service with a persistent low-priority notification (R-9.1).
 * Keeps the process (and with it the in-memory session/blocklist caches)
 * alive, prunes expired grants, and surfaces detection problems in the
 * notification (R-9.4).
 *
 * It also hosts the **compatibility-mode detector**: when the accessibility
 * service is unavailable (e.g. blocked device-wide by a work-profile
 * policy), it polls UsageStats for foreground-app changes while the screen
 * is on and feeds them to the same [GateCoordinator]. Polling only runs
 * when actually needed; the event-driven accessibility path is always
 * preferred (R-2.2).
 */
@AndroidEntryPoint
class GatekeeperForegroundService : LifecycleService() {

    @Inject lateinit var gateRepository: GateRepository
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var gateCoordinator: GateCoordinator

    private var lastShownText: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch {
            while (true) {
                sessionManager.pruneExpired()
                updateNotificationIfChanged()
                delay(60_000)
            }
        }
        lifecycleScope.launch { usagePollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    /**
     * Compatibility fallback (R-2.2): ~1 s UsageStats polling, active only
     * while (a) the accessibility service is not running, (b) usage access
     * is granted, and (c) the screen is interactive. Otherwise it idles
     * cheaply.
     */
    private suspend fun usagePollLoop() {
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val resumedEventType = if (Build.VERSION.SDK_INT >= 29) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

        var windowStart = System.currentTimeMillis()
        var lastForeground: String? = null

        while (true) {
            if (GatekeeperAccessibilityService.isRunning) {
                // Primary path is active — stand down and keep the query
                // window fresh for a clean handoff if it ever stops.
                windowStart = System.currentTimeMillis()
                delay(IDLE_INTERVAL_MS)
                continue
            }
            if (!Permissions.hasUsageStatsAccess(this) || !power.isInteractive) {
                windowStart = System.currentTimeMillis()
                delay(IDLE_INTERVAL_MS)
                continue
            }

            val now = System.currentTimeMillis()
            // Small overlap so events landing between ticks are never missed;
            // repeats are harmless (same-package check + coordinator debounce).
            val events = usageStats.queryEvents(windowStart - QUERY_OVERLAP_MS, now)
            windowStart = now

            var latest: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == resumedEventType) latest = event.packageName
            }
            if (latest != null && latest != lastForeground) {
                lastForeground = latest
                gateCoordinator.onForegroundApp(latest)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val gatedCount = gateRepository.blockedPackages.value.size
        val text = when (Permissions.detectionMode(this)) {
            DetectionMode.ACCESSIBILITY -> getString(R.string.notification_text_ok, gatedCount)
            DetectionMode.USAGE_FALLBACK -> getString(R.string.notification_text_fallback, gatedCount)
            DetectionMode.NONE -> getString(R.string.notification_text_service_off)
        }
        lastShownText = text
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotificationIfChanged() {
        val previous = lastShownText
        val notification = buildNotification()
        if (lastShownText != previous) {
            notificationManager().notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply { description = getString(R.string.notification_channel_description) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "gatekeeper_service"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 1_000L
        private const val IDLE_INTERVAL_MS = 3_000L
        private const val QUERY_OVERLAP_MS = 1_000L

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, GatekeeperForegroundService::class.java)
                )
            }
        }
    }
}
