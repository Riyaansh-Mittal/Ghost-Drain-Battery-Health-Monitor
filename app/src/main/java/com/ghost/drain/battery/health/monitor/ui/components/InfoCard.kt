package com.ghost.drain.battery.health.monitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghost.drain.battery.health.monitor.ui.theme.*

/**
 * Standard dark card used across all screens.
 *
 * [accentColor] drives both the border color and is available to content.
 * Pass null for a borderless neutral card.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceCard,
    accentColor: Color? = BorderStandard,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (accentColor != null) {
        BorderStroke(borderWidth, accentColor)
    } else null

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

// ── Semantic convenience wrappers ─────────────────────────────────────────────

@Composable
fun GreenCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    InfoCard(modifier = modifier, backgroundColor = SurfaceCard, accentColor = GreenPrimary, content = content)

@Composable
fun AmberCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    InfoCard(modifier = modifier, backgroundColor = SurfaceCardAlt, accentColor = AmberPrimary, content = content)

@Composable
fun RedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    InfoCard(modifier = modifier, backgroundColor = SurfaceCardAlt, accentColor = RedPrimary, content = content)

@Composable
fun NeutralCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = InfoCard(
    modifier = modifier,
    backgroundColor = SurfaceCard,
    accentColor = null,
    borderWidth = 1.dp,
    content = content
)

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun InfoCardPreview() {
    BatteryHealthMonitorTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GreenCard {
                Text("Green card", style = BatteryTypography.titleLarge)
                Text("Border matches green accent", style = BatteryTypography.bodyMedium)
            }
            AmberCard {
                Text("Amber card", style = BatteryTypography.titleLarge)
                Text("Low alarm, overheating warning", style = BatteryTypography.bodyMedium)
            }
            RedCard {
                Text("Red card", style = BatteryTypography.titleLarge)
                Text("Danger / counterfeit alert", style = BatteryTypography.bodyMedium)
            }
            NeutralCard {
                Text("Neutral card", style = BatteryTypography.titleLarge)
                Text("Session summary, analytics panels", style = BatteryTypography.bodyMedium)
            }
        }
    }
}