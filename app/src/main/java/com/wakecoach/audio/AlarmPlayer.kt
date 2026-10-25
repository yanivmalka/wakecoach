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
import android.provider.Settings
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
    /** Voices currently speaking over the alarm (TTS, a recording); ducked while > 0. */
    private var duckers = 0
    private var overlayPlayer: MediaPlayer? = null

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

    /**
     * Plays a personal clip once over the ringing alarm after [delayMs], ducking the alarm
     * while it speaks. Ignored if the alarm was stopped in the meantime.
     */
    fun overlay(file: java.io.File, delayMs: Long) = handler.post { later(delayMs) { playOverlay(file) } }

    private fun playOverlay(file: java.io.File) {
        if (player == null) return
        val voice = MediaPlayer()
        try {
            voice.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            voice.setDataSource(file.absolutePath)
            voice.prepare()
        } catch (e: Exception) {
            voice.release()
            return
        }
        overlayPlayer = voice
        duckers++
        applyVolume()
        val done = { mp: MediaPlayer ->
            if (overlayPlayer === mp) {
                overlayPlayer = null
                mp.release()
                duckers = (duckers - 1).coerceAtLeast(0)
                applyVolume()
            }
        }
        voice.setOnCompletionListener { done(it) }
        voice.setOnErrorListener { mp, _, _ -> done(mp); true }
        voice.start()
    }

    /** Lowers the alarm while a voice line or recording plays over it. */
    fun duck(on: Boolean) = handler.post {
        duckers = (duckers + if (on) 1 else -1).coerceAtLeast(0)
        applyVolume()
    }

    /** Switches to [sound]; keeps the current sound if the new one cannot be opened. */
    private fun play(sound: SoundRef, volume: Float) {
        val next = candidates(sound).firstNotNullOfOrNull(::create) ?: return
        player?.run { runCatching { stop() }; release() }
        player = next
        targetVolume = volume
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
        // Settings.System defaults resolve through the system's readable cache even when the
        // underlying file is not readable by us; the bundled beep is always readable.
        val last = listOfNotNull(
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            Settings.System.DEFAULT_RINGTONE_URI,
            rawUri(context, BuiltinSound.URGENT_BEEP),
        )
        return (listOfNotNull(own) + system + last).distinct()
    }

    /** An opened, playing player for [uri], or null; never leaks a half-set-up player. */
    private fun create(uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.prepare()
            // If playback dies mid-ring (media server restart, file gone), fall back to the beep.
            mp.setOnErrorListener { failed, _, _ ->
                handler.post {
                    if (player === failed) {
                        player = null
                        failed.release()
                        play(SoundRef.Builtin(BuiltinSound.URGENT_BEEP), maxOf(targetVolume, 0.5f))
                    }
                }
                true
            }
            mp.start()
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
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
        val v = if (duckers > 0) targetVolume * DUCK else targetVolume
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
        overlayPlayer?.run { runCatching { stop() }; release() }
        overlayPlayer = null
        duckers = 0
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
