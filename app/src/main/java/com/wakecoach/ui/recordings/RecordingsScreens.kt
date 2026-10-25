package com.wakecoach.ui.recordings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecoach.R
import com.wakecoach.audio.Recorder
import com.wakecoach.data.RecordingEntity
import com.wakecoach.data.RecordingPurpose
import com.wakecoach.data.RecordingRepository
import com.wakecoach.ui.theme.BigTouch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** List of personal clips, with record / import, and the entry to the guided interview. */
@Composable
fun RecordingsScreen(
    repo: RecordingRepository,
    playRecordings: Boolean,
    onPlayRecordingsChange: (Boolean) -> Unit,
    onInterview: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clips by repo.all.collectAsStateWithLifecycle(emptyList())
    var owner by remember { mutableStateOf("") }
    val player = rememberClipPlayer()
    var importFailed by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importFailed = !repo.import(uri, owner, RecordingPurpose.WAKE_MESSAGE) }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.rec_title), fontSize = 28.sp)
        Text(stringResource(R.string.rec_explain), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rec_play_on_alarm), fontSize = 17.sp, modifier = Modifier.weight(1f))
            Switch(checked = playRecordings, onCheckedChange = onPlayRecordingsChange)
        }
        Button(onClick = onInterview, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
            Text("🎙 " + stringResource(R.string.interview_title), fontSize = 19.sp)
        }

        OutlinedTextField(
            value = owner, onValueChange = { owner = it }, singleLine = true,
            label = { Text(stringResource(R.string.rec_owner)) },
            placeholder = { Text(stringResource(R.string.rec_owner_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordControl(Modifier.weight(1f)) { file, ms ->
                scope.launch { repo.add(file, ms, owner, RecordingPurpose.WAKE_MESSAGE) }
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) }, modifier = Modifier.heightIn(min = BigTouch)) {
                Text(stringResource(R.string.rec_import))
            }
        }
        if (importFailed) Text(stringResource(R.string.rec_import_failed), color = MaterialTheme.colorScheme.error)

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clips, key = { it.id }) { clip ->
                ClipRow(clip, playing = player.playingId == clip.id, onPlay = { player.toggle(clip) }, onDelete = {
                    player.stop()
                    scope.launch { repo.delete(clip) }
                })
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.done), fontSize = 18.sp)
        }
    }
}

private data class Step(val question: Int, val hint: Int, val purpose: RecordingPurpose, val askOwner: Boolean)

private val steps = listOf(
    Step(R.string.interview_q_why, R.string.interview_hint_why, RecordingPurpose.WHY, askOwner = false),
    Step(R.string.interview_q_hard, R.string.interview_hint_hard, RecordingPurpose.ENCOURAGE, askOwner = false),
    Step(R.string.interview_q_family, R.string.interview_hint_family, RecordingPurpose.WAKE_MESSAGE, askOwner = true),
)

/** Guided "personal interview": three short questions, each answered by voice. */
@Composable
fun InterviewScreen(repo: RecordingRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var owner by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val step = steps[index]

    fun next() {
        saved = false
        owner = ""
        if (index == steps.lastIndex) onDone() else index++
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        Text("${index + 1}/${steps.size}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(step.question), fontSize = 30.sp)
        Text(stringResource(step.hint), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (step.askOwner) {
            OutlinedTextField(
                value = owner, onValueChange = { owner = it }, singleLine = true,
                label = { Text(stringResource(R.string.rec_owner)) },
                placeholder = { Text(stringResource(R.string.rec_owner_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.weight(1f))
        if (saved) Text("✅ " + stringResource(R.string.interview_saved), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        RecordControl(Modifier.fillMaxWidth()) { file, ms ->
            scope.launch {
                repo.add(file, ms, if (step.askOwner) owner else "", step.purpose)
                saved = true
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = ::next, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.skip)) }
            Button(onClick = ::next, enabled = saved, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(if (index == steps.lastIndex) R.string.done else R.string.next), fontSize = 18.sp)
            }
        }
    }
}

/** Big record/stop button with a running timer; asks for the microphone on first use. */
@Composable
private fun RecordControl(modifier: Modifier, onRecorded: (File, Long) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { Recorder(context) }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) { onDispose { recorder.stopAndDiscard() } }
    LaunchedEffect(recording) {
        while (recording) { elapsed = System.currentTimeMillis() - startedAt; delay(200) }
    }

    fun begin() {
        runCatching { recorder.start() }.onSuccess { recording = true; startedAt = System.currentTimeMillis() }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) begin() }

    Button(
        onClick = {
            if (recording) {
                recording = false
                recorder.stop()?.let { (file, ms) -> onRecorded(file, ms) }
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                begin()
            } else {
                mic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        colors = if (recording) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
        ) else ButtonDefaults.buttonColors(),
        modifier = modifier.heightIn(min = BigTouch + 8.dp),
    ) {
        Text(
            if (recording) "■ " + stringResource(R.string.rec_stop) + "  " + formatMs(elapsed) else "● " + stringResource(R.string.rec_record),
            fontSize = 19.sp,
        )
    }
}

@Composable
private fun ClipRow(clip: RecordingEntity, playing: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val who = clip.owner.ifBlank { stringResource(R.string.rec_me) }
    val what = when (RecordingPurpose.entries.firstOrNull { it.name == clip.purpose }) {
        RecordingPurpose.WHY -> stringResource(R.string.rec_purpose_why)
        RecordingPurpose.ENCOURAGE -> stringResource(R.string.rec_purpose_encourage)
        else -> stringResource(R.string.rec_purpose_message)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onPlay, shape = CircleShape, modifier = Modifier.size(52.dp)) {
                Text(if (playing) "■" else "▶", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(who, fontSize = 17.sp)
                Text("$what · ${formatMs(clip.durationMs)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Text("🗑", fontSize = 20.sp) }
        }
    }
}

private class ClipPlayer {
    var playingId by mutableStateOf<Long?>(null)
    private var mp: MediaPlayer? = null

    fun toggle(clip: RecordingEntity) {
        val same = playingId == clip.id
        stop()
        if (same) return
        val next = MediaPlayer()
        try {
            next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            next.setDataSource(clip.path)
            next.prepare()
            // ClipPlayer.stop, not MediaPlayer.stop: release and clear the "playing" mark.
            next.setOnCompletionListener { this@ClipPlayer.stop() }
            next.start()
        } catch (e: Exception) {
            next.release()
            return
        }
        mp = next
        playingId = clip.id
    }

    fun stop() {
        mp?.run { runCatching { stop() }; release() }
        mp = null
        playingId = null
    }
}

@Composable
private fun rememberClipPlayer(): ClipPlayer {
    val p = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { p.stop() } }
    return p
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
