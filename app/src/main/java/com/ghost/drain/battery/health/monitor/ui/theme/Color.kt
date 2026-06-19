package com.ghost.drain.battery.health.monitor.ui.theme

import androidx.compose.ui.graphics.Color

// ── Backgrounds ───────────────────────────────────────────────────────────────
val Black         = Color(0xFF000000)   // true black — all screen backgrounds
val Surface       = Color(0xFF0F0F0F)   // slightly lifted surface
val SurfaceCard   = Color(0xFF1A1A1A)   // card background (green-bordered cards)
val SurfaceCardAlt= Color(0xFF1C1208)   // warm dark — amber/orange tinted cards
val SurfaceRed    = Color(0xFF1C0A0A)   // red tinted — overheat / danger cards
val SurfaceGhost  = Color(0xFF1A1500)   // ghost drain banner background

// ── Green — primary accent ────────────────────────────────────────────────────
val GreenPrimary  = Color(0xFF4CAF50)   // toggles ON, gauge arc, health bar
val GreenBright   = Color(0xFF69F069)   // large % numbers (68%, 88%)
val GreenGlow     = Color(0xFF2E7D32)   // card borders, dim accents
val GreenDark     = Color(0xFF1B3E1D)   // green card border fill

// ── Amber — warning ───────────────────────────────────────────────────────────
val AmberPrimary  = Color(0xFFFFA726)   // low alarm slider, 20% number, streak icon
val AmberDark     = Color(0xFF4A2800)   // amber card surface tint
val AmberBorder   = Color(0xFF7A4500)   // amber card border

// ── Red — danger ──────────────────────────────────────────────────────────────
val RedPrimary    = Color(0xFFEF5350)   // overheat alert, over-limit bars
val RedDark       = Color(0xFF4A0A0A)   // red card surface
val RedBorder     = Color(0xFF7A1A1A)   // red card border

// ── Purple — deep sleep / gamer ───────────────────────────────────────────────
val PurplePrimary = Color(0xFF9C27B0)
val PurpleLight   = Color(0xFFCE93D8)
val PurpleSurface = Color(0xFF1A0A1E)

// ── Chart colors ──────────────────────────────────────────────────────────────
val ChartTeal     = Color(0xFF26A69A)   // live current line
val ChartAmber    = Color(0xFFFFCA28)   // counterfeit zone line
val ChartRed      = Color(0xFFEF5350)   // over-limit bars

// ── Text ──────────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextMuted     = Color(0xFF707070)
val TextHint      = Color(0xFF444444)

// ── Gauge arc colors (by battery %) ───────────────────────────────────────────
val GaugeGreen    = Color(0xFF4CAF50)   // 15–80%
val GaugeAmber    = Color(0xFFFFA726)   // 10–15% and 80–95%
val GaugeRed      = Color(0xFFEF5350)   // below 10% or above 95%
val GaugeTrack    = Color(0xFF2A2A2A)   // unfilled arc track