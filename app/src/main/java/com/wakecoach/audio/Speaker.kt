package com.wakecoach.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads a line aloud once the TTS engine is ready. Call [shutdown] when done. */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.getDefault()
                pending?.let(::speak)
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wakecoach")
    }

    fun shutdown() = tts.shutdown()
}
