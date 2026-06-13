package com.ghost.drain.battery.health.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ghost.drain.battery.health.monitor.service.BatteryMonitorService
import com.ghost.drain.battery.health.monitor.util.OemBatteryOptimizationHelper

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView() — add your layout in Week 2

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // ─── Step 1: Request notification permission (Android 13+) ───────
        requestNotificationPermission()

        // ─── Step 2: Start the foreground service ─────────────────────────
        BatteryMonitorService.start(this)

        // ─── Step 3: Show OEM optimization screen on first launch only ────
        if (!prefs.getBoolean("oem_shown", false)) {
            OemBatteryOptimizationHelper.openOptimizationSettings(this)
            prefs.edit().putBoolean("oem_shown", true).apply()
        }

        // ─── Step 4: Record install timestamp for 24-hour ad grace period ─
        if (prefs.getLong("install_timestamp", 0L) == 0L) {
            prefs.edit().putLong("install_timestamp", System.currentTimeMillis()).apply()
        }

        // ─── Step 5: Initialize AppLovin MAX (playstore flavor only) ──────
        // BuildConfig.INCLUDE_APPLOVIN is true only for the playstore flavor.
        // In fdroid and huawei flavors this block is never reached.
        if (BuildConfig.INCLUDE_APPLOVIN) {
            initAdSdkIfSafe()
        }
    }

    /**
     * Three-gate check before any ad SDK is initialized:
     * 1. Installed from a trusted store (not sideloaded from APKPure etc.)
     * 2. Firebase Remote Config master switch is ON
     * 3. User has been installed for at least 24 hours (viral spike grace period)
     *
     * All three must pass. If any fails, no ad SDK is initialized for this session.
     */
    private fun initAdSdkIfSafe() {
        if (!isInstalledFromTrustedStore()) return
        if (!isAdGracePeriodOver()) return
        // Remote Config check happens inside AdManager (add in Week 2 when you wire UI)
        // For now just initialize AppLovin MAX in test mode
        initAppLovinMax()
    }

    private fun isInstalledFromTrustedStore(): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) { null }

        val trustedStores = setOf(
            "com.android.vending",                  // Google Play
            "com.sec.android.app.samsungapps",      // Samsung Galaxy Store
            "com.xiaomi.market",                    // Xiaomi GetApps
            "com.oppo.market",                      // OPPO
            "com.vivo.appstore",                    // Vivo
            "com.huawei.appmarket"                  // Huawei AppGallery (huawei flavor would skip this anyway)
        )
        return trustedStores.contains(installer)
    }

    private fun isAdGracePeriodOver(): Boolean {
        val installTime = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getLong("install_timestamp", System.currentTimeMillis())
        val hoursInstalled = (System.currentTimeMillis() - installTime) / 3_600_000L
        return hoursInstalled >= 24
    }

    private fun initAppLovinMax() {
        // Full AppLovin MAX init — expand this in Week 2 when building the home screen UI
        // AppLovinSdk.getInstance(this).apply {
        //     mediationProvider = "max"
        //     initializeSdk {
        //         // SDK initialized — safe to load ad units now
        //     }
        // }
        // Stub left intentionally — wire up when you add the home screen layout
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}