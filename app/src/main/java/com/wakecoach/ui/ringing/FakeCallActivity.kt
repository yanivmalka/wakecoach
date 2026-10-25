package com.wakecoach.ui.ringing

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.audio.Speaker
import com.wakecoach.ui.theme.BigTouch
import com.wakecoach.ui.theme.ThemeMode
import com.wakecoach.ui.theme.WakeCoachTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Simulated "I'm calling X" after a repeat snooze (requirement 9). It looks and sounds like an
 * outgoing call (ringback, connect, a voice, hang-up), but NO call is ever placed: nothing here
 * touches telephony. The screen says so at the end.
 */
class FakeCallActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var speaker: Speaker? = null

    private enum class Phase { DIALING, CONNECTED, ENDED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val clip = intent.getStringExtra(EXTRA_CLIP)?.let(::File)?.takeIf { it.exists() }

        var phase by mutableStateOf(Phase.DIALING)
        var seconds by mutableIntStateOf(0)

        setContent {
            WakeCoachTheme(ThemeMode.DARK) {
                LaunchedEffect(Unit) {
                    play(Uri.parse("android.resource://$packageName/${R.raw.ringback}"))
                    phase = Phase.CONNECTED
                    // Their own recorded voice if we have it, otherwise a spoken line.
                    if (clip != null) play(Uri.fromFile(clip)) else say(getString(R.string.fake_call_voice, name))
                    phase = Phase.ENDED
                }
                LaunchedEffect(phase) {
                    while (phase == Phase.CONNECTED) { delay(1000); seconds++ }
                }
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(40.dp))
                    Box(
                        Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(name.take(1).ifBlank { "?" }, fontSize = 52.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(name, fontSize = 32.sp)
                    Text(
                        when (phase) {
                            Phase.DIALING -> stringResource(R.string.fake_call_dialing)
                            Phase.CONNECTED -> "%d:%02d".format(seconds / 60, seconds % 60)
                            Phase.ENDED -> stringResource(R.string.fake_call_ended)
                        },
                        fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (phase == Phase.ENDED) {
                        Text(stringResource(R.string.fake_call_warning, name), fontSize = 20.sp, textAlign = TextAlign.Center)
                        Text(
                            stringResource(R.string.fake_call_disclaimer), fontSize = 13.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = ::finish, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
                            Text(stringResource(R.string.fake_call_close), fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }

    /** Plays [uri] once on the alarm stream and returns when it finishes (or fails). */
    private suspend fun play(uri: Uri) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            mp.setDataSource(this, uri)
            mp.prepare()
        } catch (e: Exception) {
            mp.release()
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        player = mp
        mp.setOnCompletionListener { it.release(); if (player === it) player = null; if (cont.isActive) cont.resume(Unit) }
        mp.start()
        cont.invokeOnCancellation { runCatching { mp.release() } }
    }

    private suspend fun say(text: String) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        speaker = Speaker(this) { speaking -> if (!speaking) done.complete(Unit) }.also { it.speak(text) }
        // TTS may be unavailable; never hang the "call".
        kotlinx.coroutines.withTimeoutOrNull(15_000) { done.await() }
    }

    override fun onDestroy() {
        player?.run { runCatching { stop() }; release() }
        speaker?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_CLIP = "clip"

        fun intent(context: Context, name: String, clip: String?) =
            Intent(context, FakeCallActivity::class.java).putExtra(EXTRA_NAME, name).putExtra(EXTRA_CLIP, clip)
    }
}
