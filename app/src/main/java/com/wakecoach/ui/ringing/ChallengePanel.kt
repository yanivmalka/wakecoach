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
import com.wakecoach.ui.theme.BigTouch
import kotlin.math.sqrt

@Composable
fun ChallengePanel(challenge: Challenge, onSolved: () -> Unit) {
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
            is Challenge.Shake -> MotionChallenge(challenge.shakes, steps = false, onSolved)
            is Challenge.Steps -> MotionChallenge(challenge.steps, steps = true, onSolved)
            // Scanner arrives in a later phase; the generator never produces this without a registered code.
            is Challenge.ScanCode -> MotionChallenge(30, steps = true, onSolved)
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

/**
 * Counts shakes (accelerometer spikes) or steps (step detector). Without the step sensor or
 * the activity-recognition permission, steps fall back to shakes so the alarm stays solvable.
 */
@Composable
private fun MotionChallenge(target: Int, steps: Boolean, onSolved: () -> Unit) {
    val context = LocalContext.current
    val useSteps = steps && hasStepDetector(context)
    var count by remember { mutableIntStateOf(0) }

    DisposableEffect(useSteps) {
        val sm = context.getSystemService(SensorManager::class.java)
        var lastShake = 0L
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                if (useSteps) count++
                else {
                    val (x, y, z) = e.values
                    val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                    if (g > SHAKE_G && e.timestamp - lastShake > SHAKE_GAP_NS) { lastShake = e.timestamp; count++ }
                }
                if (count >= target) { done = true; onSolved() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val sensor = sm.getDefaultSensor(if (useSteps) Sensor.TYPE_STEP_DETECTOR else Sensor.TYPE_ACCELEROMETER)
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val left = (target - count).coerceAtLeast(0)
    Text(
        if (useSteps) stringResource(R.string.challenge_steps, left) else stringResource(R.string.challenge_shake, left),
        fontSize = 24.sp, textAlign = TextAlign.Center,
    )
    LinearProgressIndicator(progress = { count / target.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
}

private fun hasStepDetector(context: Context): Boolean {
    val sm = context.getSystemService(SensorManager::class.java)
    val permitted = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    return permitted && sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
}

private const val SHAKE_G = 2.2f
private const val SHAKE_GAP_NS = 250_000_000L
