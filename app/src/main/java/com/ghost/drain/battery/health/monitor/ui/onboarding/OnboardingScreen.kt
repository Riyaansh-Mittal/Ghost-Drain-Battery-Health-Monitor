package com.ghost.drain.battery.health.monitor.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.drain.battery.health.monitor.R
import com.ghost.drain.battery.health.monitor.data.UserIdentity
import com.ghost.drain.battery.health.monitor.ui.theme.*

// ── Per-identity design tokens ────────────────────────────────────────────────
// These identity accent colors are intentionally local to onboarding —
// they are visual differentiators for the selector UI only and are not
// part of the app's semantic color system. The home screen uses GreenPrimary
// from Color.kt, not these onboarding-specific shades.
private data class IdentityStyle(
    val iconRes: Int,
    val accentColor: Color
)

@Composable
private fun identityStyle(identity: UserIdentity): IdentityStyle = when (identity) {
    UserIdentity.GENERAL -> IdentityStyle(
        iconRes     = R.drawable.ic_battery,
        accentColor = Color(0xFF4ADE80) // Vibrant Green
    )
    UserIdentity.GAMER -> IdentityStyle(
        iconRes     = R.drawable.ic_controller,
        accentColor = Color(0xFFC084FC) // Vibrant Purple
    )
    UserIdentity.GIG_DRIVER -> IdentityStyle(
        iconRes     = R.drawable.ic_car,
        accentColor = Color(0xFFFB923C) // Vibrant Orange
    )
    UserIdentity.POWER_USER -> IdentityStyle(
        iconRes     = R.drawable.ic_settings,
        accentColor = Color(0xFF60A5FA) // Vibrant Blue
    )
}

// ── Screen entry point ────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingContent(
        onSelectIdentity = { identity ->
            viewModel.confirmSelection(identity, onComplete)
        }
    )
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingContent(
    onSelectIdentity: (UserIdentity) -> Unit
) {
    val backgroundColor = Color(0xFF090B0F) // Deep space dark background

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // We capture the exact available screen height here
        val minScreenHeight = maxHeight

        // ── Ambient background glow — stays pinned to top ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1B5E20).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        // ── Scrollable Wrapper ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Main UI Layout ─────────────────────────────────────────────────
            // By setting minimum height to the exact screen height, we can safely
            // use .weight() inside. If content fits, weight stretches perfectly
            // (0 scroll). If content overflows, it unlocks scrolling.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minScreenHeight)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Using heightIn ensures that if the screen shrinks and weights collapse,
                // the elements won't squish against each other completely.
                Spacer(modifier = Modifier.heightIn(min = 24.dp).weight(0.15f))

                // ── Header icon with strong glow ───────────────────────────────
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50).copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_battery), // Assuming this is your outline battery icon
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Titles ─────────────────────────────────────────────────────
                Text(
                    text = "What matters most\nto you right now?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We'll personalize your experience\nto match your goal.",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9CA3AF)
                )

                Spacer(modifier = Modifier.heightIn(min = 32.dp).weight(0.6f))

                // ── Identity cards ─────────────────────────────────────────────
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UserIdentity.entries.forEach { identity ->
                        IdentityCard(
                            identity = identity,
                            onSelect = { onSelectIdentity(identity) }
                        )
                    }
                }

                // Expanded space to push the pill downward
                Spacer(modifier = Modifier.heightIn(min = 32.dp).weight(1.2f))

                // ── Bottom Pill ────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xFF14171C),
                    border = BorderStroke(1.dp, Color(0xFF22262E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_reload), // Assuming you have a reload/refresh icon
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("You can change this anytime in ")
                                withStyle(SpanStyle(color = Color(0xFF4ADE80))) {
                                    append("Settings")
                                }
                                append(".")
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                // Bottom margin
                Spacer(modifier = Modifier.heightIn(min = 24.dp).weight(0.2f))
            }
        }
    }
}

// ── Identity card ─────────────────────────────────────────────────────────────

@Composable
private fun IdentityCard(
    identity: UserIdentity,
    onSelect: () -> Unit
) {
    val style = identityStyle(identity)

    // Subtle background gradient that fades out from the left
    val cardBackgroundBrush = Brush.horizontalGradient(
        colors = listOf(
            style.accentColor.copy(alpha = 0.08f),
            Color(0xFF121418)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, style.accentColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackgroundBrush)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coloured circle icon background
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = style.accentColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(style.iconRes),
                    contentDescription = null,
                    tint = style.accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = identity.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = identity.subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF8B929D)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Standard Right Chevron
            Text(
                text = "›",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF090B0F, heightDp = 852, widthDp = 393)
@Preview(showBackground = true, backgroundColor = 0xFF090B0F, heightDp = 600, widthDp = 360, name = "Small Screen Scroll Test")
@Composable
private fun OnboardingPreview() {
    BatteryHealthMonitorTheme {
        OnboardingContent(
            onSelectIdentity = {}
        )
    }
}