package com.ghost.drain.battery.health.monitor.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Handles battery optimization exemption.
 *
 * Required in AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
 *
 * Flow:
 *   • getStatus() → fast synchronous check via PowerManager
 *   • NOT_EXEMPTED → show banner, user taps → requestExemption() → direct per-app dialog
 *   • ALREADY_EXEMPTED → hide banner, no action needed
 *   • If user later re-enables optimization in Settings, the service detects it
 *     and posts a warning notification (see BatteryMonitorService.checkOptimizationStatus)
 *
 * OEM routing logic:
 *   On Xiaomi/Realme/OnePlus/Huawei/Vivo etc., ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 *   is silently intercepted or opens a wrong screen WITHOUT throwing an exception — so we
 *   NEVER try it first on OEM devices. Instead we detect the manufacturer via Build.MANUFACTURER
 *   and Build.BRAND, route to the exact OEM settings activity, and only fall back to the
 *   standard Android path if no OEM match is found.
 */
object OemBatteryOptimizationHelper {

    enum class OptimizationStatus {
        ALREADY_EXEMPTED,   // app is whitelisted — banner must be hidden
        NOT_EXEMPTED,       // OS can kill background service — show banner
        UNAVAILABLE         // PowerManager not available (shouldn't happen API 23+)
    }

    /** Synchronous — safe to call on any thread. */
    fun getStatus(context: Context): OptimizationStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return OptimizationStatus.UNAVAILABLE
        return if (pm.isIgnoringBatteryOptimizations(context.packageName))
            OptimizationStatus.ALREADY_EXEMPTED
        else
            OptimizationStatus.NOT_EXEMPTED
    }

    fun isExempted(context: Context) =
        getStatus(context) == OptimizationStatus.ALREADY_EXEMPTED

    /**
     * Routes to the exact OEM battery exemption screen.
     *
     * OEM-first strategy: detect manufacturer BEFORE firing any intent.
     * Standard ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is tried ONLY on
     * stock Android / unknown OEMs — never on Xiaomi, Realme, OnePlus etc.
     * because those OEMs silently intercept it without throwing, causing
     * tryStandardExemption() to return true while taking the user nowhere useful.
     */
    fun requestExemption(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand        = Build.BRAND.lowercase(Locale.ROOT)

        val isXiaomi = manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi")         || brand.contains("poco")

        if (isXiaomi) {
            // Xiaomi/MIUI/HyperOS blocks ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS by
            // policy — the dialog never appears. Must go to their autostart settings page.
            val handled = tryIntents(context, xiaomiIntents(context))
            if (!handled) tryStandardExemptionDialog(context) // last resort
        } else {
            // All other OEMs (Samsung, OnePlus, Realme, Huawei, Vivo, stock, etc.):
            // The standard Android popup dialog works correctly — shows a one-tap
            // "Allow / Deny" alert without leaving the app. Use it directly.
            tryStandardExemptionDialog(context)
        }
    }

    // ── Intent dispatcher ──────────────────────────────────────────────────────

    /**
     * Tries each Intent in order. Uses packageManager.resolveActivity() to confirm
     * the activity actually exists on this device before calling startActivity(),
     * so we never get an ActivityNotFoundException crash.
     */
    private fun tryIntents(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Use 0 flags — MATCH_DEFAULT_ONLY can miss explicit class intents
                // On Android 11+, add your OEM packages to <queries> in AndroidManifest.xml
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    // ── Standard Android fallback ──────────────────────────────────────────────

    private fun tryStandardExemptionDialog(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        } catch (_: Exception) {}

        // Fallback only if the dialog itself fails (shouldn't happen on API 23+)
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {}
    }

    // ── OEM intent lists ───────────────────────────────────────────────────────
    // Each list is ordered: most specific / most common ROM version first,
    // older / less common fallbacks last. tryIntents() stops at first success.

    /** Xiaomi / Redmi / POCO — MIUI 10-14 and HyperOS */
    private fun xiaomiIntents(context: Context) = listOf(
        // HyperOS / MIUI 13-14: direct per-app autostart page
        Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            putExtra("package_name", context.packageName)
        },
        // MIUI 12: power keeper hidden apps
        Intent().setClassName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
        ).apply { putExtra("package_name", context.packageName) },
        // MIUI 10-11: security center autostart list
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    )

    /** Realme / Realme UI 1-4 */
    private fun realmeIntents(context: Context) = listOf(
        // Realme UI 3-4 (ColorOS 12+): direct per-app startup
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.FakeActivity"
        ).apply { putExtra("package_name", context.packageName) },
        // Realme UI 2 (ColorOS 11)
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        // Older Realme (OPPO Color OS)
        Intent().setClassName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        // Power management fallback
        Intent().setClassName(
            "com.coloros.oppoguardelf",
            "com.coloros.powermanager.fuelgauge.PowerUsageModelActivity"
        )
    )

    /** OPPO / ColorOS */
    private fun oppoIntents(context: Context) = listOf(
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startupapp.StartupAppListActivity"
        )
    )

    /**
     * OnePlus / OxygenOS / OxygenOS 12+ (ColorOS-based).
     * OxygenOS 12+ shipped on OnePlus 10+ uses ColorOS packages, so we try
     * both the native OxygenOS path and the ColorOS fallback.
     */
    private fun onePlusIntents(context: Context) = listOf(
        // OxygenOS 11 and below — native chain-launch manager
        Intent().setClassName(
            "com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        ),
        // OxygenOS 12+ (ColorOS-based) — reuses ColorOS safecenter
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        // Direct standard dialog — works on OxygenOS unlike on Xiaomi/Realme
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )

    /** Huawei / Honor — EMUI 8-12 */
    private fun huaweiIntents() = listOf(
        // EMUI 9+
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
        ),
        // EMUI 8
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.mainProxy.MainProxyActivity"
        )
    )

    /** Samsung / OneUI */
    private fun samsungIntents() = listOf(
        // OneUI 4+ (Android 12+) — "Never sleeping apps" list: direct per-app whitelist
        // This is Settings > Battery > Background usage limits > Never sleeping apps
        Intent().apply {
            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            data = Uri.parse("package:com.ghost.drain.battery.health.monitor")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // OneUI 3-4: Background usage limits page (user adds app to "Never sleeping")
        Intent().setClassName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.BatteryUnusedAppActivity"
        ),
        // OneUI 4+: alternate background restriction page
        Intent().setClassName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.SleepingAppsActivity"
        ),
        // OneUI 5+ (Android 13+): app-specific battery settings deep link
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
            data = Uri.parse("package:com.ghost.drain.battery.health.monitor")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Fallback to standard Android per-app battery optimization dialog
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:com.ghost.drain.battery.health.monitor")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Last resort: battery page (what you're hitting now)
        Intent().setClassName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.BatteryActivity"
        )
    )

    /** Vivo / iQOO — FuntouchOS / OriginOS */
    private fun vivoIntents(context: Context) = listOf(
        // FuntouchOS 11+ / OriginOS
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        // iQOO devices
        Intent().setClassName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        ).apply { putExtra("packagename", context.packageName) },
        Intent().setClassName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
        )
    )

    /** Meizu / Flyme */
    private fun meizuIntents(context: Context) = listOf(
        Intent().setClassName(
            "com.meizu.safe",
            "com.meizu.safe.security.SHOW_APPSEC"
        ).apply {
            putExtra("packageName", context.packageName)
            putExtra("com.meizu.safe.security.SHOW_APPSEC", "com.meizu.safe.security.AppSecActivity")
        }
    )

    /** Asus / ZenUI */
    private fun asusIntents() = listOf(
        Intent().setClassName(
            "com.asus.mobilemanager",
            "com.asus.mobilemanager.powersaver.PowerSaverSettings"
        ),
        Intent().setClassName(
            "com.asus.mobilemanager",
            "com.asus.mobilemanager.entry.FunctionActivity"
        ).apply { data = Uri.parse("mobilemanager://function/index/7") }
    )

    /** Sony / Xperia */
    private fun sonyIntents() = listOf(
        Intent().setClassName(
            "com.sonymobile.cta",
            "com.sonymobile.cta.SomcCTAMainActivity"
        )
    )

    /** Nokia / HMD */
    private fun nokiaIntents() = listOf(
        Intent().setClassName(
            "com.evenwell.powersaving.g3",
            "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
        )
    )

    /** Lenovo / ZUI */
    private fun lenovoIntents() = listOf(
        Intent().setClassName(
            "com.lenovo.security",
            "com.lenovo.security.purebackground.PureBackgroundActivity"
        )
    )
}