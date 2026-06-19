package com.ghost.drain.battery.health.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary          = GreenPrimary,
    onPrimary        = Black,
    primaryContainer = GreenDark,
    onPrimaryContainer = GreenBright,
    secondary        = AmberPrimary,
    onSecondary      = Black,
    tertiary         = PurplePrimary,
    onTertiary       = TextPrimary,
    error            = RedPrimary,
    onError          = TextPrimary,
    background       = Black,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline          = Color(0xFF333333),
    outlineVariant   = Color(0xFF222222),
)

@Composable
fun BatteryHealthMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = BatteryTypography,
        content     = content
    )
}