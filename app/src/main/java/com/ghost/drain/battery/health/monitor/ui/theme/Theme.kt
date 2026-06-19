package com.ghost.drain.battery.health.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Black,
    background = Black,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    secondary = AmberPrimary,
    tertiary = PurplePrimary,
    error = RedPrimary
)

@Composable
fun BatteryHealthMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = BatteryTypography,
        content = content
    )
}