package com.wakecoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Reads lines aloud on the alarm stream, so it is heard even with media volume at zero.
 * [onSpeaking] reports start/end so the alarm can duck under the voice. Call [shutdown] when done.
 */
class Speaker(context: Context, private val onSpeaking: (Boolean) -> Unit = {}) {
    private var ready = false
    private val pending = mutableListOf<String>()
    private val main = Handler(Looper.getMainLooper())
    /** Utterances started and not yet finished; the duck lasts while this is above zero. */
    private var active = 0
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) return@TextToSpeech
            tts.language = Locale.getDefault()
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    main.post { if (active++ == 0) onSpeaking(true) }
                }
                override fun onDone(id: String?) = finished()
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = finished()
                override fun onError(id: String?, errorCode: Int) = finished()
                override fun onStop(id: String?, interrupted: Boolean) = finished()
            })
            pending.forEach(::speak)
            pending.clear()
        }
    }

    private fun finished() {
        // Only the last finished line ends the duck; counting avoids racing isSpeaking().
        main.post { if (active > 0 && --active == 0) onSpeaking(false) }
    }

    /** Queues [text] after anything already being read. */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending += text; return }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "wakecoach-${text.hashCode()}")
    }

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
        if (active > 0) { active = 0; onSpeaking(false) }
    }
}
