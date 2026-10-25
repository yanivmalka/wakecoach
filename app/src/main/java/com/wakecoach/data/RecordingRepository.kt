package com.wakecoach.data

import android.content.Context
import android.net.Uri
import com.wakecoach.audio.Recorder
import java.io.File

class RecordingRepository(private val context: Context, private val dao: RecordingDao) {
    val all = dao.observeAll()

    suspend fun add(file: File, durationMs: Long, owner: String, purpose: RecordingPurpose): Long =
        dao.insert(RecordingEntity(path = file.absolutePath, owner = owner.trim(), purpose = purpose.name,
            durationMs = durationMs, createdAt = System.currentTimeMillis()))

    suspend fun import(uri: Uri, owner: String, purpose: RecordingPurpose): Boolean {
        val (file, duration) = Recorder.import(context, uri) ?: return false
        add(file, duration, owner, purpose)
        return true
    }

    suspend fun delete(r: RecordingEntity) {
        File(r.path).delete()
        dao.delete(r)
    }

    /** A clip to play over a ringing wake-up; skips files that went missing. */
    suspend fun randomForMorning(): RecordingEntity? = dao.all().filter { File(it.path).exists() }.randomOrNull()
}
