package com.ghost.drain.battery.health.monitor.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neutral Foundation (High Depth) ──────────────────────────────────────────
val Black          = Color(0xFF000000)
val Surface        = Color(0xFF070708) // Deep, rich background
val SurfaceCard    = Color(0xFF121214) // Slightly lifted for card separation
val SurfaceCardAlt = Color(0xFF161512) // Subtle warmth for warning cards

// ── Functional Accents (The 10%) ─────────────────────────────────────────────
// These are balanced "Electric" colors. They are highly legible,
// saturated enough to be professional, but not blindingly bright.
val GreenPrimary   = Color(0xFFADFF2F) // A "Green-Yellow" (Optically balanced)
val GreenMuted     = Color(0xFF32CD32) // A stable Lime for secondary indicators
val AmberPrimary   = Color(0xFFFFB300) // Professional "Warning" Amber
val RedPrimary     = Color(0xFFFF5252) // A softer, less aggressive Red

// ── Text Hierarchy ───────────────────────────────────────────────────────────
val TextPrimary    = Color(0xFFE0E0E0) // Soft white (reduces glare vs pure white)
val TextSecondary  = Color(0xA3E0E0E0) // 64% Opacity for secondary data
val TextMuted      = Color(0x61E0E0E0) // 38% Opacity for labels/helper text

// ── System / Utility ─────────────────────────────────────────────────────────
val BorderStandard = Color(0xFF2C2C2E) // Subtle borders for card definition