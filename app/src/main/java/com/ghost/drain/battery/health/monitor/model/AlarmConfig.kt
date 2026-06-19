package com.ghost.drain.battery.health.monitor.model

data class AlarmConfig(
    val highAlarmEnabled: Boolean = true,
    val highThreshold: Int = 80,           // percent, 70–95
    val lowAlarmEnabled: Boolean = true,
    val lowThreshold: Int = 20,            // percent, 10–30
    val overheatAlarmEnabled: Boolean = true,
    val overheatThresholdCelsius: Int = 42,
    val alarmSoundName: String = "chime_rise", // key matches res/raw filename
    val alarmRepeatIntervalSeconds: Int = 60
)