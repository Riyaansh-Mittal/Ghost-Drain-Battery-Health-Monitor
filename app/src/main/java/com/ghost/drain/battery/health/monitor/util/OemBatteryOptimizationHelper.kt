package com.ghost.drain.battery.health.monitor.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OemBatteryOptimizationHelper {

    private const val TAG = "OemBatteryOpt"
    private val manufacturer = Build.MANUFACTURER.lowercase()

    /**
     * Call this on first launch AND from the "Alarms not firing?" link.
     * Returns true if any settings screen was successfully opened.
     */
    fun openOptimizationSettings(context: Context): Boolean {

        // Samsung
        if (manufacturer.contains("samsung")) {
            listOf(
                Intent().setComponent(ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName(
                    "com.samsung.android.sm.battery",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"))
            ).forEach { if (tryStart(context, it)) return true }
        }

        // Xiaomi / MIUI / HyperOS / Redmi / Poco
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco")) {
            listOf(
                Intent().setComponent(ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"))
            ).forEach { if (tryStart(context, it)) return true }
        }

        // OPPO / ColorOS / Realme
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            listOf(
                Intent().setComponent(ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"))
            ).forEach { if (tryStart(context, it)) return true }
        }

        // Vivo / FuntouchOS / OriginOS
        if (manufacturer.contains("vivo")) {
            val intent = Intent().setComponent(ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            if (tryStart(context, intent)) return true
        }

        // Huawei / EMUI / HarmonyOS / Honor
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            val intent = Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            if (tryStart(context, intent)) return true
        }

        // OnePlus — uses ColorOS since OxygenOS 14
        if (manufacturer.contains("oneplus")) {
            val intent = Intent().setComponent(ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"))
            if (tryStart(context, intent)) return true
        }

        // Stock Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            if (tryStart(context, intent)) return true
        }

        // Fallback 1
        if (tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
            return true

        // Fallback 2
        return tryStart(context, Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opened: ${intent.component ?: intent.action}")
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Not found: ${intent.component ?: intent.action}"); false
        } catch (e: SecurityException) {
            Log.w(TAG, "Security: ${e.message}"); false
        } catch (e: Exception) {
            Log.w(TAG, "Failed: ${e.message}"); false
        }
    }
}