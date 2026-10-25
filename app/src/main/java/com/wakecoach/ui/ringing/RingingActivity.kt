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
                // Read a reminder's label aloud once, after the sound has had a moment to wake the user.
                LaunchedEffect(s?.request?.firstRingAt, s?.request?.snoozeCount) {
                    if (s != null && s.kind.isNudge && s.label.isNotBlank()) {
                        delay(2_000)
                        (speaker ?: Speaker(this@RingingActivity).also { speaker = it }).speak(s.label)
                    }
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
