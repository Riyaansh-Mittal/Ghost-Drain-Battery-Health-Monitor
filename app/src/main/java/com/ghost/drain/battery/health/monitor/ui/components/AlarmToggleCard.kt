package com.ghost.drain.battery.health.monitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.drain.battery.health.monitor.ui.theme.*

/**
 * The big alarm hero card on Screen 1 Home Dashboard.
 *
 * Tapping the card body navigates to Alarm screen.
 * Tapping the toggle switches alarm on/off without navigating.
 */
@Composable
fun AlarmToggleCard(
    title: String,           // "Unplug alarm at 80%"
    subtitle: String,        // "Rings every 60s until unplugged"
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isEnabled) GreenGlow else Color(0xFF333333)
    val backgroundColor = if (isEnabled) SurfaceCard else Surface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bell icon placeholder — replace with your icon resource
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isEnabled) GreenDark else Color(0xFF1A1A1A)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "🔔",
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = BatteryTypography.titleLarge.copy(
                        color = if (isEnabled) TextPrimary else TextSecondary
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = BatteryTypography.bodyMedium
                )
            }

            // Toggle — stops click propagation to card
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = GreenPrimary,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AlarmToggleCardPreview() {
    BatteryHealthMonitorTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var highEnabled by remember { mutableStateOf(true) }
            var lowEnabled  by remember { mutableStateOf(true) }

            AlarmToggleCard(
                title     = "Unplug alarm at 80%",
                subtitle  = "Rings every 60s until unplugged",
                isEnabled = highEnabled,
                onToggle  = { highEnabled = it },
                onCardClick = {}
            )
            AlarmToggleCard(
                title     = "Low battery alarm at 20%",
                subtitle  = "Alerts before deep discharge",
                isEnabled = lowEnabled,
                onToggle  = { lowEnabled = it },
                onCardClick = {}
            )
        }
    }
}