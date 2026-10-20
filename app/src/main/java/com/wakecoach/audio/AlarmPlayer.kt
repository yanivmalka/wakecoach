package com.wakecoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.wakecoach.core.model.Profile

/** Loops the alarm sound with a volume ramp from near-silent to full over the profile's crescendo. */
class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ramp: Runnable? = null

    fun start(profile: Profile) = handler.post {
        stopNow()
        // Try the user's alarm tone first; an alarm that silently fails is the worst outcome.
        player = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .firstNotNullOfOrNull { type ->
                RingtoneManager.getActualDefaultRingtoneUri(context, type)?.let { uri -> runCatching { create(uri) }.getOrNull() }
            }
        startRamp(profile.crescendoSeconds)
        if (profile.vibrate || player == null) {
            vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
        }
    }

    private fun create(uri: android.net.Uri): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            setVolume(MIN_VOLUME, MIN_VOLUME)
            prepare()
            start()
        }

    fun stop() = handler.post { stopNow() }

    private fun startRamp(seconds: Int) {
        val steps = (seconds.coerceAtLeast(1) * 2)
        var step = 0
        ramp = object : Runnable {
            override fun run() {
                step++
                val v = (MIN_VOLUME + (1f - MIN_VOLUME) * step / steps).coerceAtMost(1f)
                player?.setVolume(v, v)
                if (step < steps) handler.postDelayed(this, 500)
            }
        }.also { handler.postDelayed(it, 500) }
    }

    private fun stopNow() {
        ramp?.let(handler::removeCallbacks)
        ramp = null
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator().cancel()
    }

    @Suppress("DEPRECATION") // VibratorManager is API 31+; this still works there.
    private fun vibrator(): Vibrator = context.getSystemService(Vibrator::class.java)

    private companion object {
        const val MIN_VOLUME = 0.05f
    }
}
