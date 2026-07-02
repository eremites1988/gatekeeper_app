package com.gatekeeper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gatekeeper.app.MainActivity
import com.gatekeeper.app.R
import com.gatekeeper.app.data.GateRepository
import com.gatekeeper.app.data.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service with a persistent low-priority notification (R-9.1).
 * Keeps the process (and with it the in-memory session/blocklist caches)
 * alive, prunes expired grants, and surfaces a warning in the notification
 * when the accessibility service has been disabled (R-9.4).
 */
@AndroidEntryPoint
class GatekeeperForegroundService : LifecycleService() {

    @Inject lateinit var gateRepository: GateRepository
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch {
            while (true) {
                sessionManager.pruneExpired()
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (GatekeeperAccessibilityService.isRunning) {
            getString(R.string.notification_text_ok, gateRepository.blockedPackages.value.size)
        } else {
            getString(R.string.notification_text_service_off)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(contentIntent)
            .build()
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

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, GatekeeperForegroundService::class.java)
                )
            }
        }
    }
}
