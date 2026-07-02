package com.gatekeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gatekeeper.app.service.GatekeeperForegroundService
import com.gatekeeper.app.ui.GatekeeperApp
import com.gatekeeper.app.ui.theme.GatekeeperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep detection/session state alive in the background (R-9.1).
        GatekeeperForegroundService.start(this)
        setContent {
            GatekeeperTheme {
                GatekeeperApp()
            }
        }
    }
}
