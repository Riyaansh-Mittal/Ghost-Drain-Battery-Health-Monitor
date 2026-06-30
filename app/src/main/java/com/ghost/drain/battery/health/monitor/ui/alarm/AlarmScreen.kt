package com.ghost.drain.battery.health.monitor.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.service.BatteryMonitorService
import com.ghost.drain.battery.health.monitor.service.OemBatteryOptimizationHelper
import com.ghost.drain.battery.health.monitor.ui.theme.*

@Composable
fun AlarmScreen(
    onBack: () -> Unit,
    viewModel: AlarmViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.recheckOptimizationStatus()
        val fromNotif = activity?.intent
            ?.getBooleanExtra(BatteryMonitorService.EXTRA_OPEN_ALARM_SCREEN, false) == true
        if (fromNotif) {
            activity?.intent?.removeExtra(BatteryMonitorService.EXTRA_OPEN_ALARM_SCREEN)
            OemBatteryOptimizationHelper.requestExemption(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.recheckOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) viewModel.setSoundUri(uri.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                "Alarm Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // ── Streak card ────────────────────────────────────────────────────────
        // Show if user has ever had a streak OR completed the challenge
        if (state.streakCount > 0 || state.challengeCompleted) {
            StreakCard(
                streak            = state.streakCount,
                longestStreak     = state.longestStreak,
                targetPercent     = state.streakTargetPercent,
                challengeComplete = state.challengeCompleted,
                onTargetChange    = viewModel::setStreakTarget
            )
        }

        // ── OEM battery optimization banner ───────────────────────────────────
        AnimatedVisibility(visible = state.showOemBanner) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261010)),
                border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(RedPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield),
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "⚠ Battery Alerts Disabled",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RedPrimary
                        )
                        Text(
                            "Low battery warnings won't appear",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                    Surface(
                        onClick = { OemBatteryOptimizationHelper.requestExemption(context) },
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Fix Now",
                            color = RedPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ── High alarm ─────────────────────────────────────────────────────────
        AlarmSliderSection(
            title         = "Unplug Alarm",
            subtitle      = "Notifies when charge reaches the set level",
            infoText      = "Saves battery health by avoiding overcharge.",
            iconRes       = R.drawable.ic_plug,
            enabled       = state.highEnabled,
            onToggle      = viewModel::setHighEnabled,
            value         = state.highPercent,
            valueRange    = 50..100,
            unit          = "%",
            onValueChange = viewModel::setHighPercent,
            accentColor   = GreenPrimary
        )

        // ── Low alarm ──────────────────────────────────────────────────────────
        AlarmSliderSection(
            title         = "Low Battery Alarm",
            subtitle      = "Alerts before battery gets too low",
            infoText      = "Get warned in time to keep your day going.",
            iconRes       = R.drawable.ic_battery_warning,
            enabled       = state.lowEnabled,
            onToggle      = viewModel::setLowEnabled,
            value         = state.lowPercent,
            valueRange    = 5..30,
            unit          = "%",
            onValueChange = viewModel::setLowPercent,
            accentColor   = AmberPrimary
        )

        // ── Overheat alarm ─────────────────────────────────────────────────────
        AlarmSliderSection(
            title         = "Overheat Alarm",
            subtitle      = "Alerts when temperature exceeds limit",
            infoText      = "Helps prevent overheating and battery damage.",
            iconRes       = R.drawable.ic_thermometer,
            enabled       = state.overheatEnabled,
            onToggle      = viewModel::setOverheatEnabled,
            value         = state.overheatThreshold,
            valueRange    = 38..55,
            unit          = "°C",
            onValueChange = viewModel::setOverheatThreshold,
            accentColor   = RedPrimary
        )

        // ── DEBUG ONLY — remove before release ────────────────────────────────────
//        DebugStreakControls(viewModel)

        // ── Auto Save Footer ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Your settings are saved automatically",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Reusable Component: Icon Box ───────────────────────────────────────────────

@Composable
private fun IconBox(@DrawableRes iconRes: Int, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Reusable Component: Alarm Slider Card ──────────────────────────────────────

@Composable
private fun AlarmSliderSection(
    title: String,
    subtitle: String,
    infoText: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    value: Int,
    valueRange: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderStandard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Top Row: Icon, Text, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBox(iconRes = iconRes, tint = accentColor)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accentColor,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = BorderStandard,
                        uncheckedThumbColor = TextSecondary
                    )
                )
            }

            // Middle: Giant Value & Slider
            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Large Number Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = value.toString(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            color = accentColor,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = unit,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = accentColor,
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Slider Control
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onValueChange(it.toInt()) },
                        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = BorderStandard
                        )
                    )

                    // Min/Max Labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${valueRange.first}$unit", fontSize = 11.sp, color = TextMuted)
                        Text("${valueRange.last}$unit", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderStandard, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom: Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(infoText, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

// ── Streak Card ────────────────────────────────────────────────────────────────

@Composable
private fun StreakCard(
    streak:            Int,
    longestStreak:     Int,
    targetPercent:     Int,
    challengeComplete: Boolean,
    onTargetChange:    (Int) -> Unit
) {
    val milestoneLabel: String? = when (streak) {
        1    -> "Starter"
        3    -> "Building Habit"
        7    -> "One Week Complete"
        14   -> "Halfway to Mastery"
        21   -> "Battery Guardian"
        25   -> "Final Stretch"
        29   -> "One Day Remaining"
        30   -> "🏆 Battery Guardian"
        else -> null
    }

    val accentColor = if (challengeComplete) Color(0xFFFFD700) else GreenPrimary
    val bgColor     = if (challengeComplete) Color(0xFF1A1500) else SurfaceCard
    val borderColor = if (challengeComplete) Color(0xFFFFD700).copy(alpha = 0.4f)
    else GreenPrimary.copy(alpha = 0.3f)

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (challengeComplete) "🏆" else "🔥", fontSize = 28.sp)
                    Column {
                        Text(
                            if (challengeComplete && streak == 0)
                                "Challenge Complete!"
                            else if (streak == 1) "1-Day Streak"
                            else "$streak-Day Streak",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            "Unplugged near $targetPercent% · ${streak} day${if (streak == 1) "" else "s"} in a row",
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
                // Best streak badge — only show when it differs from current
                if (longestStreak > streak) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            "$longestStreak",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text("best", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            // ── Milestone badge ─────────────────────────────────────────────
            if (milestoneLabel != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "✦ $milestoneLabel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // ── Progress bar toward Day 30 ──────────────────────────────────
            if (!challengeComplete) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "30-day challenge progress",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            "$streak / 30",
                            fontSize = 11.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (streak / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = GreenPrimary,
                        trackColor = BorderStandard
                    )
                }
            } else {
                // Permanent achievement row after Day 30
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🏅", fontSize = 16.sp)
                        Text(
                            "Battery Guardian badge unlocked",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderStandard, thickness = 1.dp)

            // ── Target % slider ─────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Unplug target",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Success zone: ${targetPercent - 2}%–${targetPercent + 4}%",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        "$targetPercent%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                }
                Slider(
                    value = targetPercent.toFloat(),
                    onValueChange = { onTargetChange(it.toInt()) },
                    valueRange = 70f..95f,
                    steps = 4,  // 70, 75, 80, 85, 90, 95
                    colors = SliderDefaults.colors(
                        thumbColor = GreenPrimary,
                        activeTrackColor = GreenPrimary,
                        inactiveTrackColor = BorderStandard
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("70%", fontSize = 11.sp, color = TextMuted)
                    Text("95%", fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }
}

//@Composable
//private fun DebugStreakControls(viewModel: AlarmViewModel) {
//    Card(
//        shape = RoundedCornerShape(12.dp),
//        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A00)),
//        border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.4f))
//    ) {
//        Column(
//            modifier = Modifier.padding(12.dp),
//            verticalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            Text(
//                "🐛 Debug — Streak Controls",
//                fontSize = 12.sp,
//                fontWeight = FontWeight.Bold,
//                color = Color(0xFFFF6B00)
//            )
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(8.dp),
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Button(
//                    onClick = { viewModel.debugSetStreak(1) },
//                    modifier = Modifier.weight(1f),
//                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
//                ) { Text("1 Day", fontSize = 11.sp) }
//
//                Button(
//                    onClick = { viewModel.debugSetStreak(7) },
//                    modifier = Modifier.weight(1f),
//                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
//                ) { Text("7 Days", fontSize = 11.sp) }
//
//                Button(
//                    onClick = { viewModel.debugSetStreak(21) },
//                    modifier = Modifier.weight(1f),
//                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
//                ) { Text("21 Days", fontSize = 11.sp) }
//
//                Button(
//                    onClick = { viewModel.debugSetStreak(30) },
//                    modifier = Modifier.weight(1f),
//                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
//                ) { Text("30 Days 🏆", fontSize = 11.sp) }
//            }
//            OutlinedButton(
//                onClick = { viewModel.debugResetStreak() },
//                modifier = Modifier.fillMaxWidth(),
//                border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f))
//            ) {
//                Text("Reset Streak", fontSize = 11.sp, color = Color(0xFFFF3B30))
//            }
//        }
//    }
//}