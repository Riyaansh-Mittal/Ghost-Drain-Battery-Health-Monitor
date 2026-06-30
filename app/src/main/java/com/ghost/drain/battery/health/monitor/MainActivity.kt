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
import com.ghost.drain.battery.health.monitor.ui.home.HomeScreen
import com.ghost.drain.battery.health.monitor.ui.alarm.AlarmScreen
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    // Mutable state so onNewIntent can push a new value into the composition
    // without restarting the Activity.
    private val openAlarmScreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Read the extra BEFORE setContent so the initial composition already
        // knows whether to go straight to AlarmScreen.
        openAlarmScreen.value = intent
            ?.getBooleanExtra(BatteryMonitorService.EXTRA_OPEN_ALARM_SCREEN, false) == true

        lifecycleScope.launch {
            val prefs          = UserPreferences(applicationContext)
            val onboardingDone = prefs.isOnboardingDone.first()

            setContent {
                BatteryHealthMonitorTheme {
                    AppNavHost(
                        initialOnboardingDone = onboardingDone,
                        openAlarmScreen       = openAlarmScreen
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If an alarm is sounding and the user opens the app, stop it automatically.
        startService(
            Intent(this, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_APP_OPENED
            }
        )
    }

    // Called when the app is already open and the user taps the notification.
    // FLAG_ACTIVITY_SINGLE_TOP means onCreate is NOT called again — only this.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BatteryMonitorService.EXTRA_OPEN_ALARM_SCREEN, false)) {
            openAlarmScreen.value = true
        }
    }
}

// ── Navigation host ───────────────────────────────────────────────────────────

@Composable
private fun AppNavHost(
    initialOnboardingDone: Boolean,
    openAlarmScreen: MutableState<Boolean>
) {
    var onboardingDone by remember { mutableStateOf(initialOnboardingDone) }

    // "alarm" if we were opened from the OEM notification, otherwise "home"
    var currentScreen by remember {
        mutableStateOf(if (openAlarmScreen.value) "alarm" else "home")
    }

    // React to onNewIntent pushing openAlarmScreen = true while app is already
    // on screen (e.g. user is on HomeScreen and taps the notification).
    LaunchedEffect(openAlarmScreen.value) {
        if (openAlarmScreen.value && onboardingDone) {
            currentScreen = "alarm"
            // Reset so navigating back to Home and tapping the notification
            // again still works correctly.
            openAlarmScreen.value = false
        }
    }

    if (!onboardingDone) {
        OnboardingScreen(onComplete = {
            onboardingDone = true
        })
    } else {
        // User already completed onboarding — start the battery service if not running.
        val ctx = LocalContext.current
        LaunchedEffect(Unit) {
            val intent = android.content.Intent(ctx, BatteryMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(intent)
            else
                ctx.startService(intent)
        }

        when (currentScreen) {
            "home"  -> HomeScreen(onNavigateToAlarm = { currentScreen = "alarm" })
            "alarm" -> AlarmScreen(onBack = { currentScreen = "home" })
        }
    }
}