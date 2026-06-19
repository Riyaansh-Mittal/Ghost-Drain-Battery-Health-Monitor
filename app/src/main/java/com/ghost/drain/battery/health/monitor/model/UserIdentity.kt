package com.ghost.drain.battery.health.monitor.model

enum class UserIdentity(val key: String, val displayName: String) {
    GENERAL("general", "Protect my phone's battery"),
    GAMER("gamer", "Stop thermal lag while gaming"),
    DRIVER("driver", "Keep my phone alive while driving"),
    POWER_USER("power_user", "Show me all the technical data");

    companion object {
        fun fromKey(key: String): UserIdentity =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}