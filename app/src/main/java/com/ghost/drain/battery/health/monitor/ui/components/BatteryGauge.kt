package com.ghost.drain.battery.health.monitor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.drain.battery.health.monitor.ui.theme.*

@Composable
fun BatteryGauge(
    percent: Int,
    temperatureCelsius: Float = 25f,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 14.dp,
    animateChanges: Boolean = true
) {
    val isOverheat = temperatureCelsius > 42f
    val arcColor = gaugeColor(percent)

    val targetSweep = (percent.coerceIn(0, 100) / 100f) * 270f
    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge_sweep"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "overheat_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx  = strokeWidth.toPx()
            val padding   = strokePx / 2f + 4.dp.toPx()
            val arcSize   = Size(
                this.size.width  - padding * 2,
                this.size.height - padding * 2
            )
            val topLeft = Offset(padding, padding)
            val startAngle = 135f

            // Track
            drawArc(
                color      = GaugeTrack,
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Filled arc
            if (animatedSweep > 0f) {
                drawArc(
                    color      = arcColor,
                    startAngle = startAngle,
                    sweepAngle = animatedSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Overheat glow
            if (isOverheat) {
                val glowStroke  = strokePx + 8.dp.toPx()
                val glowPadding = glowStroke / 2f
                drawArc(
                    color      = RedPrimary.copy(alpha = pulseAlpha),
                    startAngle = startAngle,
                    sweepAngle = 270f,
                    useCenter  = false,
                    topLeft    = Offset(glowPadding, glowPadding),
                    size       = Size(
                        this.size.width  - glowPadding * 2,
                        this.size.height - glowPadding * 2
                    ),
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
            }
        }

        // ── Center text — number + % inline, then label ──────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Number and % sign on the same baseline
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text  = "$percent",
                    color = arcColor,                 // number matches arc color
                    fontWeight = FontWeight.Bold,
                    fontSize   = (size.value * 0.28f).sp  // scales with gauge size
                )
                Text(
                    text  = "%",
                    color = arcColor.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = (size.value * 0.13f).sp,
                    modifier   = Modifier.padding(bottom = (size.value * 0.04f).dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "Battery",
                color    = TextMuted,
                fontSize = (size.value * 0.10f).sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

private fun gaugeColor(percent: Int): Color = when {
    percent < 10  -> GaugeRed
    percent < 15  -> GaugeAmber
    percent <= 80 -> GaugeGreen
    percent <= 95 -> GaugeAmber
    else          -> GaugeRed
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BatteryGaugePreview() {
    BatteryHealthMonitorTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryGauge(percent = 68, temperatureCelsius = 31f, size = 140.dp)
            BatteryGauge(percent = 82, temperatureCelsius = 38f, size = 140.dp)
            BatteryGauge(percent = 8,  temperatureCelsius = 44f, size = 140.dp)
        }
    }
}