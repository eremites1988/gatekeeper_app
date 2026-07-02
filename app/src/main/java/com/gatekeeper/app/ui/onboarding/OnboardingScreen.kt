package com.gatekeeper.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeeper.app.data.SettingsRepository
import com.gatekeeper.app.service.GatekeeperForegroundService
import com.gatekeeper.app.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    fun complete() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }
}

private data class Step(
    val title: String,
    val why: String,
    val required: Boolean,
)

/**
 * First-run wizard (R-10.2): explains and verifies each permission before
 * the app can be used. Each step deep-links to the relevant settings page.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check grants every time the user comes back from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var notificationsRequested by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val accessibilityGranted = remember(refreshTick) {
        Permissions.isAccessibilityServiceEnabled(context)
    }
    val overlayGranted = remember(refreshTick) { Permissions.canDrawOverlays(context) }
    val notificationsGranted = remember(refreshTick, notificationsRequested) {
        Permissions.hasNotificationPermission(context)
    }
    val batteryGranted = remember(refreshTick) {
        Permissions.isIgnoringBatteryOptimizations(context)
    }
    val usageGranted = remember(refreshTick) { Permissions.hasUsageStatsAccess(context) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Welcome to Gatekeeper", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Gatekeeper turns impulsive app-opening into a productive trade: finish " +
                    "tasks, do exercises, or read pages before a distracting app opens.\n\n" +
                    "Everything runs on this device — no internet, no analytics, no accounts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            StepCard(
                title = "1. Accessibility service (recommended)",
                why = "The best way to detect which app is in the foreground so the gate can " +
                    "appear instantly. Gatekeeper never reads your screen content. " +
                    "If this option is blocked on your device (common when a work profile " +
                    "restricts third-party accessibility services), skip it and enable " +
                    "compatibility mode in steps 4 and 5 instead.",
                granted = accessibilityGranted,
                buttonText = "Enable in settings",
            ) { context.startActivity(Permissions.accessibilitySettingsIntent()) }

            StepCard(
                title = "2. Notifications",
                why = "Shows a quiet, persistent notification that keeps Gatekeeper alive " +
                    "in the background.",
                granted = notificationsGranted,
                buttonText = "Allow notifications",
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationsRequested = true
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            StepCard(
                title = "3. Battery optimization exemption",
                why = "Stops Android (and aggressive manufacturer battery managers on " +
                    "Samsung, Xiaomi, Oppo, Huawei…) from killing Gatekeeper in the background.",
                granted = batteryGranted,
                buttonText = "Exempt Gatekeeper",
            ) { context.startActivity(Permissions.batteryOptimizationIntent(context)) }

            StepCard(
                title = "4. Display over other apps" +
                    if (accessibilityGranted) " (optional)" else " (compatibility mode)",
                why = "Lets the gate appear over a blocked app when the accessibility " +
                    "service isn't available.",
                granted = overlayGranted,
                buttonText = "Allow overlay",
            ) { context.startActivity(Permissions.overlaySettingsIntent(context)) }

            StepCard(
                title = "5. Usage access" +
                    if (accessibilityGranted) " (optional)" else " (compatibility mode)",
                why = "Detects the foreground app by briefly checking usage events about " +
                    "once a second while the screen is on. Used automatically whenever the " +
                    "accessibility service isn't available.",
                granted = usageGranted,
                buttonText = "Allow usage access",
            ) { context.startActivity(Permissions.usageAccessSettingsIntent()) }

            val fallbackReady = usageGranted && overlayGranted
            val detectionReady = accessibilityGranted || fallbackReady

            if (!accessibilityGranted && fallbackReady) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Compatibility mode is ready. Gatekeeper will detect apps via usage " +
                        "events; the gate may take a moment longer to appear than with the " +
                        "accessibility service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    GatekeeperForegroundService.start(context)
                    viewModel.complete()
                },
                enabled = detectionReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        accessibilityGranted -> "Start using Gatekeeper"
                        fallbackReady -> "Start using Gatekeeper (compatibility mode)"
                        else -> "Enable step 1, or steps 4 + 5, to continue"
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    why: String,
    granted: Boolean,
    buttonText: String,
    onGrant: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(why, style = MaterialTheme.typography.bodySmall)
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onGrant) { Text(buttonText) }
                }
            }
        }
    }
}
