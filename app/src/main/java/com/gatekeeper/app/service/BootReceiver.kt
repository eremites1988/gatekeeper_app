package com.gatekeeper.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gatekeeper.app.data.SessionManager
import com.gatekeeper.app.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms detection and re-evaluates session grants after a reboot (R-9.2, R-8.4). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                sessionManager.onBoot(settings.grantsSurviveReboot)
                GatekeeperForegroundService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
