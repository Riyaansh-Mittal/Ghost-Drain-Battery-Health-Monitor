package com.ghost.drain.battery.health.monitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService

class BatteryApp : Application() {

    companion object {
        const val CHANNEL_PERSISTENT = "battery_persistent"
        const val CHANNEL_ALARM      = "battery_alarm"
        const val CHANNEL_SILENT     = "battery_silent"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return

        // Persistent status — always visible, no sound
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PERSISTENT,
                "Battery Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live battery status in your notification bar" }
        )

        // Alarm channel — HIGH importance so it bypasses DND
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                "Charge Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fires when battery reaches your set threshold"
                enableVibration(true)
                setBypassDnd(true)
            }
        )

        // Silent informational (ghost drain, overnight summary)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                "Battery Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Ghost drain alerts and overnight summaries" }
        )
    }
}