package com.ghost.drain.battery.health.monitor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.ui.components.*
import com.ghost.drain.battery.health.monitor.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state               = state,
        onAlarmToggleHigh   = viewModel::toggleHighAlarm,
        onAlarmToggleLow    = viewModel::toggleLowAlarm,
        onAlarmCardClick    = onNavigateToAlarm,
        onHealthCardClick   = onNavigateToHealth,
        onDismissGhostDrain = viewModel::dismissGhostDrainBanner
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onAlarmToggleHigh: () -> Unit,
    onAlarmToggleLow: () -> Unit,
    onAlarmCardClick: () -> Unit,
    onHealthCardClick: () -> Unit,
    onDismissGhostDrain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolling removed to force a single frame
                .padding(horizontal = 16.dp)
                // Reduced top/bottom padding so elements don't get crushed
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Tightened the spacing slightly to ensure fit on smaller devices
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── 0. Top Bar (Menu & Settings) ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = TextPrimary
                )
                Icon(
                    painter = painterResource(R.drawable.ic_bell),
                    contentDescription = "Notifications",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary
                )
            }

            // ── 1. Battery gauge (Wrapped in Weight to prevent scrolling) ─────
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                BatteryGauge(
                    percent            = state.percent,
                    temperatureCelsius = state.temperatureCelsius,
                    isCharging         = state.isCharging, // Pass the state from your UI State
                    size               = 200.dp,
                    modifier           = Modifier
                )
            }

            // ── 2. Charging state pill ────────────────────────────────────────
            ChargingPill(text = state.chargingPillText, isCharging = state.isCharging)

            // ── 3. Time to full (only visible when charging) ──────────────────
            AnimatedVisibility(
                visible = state.minutesToFullText.isNotEmpty(),
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        tint     = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = state.minutesToFullText,
                        style = BatteryTypography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            // ── 4. Temp + Charger row ─────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemperatureCard(
                    displayText  = state.tempDisplayText,
                    contextText  = state.tempContextText,
                    colorLevel   = state.tempColorLevel,
                    modifier     = Modifier.weight(1f)
                )
                ChargerCard(
                    verdictLine    = state.chargerVerdictLine,
                    qualityText    = state.chargerQualityText,
                    deviceModel    = state.deviceModelText,
                    colorLevel     = state.chargerColorLevel,
                    modifier       = Modifier.weight(1f)
                )
            }

            // ── 5. Alarm hero card ────────────────────────────────────────────
            AlarmToggleCard(
                title       = "Unplug alarm at ${state.highAlarmPercent}%",
                subtitle    = "Rings every 60s until unplugged",
                isEnabled   = state.highAlarmEnabled,
                onToggle    = { onAlarmToggleHigh() },
                onCardClick = onAlarmCardClick
            )

            // ── 6. Battery health card ────────────────────────────────────────
            BatteryHealthCard(
                healthPercent    = state.healthPercent,
                gradeText        = state.healthGradeText,
                currentMah       = state.healthCurrentMah,
                designMah        = state.healthDesignMah,
                onCardClick      = onHealthCardClick
            )

            // ── 7. Live mA + voltage card ─────────────────────────────────────
            LiveMaCard(
                currentText      = state.currentText,
                netCurrentText   = state.netCurrentText,
                averageCurrentText = state.averageCurrentText,
                isPositive       = state.currentIsPositive,
                voltageMvText    = state.voltageMvText,
                sparklinePoints  = state.sparklinePoints
            )

            // ── 8. Ghost drain banner (conditional) ───────────────────────────
            AnimatedVisibility(
                visible = state.showGhostDrainBanner,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                GhostDrainBanner(onDismiss = onDismissGhostDrain)
            }
        }
    }
}

// ── Charging pill ─────────────────────────────────────────────────────────────

@Composable
private fun ChargingPill(text: String, isCharging: Boolean) {
    val bgColor     = if (isCharging) SurfaceCardAlt else SurfaceCard
    val textColor   = if (isCharging) GreenPrimary else TextSecondary
    val borderColor = if (isCharging) GreenMuted else BorderStandard

    Surface(
        shape  = RoundedCornerShape(percent = 50),
        color  = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isCharging) {
                Icon(
                    painter = painterResource(R.drawable.ic_thunderbolt),
                    contentDescription = null,
                    tint     = GreenPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text       = text,
                color      = textColor,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Temperature card ──────────────────────────────────────────────────────────

@Composable
private fun TemperatureCard(
    displayText: String,
    contextText: String,
    colorLevel: TempLevel,
    modifier: Modifier = Modifier
) {
    val accent = when (colorLevel) {
        TempLevel.NORMAL  -> GreenPrimary
        TempLevel.WARNING -> AmberPrimary
        TempLevel.DANGER  -> RedPrimary
    }
    val bg = when (colorLevel) {
        TempLevel.NORMAL  -> SurfaceCard
        TempLevel.WARNING -> SurfaceCardAlt
        TempLevel.DANGER  -> SurfaceCardAlt
    }
    val border = when (colorLevel) {
        TempLevel.NORMAL  -> BorderStandard
        TempLevel.WARNING -> BorderStandard
        TempLevel.DANGER  -> BorderStandard
    }

    Card(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(R.drawable.ic_thermometer), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = displayText, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(text = contextText, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

// ── Charger card ──────────────────────────────────────────────────────────────

@Composable
private fun ChargerCard(
    verdictLine: String,
    qualityText: String,
    deviceModel: String,
    colorLevel: ChargerLevel,
    modifier: Modifier = Modifier
) {
    val accent = when (colorLevel) {
        ChargerLevel.NORMAL  -> GreenPrimary
        ChargerLevel.WARNING -> AmberPrimary
        ChargerLevel.DANGER  -> RedPrimary
    }
    val bg = when (colorLevel) {
        ChargerLevel.NORMAL  -> SurfaceCard
        ChargerLevel.WARNING -> SurfaceCardAlt
        ChargerLevel.DANGER  -> SurfaceCardAlt
    }
    val border = when (colorLevel) {
        ChargerLevel.NORMAL  -> BorderStandard
        ChargerLevel.WARNING -> BorderStandard
        ChargerLevel.DANGER  -> BorderStandard
    }

    Card(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(16.dp), // 16.dp for exact match
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verdictLine,
                    fontSize = 14.sp,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                if (qualityText.isNotEmpty()) {
                    Text(
                        text = qualityText,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (deviceModel.isNotEmpty()) {
                    Text(
                        text = deviceModel,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
            if (colorLevel == ChargerLevel.NORMAL) {
                // Updated: Using ic_circle_check instead of manual text
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = "Status OK",
                        tint = GreenPrimary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Battery health card ───────────────────────────────────────────────────────

@Composable
private fun BatteryHealthCard(
    healthPercent: Int?,
    gradeText: String,
    currentMah: Int?,
    designMah: Int?,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderStandard)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_heartbeat), contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Battery health", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    if (healthPercent != null) {
                        Text(text = " · ${healthPercent}%", fontSize = 16.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (healthPercent != null) {
                    LinearProgressIndicator(
                        progress = { healthPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = GreenPrimary,
                        trackColor = BorderStandard,
                    )
                }
            }
        }
    }
}

// ── Live mA card ──────────────────────────────────────────────────────────────

@Composable
private fun LiveMaCard(
    currentText: String,
    netCurrentText: String,
    averageCurrentText: String,
    isPositive: Boolean,
    voltageMvText: String,
    sparklinePoints: List<Float>
) {
    InfoCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = SurfaceCard,
        accentColor     = BorderStandard
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_thunderbolt),
                        contentDescription = null,
                        tint               = GreenPrimary,
                        modifier           = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text       = currentText,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isPositive) GreenPrimary else AmberPrimary
                    )
                    if (netCurrentText.isNotEmpty()) {
                        Text(
                            text  = netCurrentText,
                            style = BatteryTypography.bodySmall,
                            color = GreenPrimary
                        )
                    }
                    if (averageCurrentText.isNotEmpty() && averageCurrentText != "—") {
                        Text(
                            text  = averageCurrentText,
                            style = BatteryTypography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                if (voltageMvText.isNotEmpty()) {
                    Text(
                        text       = voltageMvText,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary
                    )
                    Text(
                        text  = "Voltage",
                        style = BatteryTypography.labelSmall,
                        color = TextMuted
                    )
                }
                if (sparklinePoints.size >= 3) {
                    Spacer(Modifier.height(8.dp))
                    MiniSparkline(
                        points   = sparklinePoints,
                        color    = GreenPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }
        }
    }
}

// ── Mini sparkline ────────────────────────────────────────────────────────────

@Composable
private fun MiniSparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    Canvas(modifier = modifier) {
        val min = points.min()
        val max = points.max()
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1).toFloat()

        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path   = path,
            color  = color,
            style  = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ── Ghost drain banner ────────────────────────────────────────────────────────

@Composable
private fun GhostDrainBanner(onDismiss: () -> Unit) {
    AmberCard {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👻", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ghost drain detected — something is preventing deep sleep. Tap to investigate.",
                    style = BatteryTypography.bodyMedium,
                    color = AmberPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter            = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint               = AmberPrimary,
                modifier           = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "✕",
                color    = TextMuted,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}