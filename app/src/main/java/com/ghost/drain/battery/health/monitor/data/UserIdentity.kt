package com.ghost.drain.battery.health.monitor.data

enum class UserIdentity(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String
) {
    GENERAL(
        key      = "general",
        title    = "Protect my phone's battery",
        subtitle = "For overnight chargers, budget phone keepers, and everyday users.",
        detail   = "Standard layout · 80% alarm · 20% low alert"
    ),
    GAMER(
        key      = "gamer",
        title    = "Stop thermal lag while gaming",
        subtitle = "For mobile gamers (Free Fire, BGMI, MLBB, Genshin, and more).",
        detail   = "Thermal card on top · Session scorecard · Overlay shortcut"
    ),
    GIG_DRIVER(
        key      = "gig_driver",
        title    = "Keep my phone alive while driving",
        subtitle = "For gig drivers (Uber, Grab, Gojek, Ola, Rappi, DoorDash, and more).",
        detail   = "Crisis Mode · Net drain on top · Driver Mode quick-launch"
    ),
    POWER_USER(
        key      = "power_user",
        title    = "Show me all the technical data",
        subtitle = "For power users, XDA crowd, and ROM developers.",
        detail   = "mA + voltage prominent · Deep sleep % · Copy stats for Reddit"
    );

    companion object {
        fun fromKey(key: String): UserIdentity =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}