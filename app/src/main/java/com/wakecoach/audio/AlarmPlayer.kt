package com.wakecoach.audio

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.wakecoach.R
import com.wakecoach.core.audio.BuiltinSound
import com.wakecoach.core.audio.SoundPlan
import com.wakecoach.core.audio.SoundRef
import com.wakecoach.core.model.Profile

/**
 * Plays a [SoundPlan]: the first stage ramps up over the profile's crescendo, later stages
 * take over at full volume if nobody has reacted. Every step falls back to the phone's own
 * alarm/ringtone, because an alarm that silently fails is the worst outcome.
 */
class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Runnable>()
    /** Volume the ramp/stage wants; ducking scales it down temporarily. */
    private var targetVolume = MIN_VOLUME
    private var ducked = false

    fun start(profile: Profile, plan: SoundPlan) = handler.post {
        stopNow()
        val first = plan.stages.first()
        play(first.sound, MIN_VOLUME)
        rampTo(1f, profile.crescendoSeconds)
        vibrate(strong = false, force = profile.vibrate || player == null)

        plan.stages.drop(1).forEach { stage ->
            later(stage.startAfterSec * 1000L) {
                play(stage.sound, 1f)
                targetVolume = 1f
                applyVolume()
                vibrate(strong = true, force = true)
            }
        }
    }

    fun stop() = handler.post { stopNow() }

    /** Lowers the alarm while a voice line or recording plays over it. */
    fun duck(on: Boolean) = handler.post {
        ducked = on
        applyVolume()
    }

    private fun play(sound: SoundRef, volume: Float) {
        player?.run { runCatching { stop() }; release() }
        targetVolume = volume
        player = candidates(sound).firstNotNullOfOrNull { uri -> runCatching { create(uri) }.getOrNull() }
        applyVolume()
    }

    private fun candidates(sound: SoundRef): List<Uri> {
        val system = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .mapNotNull { RingtoneManager.getActualDefaultRingtoneUri(context, it) }
        val own = when (sound) {
            is SoundRef.Custom -> Uri.parse(sound.uri)
            is SoundRef.Builtin -> when (sound.sound) {
                BuiltinSound.PHONE_RINGTONE -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                else -> rawUri(context, sound.sound)
            }
        }
        return listOfNotNull(own) + system
    }

    private fun create(uri: Uri): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            setVolume(0f, 0f)
            prepare()
            start()
        }

    private fun rampTo(end: Float, seconds: Int) {
        val steps = seconds.coerceAtLeast(1) * 2
        val start = targetVolume
        for (step in 1..steps) {
            later(step * 500L) {
                // A later stage may already have jumped to full volume; never ramp back down.
                targetVolume = maxOf(targetVolume, start + (end - start) * step / steps)
                applyVolume()
            }
        }
    }

    private fun applyVolume() {
        val v = if (ducked) targetVolume * DUCK else targetVolume
        player?.setVolume(v, v)
    }

    private fun vibrate(strong: Boolean, force: Boolean) {
        if (!force) return
        val pattern = if (strong) longArrayOf(0, 1000, 300) else longArrayOf(0, 800, 700)
        vibrator().vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun later(delayMs: Long, block: () -> Unit) {
        val r = Runnable(block)
        pending += r
        handler.postDelayed(r, delayMs)
    }

    private fun stopNow() {
        pending.forEach(handler::removeCallbacks)
        pending.clear()
        player?.run { runCatching { stop() }; release() }
        player = null
        ducked = false
        targetVolume = MIN_VOLUME
        vibrator().cancel()
    }

    @Suppress("DEPRECATION") // VibratorManager is API 31+; this still works there.
    private fun vibrator(): Vibrator = context.getSystemService(Vibrator::class.java)

    companion object {
        private const val MIN_VOLUME = 0.05f
        private const val DUCK = 0.25f

        fun rawUri(context: Context, sound: BuiltinSound): Uri? {
            val res = when (sound) {
                BuiltinSound.CHIME_SOFT -> R.raw.chime_soft
                BuiltinSound.BIRDS_MORNING -> R.raw.birds_morning
                BuiltinSound.RISE_BRIGHT -> R.raw.rise_bright
                BuiltinSound.URGENT_BEEP -> R.raw.urgent_beep
                BuiltinSound.PHONE_RINGTONE -> return null
            }
            return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$res")
        }
    }
}
