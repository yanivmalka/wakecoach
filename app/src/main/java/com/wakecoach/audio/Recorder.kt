package com.wakecoach.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/** Records short voice clips (AAC in .m4a) into filesDir/recordings. Needs RECORD_AUDIO. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start() {
        stopAndDiscard()
        val out = File(dir(context), "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            // Microphone busy or unavailable: release it and leave no empty file behind.
            r.release()
            out.delete()
            throw e
        }
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
    }

    /** Returns the finished clip and its length, or null if it was too short to keep. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val out = file
        val length = System.currentTimeMillis() - startedAt
        recorder = null
        file = null
        // stop() throws if nothing was captured (a tap-tap); treat that as a discarded clip.
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || out == null || length < 600) { out?.delete(); return null }
        return out to length
    }

    fun stopAndDiscard() {
        stop()?.first?.delete()
    }

    companion object {
        fun dir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

        /** Copies an audio file the user picked into app storage; returns it with its duration. */
        fun import(context: Context, uri: Uri): Pair<File, Long>? = runCatching {
            val out = File(dir(context), "imp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            val duration = MediaMetadataRetriever().run {
                try {
                    setDataSource(out.absolutePath)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    release()
                }
            }
            if (duration <= 0) { out.delete(); null } else out to duration
        }.getOrNull()
    }
}
