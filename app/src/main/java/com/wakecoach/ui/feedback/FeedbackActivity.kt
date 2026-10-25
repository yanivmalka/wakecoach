package com.wakecoach.ui.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wakecoach.R
import com.wakecoach.audio.Speaker
import com.wakecoach.container
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.ui.theme.BigTouch
import com.wakecoach.ui.theme.WakeCoachTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** 1-5 check-ins (requirement 7): right after waking, and a second tiredness sample later. */
class FeedbackActivity : ComponentActivity() {
    private var speaker: Speaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getLongExtra(EXTRA_SESSION, -1)
        val later = intent.getBooleanExtra(EXTRA_LATER, false)
        if (sessionId < 0) { finish(); return }
        val repo = container.repository

        var message by mutableStateOf("")
        if (!later) lifecycleScope.launch {
            val ahead = intent.getIntExtra(EXTRA_CLOCK_AHEAD, 0)
            message = listOfNotNull(
                // Own up to the clock fib now that the user is up.
                if (ahead > 0) getString(R.string.clock_ahead_reveal, ahead) else null,
                MessageComposer(Lang.of(Locale.getDefault())).morningLine(repo.stats(), repo.priorityFor(LocalDate.now())),
            ).joinToString("\n")
            // The alarm sound has stopped by now; say today's goal out loud.
            speaker = Speaker(this@FeedbackActivity).also { it.speak(message) }
        }

        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(null)
            // Wait for the stored theme so the screen does not flash the wrong colors.
            val mode = settings?.themeMode ?: return@setContent
            WakeCoachTheme(mode) {
                if (later) {
                    var tired by remember { mutableStateOf<Int?>(null) }
                    Screen(
                        title = stringResource(R.string.notif_followup_title), message = "",
                        questions = listOf(stringResource(R.string.q_later_tiredness) to tired),
                        onAnswer = { _, v -> tired = v },
                        onDone = {
                            tired?.let { lifecycleScope.launch { repo.saveLaterTiredness(sessionId, it); finish() } } ?: finish()
                        },
                    )
                } else {
                    var answers by remember { mutableStateOf(listOf<Int?>(null, null, null)) }
                    Screen(
                        title = stringResource(R.string.feedback_title), message = message,
                        questions = listOf(
                            stringResource(R.string.q_mood) to answers[0],
                            stringResource(R.string.q_wake_ease) to answers[1],
                            stringResource(R.string.q_tiredness) to answers[2],
                        ),
                        onAnswer = { i, v -> answers = answers.toMutableList().also { it[i] = v } },
                        onDone = {
                            val (m, e, t) = answers
                            if (m != null && e != null && t != null) {
                                lifecycleScope.launch { repo.saveFeedback(sessionId, m, e, t); finish() }
                            } else finish()
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SESSION = "session_id"
        private const val EXTRA_LATER = "later"
        private const val EXTRA_CLOCK_AHEAD = "clock_ahead"

        fun morningIntent(context: Context, sessionId: Long, clockAheadMinutes: Int = 0) =
            Intent(context, FeedbackActivity::class.java).putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_CLOCK_AHEAD, clockAheadMinutes)

        fun laterIntent(context: Context, sessionId: Long) =
            morningIntent(context, sessionId).putExtra(EXTRA_LATER, true)
    }
}

@Composable
private fun Screen(
    title: String,
    message: String,
    questions: List<Pair<String, Int?>>,
    onAnswer: (Int, Int) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, fontSize = 28.sp)
            if (message.isNotBlank()) Text(message, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            questions.forEachIndexed { i, (q, value) ->
                Text(q, fontSize = 20.sp)
                RatingRow(value) { onAnswer(i, it) }
            }
        }
        val complete = questions.all { it.second != null }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = BigTouch)) { Text(stringResource(R.string.skip)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, enabled = complete, modifier = Modifier.heightIn(min = BigTouch)) {
                Text(stringResource(R.string.done), fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun RatingRow(value: Int?, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { n ->
            val modifier = Modifier.weight(1f).heightIn(min = BigTouch)
            if (value == n) {
                Button(onClick = { onPick(n) }, modifier = modifier) { Text("$n", fontSize = 24.sp, textAlign = TextAlign.Center) }
            } else {
                FilledTonalButton(onClick = { onPick(n) }, modifier = modifier) { Text("$n", fontSize = 24.sp) }
            }
        }
    }
}
