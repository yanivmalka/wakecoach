package com.wakecoach.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.wakecoach.ui.theme.BigTouch
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakecoach.R
import com.wakecoach.alarm.RingState
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.ui.theme.BigDigits
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun RingingScreen(
    state: RingState,
    leftHanded: Boolean,
    onSnooze: (Int?) -> Unit,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit = {},
) {
    if (state.kind.isNudge) {
        ReminderScreen(state, onSnooze = { onSnooze(it) }, onDismiss = onDismiss)
        return
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    var slid by remember { mutableStateOf(false) }
    // The first reach for snooze tells the "what if you stay in bed" story instead (requirement 10).
    var storyTold by remember { mutableStateOf(false) }
    var showStory by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Snooze sits at the top, on the side away from the thumb: reachable, but not by reflex.
        if (state.canSnooze) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (leftHanded) Arrangement.End else Arrangement.Start) {
                TextButton(onClick = {
                    val story = state.story
                    if (story != null && !storyTold) {
                        storyTold = true
                        showStory = true
                        onSpeak(story)
                    } else {
                        onSnooze(null)
                    }
                }) {
                    Text(stringResource(R.string.snooze_n, state.snoozeMinutes), fontSize = 16.sp)
                }
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }

        BigDigits(
            now.plusMinutes(state.clockAheadMinutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")),
            Modifier.fillMaxWidth().height(160.dp), color = MaterialTheme.colorScheme.primary,
        )
        if (state.label.isNotBlank()) Text(state.label, fontSize = 26.sp)
        Spacer(Modifier.height(16.dp))
        Text(state.message, fontSize = 20.sp, textAlign = TextAlign.Center)
        state.quote?.let { q ->
            Spacer(Modifier.height(14.dp))
            Text("“${q.text}”", fontSize = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (q.source.isNotBlank()) {
                Text(q.source, fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))

        when {
            showStory -> StoryPanel(
                story = state.story.orEmpty(),
                onUp = { showStory = false; if (state.challenge is Challenge.None) onDismiss() else slid = true },
                onSnoozeAnyway = { showStory = false; onSnooze(null) },
            )
            !slid -> SlideToStop(leftHanded) {
                if (state.challenge is Challenge.None) onDismiss() else slid = true
            }
            else -> ChallengePanel(state.challenge, onSolved = onDismiss)
        }
    }
}

/**
 * A long slide in the thumb's direction, so a sleepy tap cannot stop the alarm.
 * Right-handed users slide left-to-right; left-handed users slide right-to-left.
 */
@Composable
private fun SlideToStop(leftHanded: Boolean, onComplete: () -> Unit) {
    val direction = if (leftHanded) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(80.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(40.dp)).padding(8.dp),
        ) {
            val knob = 64.dp
            val maxPx = with(LocalDensity.current) { (maxWidth - knob).toPx() }
            var offset by remember { mutableFloatStateOf(0f) }
            // offset{} is mirrored in RTL, but drag deltas are physical: flip only the delta.
            val sign = if (leftHanded) -1f else 1f
            Text(
                stringResource(R.string.swipe_to_stop), fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .size(knob)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> offset = (offset + sign * delta).coerceIn(0f, maxPx) },
                        onDragStopped = {
                            if (offset >= maxPx * 0.92f) onComplete()
                            offset = 0f
                        },
                    ),
            )
        }
    }
}

/** Daytime reminder: the label is the message, one tap to dismiss, pick a snooze length. */
@Composable
private fun ReminderScreen(state: RingState, onSnooze: (Int) -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🔔", fontSize = 56.sp)
        BigDigits(now.format(DateTimeFormatter.ofPattern("HH:mm")), Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            state.label, fontSize = 36.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))

        if (state.canSnooze) {
            Text(stringResource(R.string.remind_again_in), fontSize = 16.sp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(5, 10, 15).forEach { m ->
                    OutlinedButton(onClick = { onSnooze(m) }, modifier = Modifier.weight(1f).heightIn(min = BigTouch)) {
                        Text(stringResource(R.string.minutes_short, m), fontSize = 18.sp)
                    }
                }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch + 16.dp)) {
            Text(stringResource(R.string.reminder_done), fontSize = 24.sp)
        }
    }
}

/** The reverse story with the two honest choices: get up now, or snooze anyway. */
@Composable
private fun StoryPanel(story: String, onUp: () -> Unit, onSnoozeAnyway: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(story, fontSize = 19.sp, textAlign = TextAlign.Center)
        Button(onClick = onUp, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch + 8.dp)) {
            Text(stringResource(R.string.story_get_up), fontSize = 22.sp)
        }
        TextButton(onClick = onSnoozeAnyway) { Text(stringResource(R.string.story_snooze_anyway)) }
    }
}
