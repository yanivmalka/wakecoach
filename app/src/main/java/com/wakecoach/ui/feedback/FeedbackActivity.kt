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
import androidx.annotation.ArrayRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
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
                MessageComposer(Lang.of(Locale.getDefault()), address = container.settings.current().address).morningLine(repo.stats(), repo.priorityFor(LocalDate.now())),
            ).joinToString("\n")
            // The alarm sound has stopped by now; say today's goal out loud.
            speaker = Speaker(this@FeedbackActivity).also { it.speak(message) }
        }

        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(null)
            // Wait for the stored theme so the screen does not flash the wrong colors.
            val mode = settings?.themeMode ?: return@setContent
            WakeCoachTheme(mode, settings?.themePack) {
                if (later) {
                    var tired by remember { mutableStateOf<Int?>(null) }
                    Screen(
                        title = stringResource(R.string.notif_followup_title), message = "",
                        questions = listOf(Question(stringResource(R.string.q_later_tiredness), R.array.scale_tiredness, tired)),
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
                            Question(stringResource(R.string.q_mood), R.array.scale_mood, answers[0]),
                            Question(stringResource(R.string.q_wake_ease), R.array.scale_wake_ease, answers[1]),
                            Question(stringResource(R.string.q_tiredness), R.array.scale_tiredness, answers[2]),
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

/** A 1-5 question; [scale] names each value for this question (tired 5 is not alert 5). */
private data class Question(val text: String, @ArrayRes val scale: Int, val value: Int?)

@Composable
private fun Screen(
    title: String,
    message: String,
    questions: List<Question>,
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
            questions.forEachIndexed { i, q ->
                Text(q.text, fontSize = 20.sp)
                RatingRow(q.value, stringArrayResource(q.scale).toList()) { onAnswer(i, it) }
            }
        }
        val complete = questions.all { it.value != null }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = BigTouch)) { Text(stringResource(R.string.skip)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, enabled = complete, modifier = Modifier.heightIn(min = BigTouch)) {
                Text(stringResource(R.string.done), fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

/** Five buttons, each showing its number and what it means for this question. */
@Composable
private fun RatingRow(value: Int?, labels: List<String>, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { n ->
            val selected = value == n
            Surface(
                onClick = { onPick(n) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).heightIn(min = BigTouch + 20.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("$n", fontSize = 24.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    Text(
                        labels.getOrElse(n - 1) { "" }, fontSize = 12.sp, lineHeight = 14.sp,
                        textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
            }
        }
    }
}
