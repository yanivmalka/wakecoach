package com.wakecoach.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.wakecoach.alarm.AlarmService
import com.wakecoach.alarm.RingRequest
import com.wakecoach.audio.Speaker
import com.wakecoach.container
import com.wakecoach.ui.feedback.FeedbackActivity
import com.wakecoach.ui.theme.ThemeMode
import com.wakecoach.ui.theme.WakeCoachTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen ring UI over the lock screen. */
class RingingActivity : ComponentActivity() {
    private var speaker: Speaker? = null

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        val controller = container.ringController
        // Launched by the fallback notification (service could not start from the background).
        if (controller.state.value == null && RingRequest.from(intent) != null) {
            ContextCompat.startForegroundService(this, Intent(this, AlarmService::class.java).putExtras(intent))
        }
        setContent {
            WakeCoachTheme(ThemeMode.DARK) {
                val state by controller.state.collectAsStateWithLifecycle()
                val leftHanded by container.settings.settings.collectAsStateWithLifecycle(null)
                val scope = rememberCoroutineScope()
                // No escaping the alarm with the back button.
                BackHandler { }

                val s = state
                // Speak once the sound has had a moment to wake the user: a reminder's label, or for a
                // wake-up the morning line. The ring ducks under the voice.
                LaunchedEffect(s?.request?.firstRingAt, s?.request?.snoozeCount) {
                    if (s == null) return@LaunchedEffect
                    val lines = if (s.kind.isNudge) listOf(s.label)
                    else listOfNotNull(s.message, s.quote?.let { q -> listOf(q.text, q.source).filter(String::isNotBlank).joinToString(". ") })
                    if (lines.all(String::isBlank)) return@LaunchedEffect
                    delay(if (s.kind.isNudge) 2_000 else 8_000)
                    val voice = speaker ?: Speaker(this@RingingActivity) { controller.duck(it) }.also { speaker = it }
                    lines.forEach(voice::speak)
                }
                if (s == null) {
                    // The UI can open before the service has loaded the alarm; give it a moment,
                    // then close if nothing is ringing (opened late or handled elsewhere).
                    LaunchedEffect(Unit) {
                        delay(3_000)
                        if (controller.state.value == null) finish()
                    }
                } else {
                    RingingScreen(
                        state = s,
                        leftHanded = leftHanded?.leftHanded ?: false,
                        onSnooze = { minutes -> if (controller.snooze(minutes)) finish() },
                        onDismiss = {
                            scope.launch {
                                val id = controller.dismiss()
                                if (id != null) startActivity(FeedbackActivity.morningIntent(this@RingingActivity, id))
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }
}
