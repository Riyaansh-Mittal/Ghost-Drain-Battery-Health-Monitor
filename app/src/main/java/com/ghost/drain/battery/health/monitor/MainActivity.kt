package com.ghost.drain.battery.health.monitor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.ghost.drain.battery.health.monitor.data.UserPreferences
import com.ghost.drain.battery.health.monitor.service.BatteryMonitorService
import com.ghost.drain.battery.health.monitor.ui.onboarding.OnboardingScreen
import com.ghost.drain.battery.health.monitor.ui.theme.BatteryHealthMonitorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.ghost.drain.battery.health.monitor.ui.theme.Black
import com.ghost.drain.battery.health.monitor.ui.theme.TextMuted
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless — service already started */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }

        // Start battery service immediately regardless of onboarding state
        startBatteryService()

        // Determine start destination once, then set content
        lifecycleScope.launch {
            val prefs           = UserPreferences(applicationContext)
            val onboardingDone  = prefs.isOnboardingDone.first()

            setContent {
                BatteryHealthMonitorTheme {
                    AppNavHost(initialOnboardingDone = onboardingDone)
                }
            }
        }
    }

    private fun startBatteryService() {
        val intent = Intent(this, BatteryMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

// ── Navigation host ───────────────────────────────────────────────────────────

@Composable
private fun AppNavHost(initialOnboardingDone: Boolean) {
    // Single source of truth for which screen to show
    var onboardingDone by remember { mutableStateOf(initialOnboardingDone) }

    if (!onboardingDone) {
        OnboardingScreen(
            onComplete = { onboardingDone = true }
        )
    } else {
        // Part 4: HomeScreen goes here
        HomePlaceholder()
    }
}

@Composable
private fun HomePlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = Black
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text  = "Home screen coming in Part 4",
                color = TextMuted
            )
        }
    }
}