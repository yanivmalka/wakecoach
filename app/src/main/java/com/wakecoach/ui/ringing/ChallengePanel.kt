package com.wakecoach.ui.ringing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wakecoach.R
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.motion.WalkDetector
import com.wakecoach.ui.theme.BigTouch
import kotlin.math.sqrt

@Composable
fun ChallengePanel(challenge: Challenge, onSolved: () -> Unit, onDuck: (Boolean) -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (challenge) {
            Challenge.None -> LaunchedEffect(Unit) { onSolved() }
            is Challenge.Math -> AnswerChallenge(
                prompt = stringResource(R.string.challenge_math), shown = challenge.question,
                keyboard = KeyboardType.Number, check = challenge::check, onSolved = onSolved,
            )
            is Challenge.TypePhrase -> AnswerChallenge(
                prompt = stringResource(R.string.challenge_phrase), shown = challenge.phrase,
                keyboard = KeyboardType.Text, check = challenge::check, onSolved = onSolved,
            )
            is Challenge.CatchSun -> CatchSunChallenge(challenge, onSolved)
            is Challenge.Shake -> MotionChallenge(challenge.shakes, onSolved)
            is Challenge.Steps -> WalkChallenge(challenge.steps, onSolved)
            // Scanner arrives in a later phase; the generator never produces this without a registered code.
            is Challenge.ScanCode -> WalkChallenge(30, onSolved)
            is Challenge.Breathe -> BreatheChallenge(challenge, onSolved)
            is Challenge.Voice -> VoiceChallenge(challenge, onSolved, onDuck)
            is Challenge.StandUp -> StandUpChallenge(challenge.seconds, onSolved)
        }
    }
}

@Composable
private fun AnswerChallenge(
    prompt: String,
    shown: String,
    keyboard: KeyboardType,
    check: (String) -> Boolean,
    onSolved: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Text(prompt, fontSize = 18.sp)
    Text(shown, fontSize = 34.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    OutlinedTextField(
        value = input, onValueChange = { input = it; wrong = false },
        textStyle = TextStyle(fontSize = 28.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true, isError = wrong,
        modifier = Modifier.fillMaxWidth(),
    )
    if (wrong) Text(stringResource(R.string.wrong_try_again), color = MaterialTheme.colorScheme.error)
    Button(
        onClick = { if (check(input)) onSolved() else { wrong = true; input = "" } },
        modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch),
    ) { Text(stringResource(R.string.check), fontSize = 22.sp) }
}

/** Counts hard shakes (accelerometer spikes). */
@Composable
private fun MotionChallenge(target: Int, onSolved: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        var lastShake = 0L
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                val (x, y, z) = e.values
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g > SHAKE_G && e.timestamp - lastShake > SHAKE_GAP_NS) { lastShake = e.timestamp; count++ }
                if (count >= target) { done = true; onSolved() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val left = (target - count).coerceAtLeast(0)
    Text(stringResource(R.string.challenge_shake, left), fontSize = 24.sp, textAlign = TextAlign.Center)
    LinearProgressIndicator(progress = { count / target.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
}

/**
 * Walking challenge. The system step detector is accurate but batched: it often reports nothing
 * for the first 5-10 seconds, so on its own the challenge feels stuck and people end up shaking
 * the phone. An accelerometer [WalkDetector] counts each stride at once; whichever count is ahead
 * wins. Without the step sensor or its permission, the accelerometer alone still counts walking,
 * so no hard shaking is ever needed.
 */
@Composable
private fun WalkChallenge(target: Int, onSolved: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val walk = WalkDetector()
        var systemSteps = 0
        var done = false
        fun update() {
            count = maxOf(walk.steps, systemSteps)
            if (!done && count >= target) { done = true; onSolved() }
        }
        val accel = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                val (x, y, z) = e.values
                if (walk.onSample(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH, e.timestamp)) update()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val stepper = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                systemSteps++
                update()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(accel, it, SensorManager.SENSOR_DELAY_GAME) }
        if (hasStepDetector(context)) {
            sm.registerListener(stepper, sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), SensorManager.SENSOR_DELAY_FASTEST)
        }
        onDispose {
            sm.unregisterListener(accel)
            sm.unregisterListener(stepper)
        }
    }

    val left = (target - count).coerceAtLeast(0)
    Text(stringResource(R.string.challenge_steps, left), fontSize = 24.sp, textAlign = TextAlign.Center)
    Text(stringResource(R.string.challenge_steps_hint), fontSize = 15.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress = { count / target.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
}

/**
 * "Catch the sun": tap the one sun among night symbols, a few rounds. Big cells, no typing, no
 * thinking; a wrong tap only says so and the same board stays.
 */
@Composable
private fun CatchSunChallenge(challenge: Challenge.CatchSun, onSolved: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var round by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf(false) }
    val board = challenge.boards[round.coerceAtMost(challenge.rounds - 1)]

    Text(stringResource(R.string.challenge_catch_sun, round + 1, challenge.rounds), fontSize = 22.sp, textAlign = TextAlign.Center)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        board.cells.chunked(board.columns).forEachIndexed { row, cells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.forEachIndexed { col, symbol ->
                    val index = row * board.columns + col
                    Surface(
                        onClick = {
                            if (board.isSun(index)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                missed = false
                                if (round + 1 >= challenge.rounds) onSolved() else round++
                            } else missed = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(symbol, fontSize = if (board.columns <= 3) 44.sp else 34.sp)
                        }
                    }
                }
            }
        }
    }
    Text(
        if (missed) stringResource(R.string.challenge_catch_sun_miss) else " ",
        fontSize = 16.sp, color = MaterialTheme.colorScheme.error,
    )
    LinearProgressIndicator(progress = { round / challenge.rounds.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
}

private fun hasStepDetector(context: Context): Boolean {
    val sm = context.getSystemService(SensorManager::class.java)
    val permitted = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    return permitted && sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
}

private const val SHAKE_G = 2.2f
private const val SHAKE_GAP_NS = 250_000_000L
