package com.gatekeeper.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gatekeeper.app.worker.DailyResetWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Gatekeeper — a productivity-gated app blocker.
 *
 * PRIVACY: this app is 100% on-device. It declares no INTERNET permission,
 * makes no network calls, and ships no analytics. Nothing leaves the device.
 */
@HiltAndroidApp
class GatekeeperApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailyReset()
    }

    /** R-5.4: recurring tasks reset shortly after midnight every day. */
    private fun scheduleDailyReset() {
        val now = LocalDateTime.now()
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5)
        val initialDelay = Duration.between(now, nextMidnight)
        val request = PeriodicWorkRequestBuilder<DailyResetWorker>(Duration.ofDays(1))
            .setInitialDelay(initialDelay)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyResetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
