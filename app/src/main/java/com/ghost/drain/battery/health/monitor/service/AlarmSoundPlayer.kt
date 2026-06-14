package com.ghost.drain.battery.health.monitor.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.ghost.drain.battery.health.monitor.R

class AlarmSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    // alarmType: "high" or "low" — different default tones
    fun start(alarmType: String, vibrate: Boolean) {
        stop()
        val rawRes = if (alarmType == "high") R.raw.alarm_high else R.raw.alarm_low

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context.resources.openRawResourceFd(rawRes))
            isLooping = false  // Service controls repeat interval, not MediaPlayer
            prepare()
            start()
        }

        if (vibrate) vibrate()
    }

    fun stop() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService<VibratorManager>()
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService<Vibrator>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(pattern, -1)
            }
        }
    }
}