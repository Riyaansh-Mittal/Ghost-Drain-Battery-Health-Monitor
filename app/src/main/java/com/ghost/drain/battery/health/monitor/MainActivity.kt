package com.ghost.drain.battery.health.monitor

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ghost.drain.battery.health.monitor.service.BatteryMonitorService
import com.ghost.drain.battery.health.monitor.ui.theme.BatteryHealthMonitorTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Permission result handled — service already started
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        startBatteryService()

        setContent {
            BatteryHealthMonitorTheme {
                // Placeholder — replaced in Part 3 with full navigation
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Part 1 OK — check logcat for battery data")
                }
            }
        }
    }

    private fun startBatteryService() {
        ContextCompat.startForegroundService(
            this,
            BatteryMonitorService.buildStartIntent(this)
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}