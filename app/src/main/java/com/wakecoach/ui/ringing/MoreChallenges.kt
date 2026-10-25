package com.wakecoach.ui.ringing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wakecoach.R
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.VoiceQuestion
import com.wakecoach.ui.theme.BigTouch
import java.util.Locale
import kotlin.math.abs

/** Guided breathing: a circle grows on the in-breath, holds, and shrinks on the out-breath. */
@Composable
fun BreatheChallenge(c: Challenge.Breathe, onSolved: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    var phase by remember { mutableIntStateOf(R.string.breathe_in) }
    var round by remember { mutableIntStateOf(1) }
    LaunchedEffect(c) {
        repeat(c.rounds) { i ->
            round = i + 1
            phase = R.string.breathe_in
            scale.animateTo(1f, tween(c.inhaleSec * 1000, easing = LinearEasing))
            phase = R.string.breathe_hold
            kotlinx.coroutines.delay(c.holdSec * 1000L)
            phase = R.string.breathe_out
            scale.animateTo(0.4f, tween(c.exhaleSec * 1000, easing = LinearEasing))
        }
        onSolved()
    }
    Text(stringResource(phase), fontSize = 28.sp)
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(180.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
        )
    }
    Text(stringResource(R.string.breathe_round, round, c.rounds), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Spoken dialog: answer the question out loud. Falls back to typing when speech recognition
 * or the microphone isn't available, so the alarm is always solvable.
 */
@Composable
fun VoiceChallenge(c: Challenge.Voice, onSolved: () -> Unit, onDuck: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val question = stringResource(questionText(c.question))
    var answer by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(!SpeechRecognizer.isRecognitionAvailable(context)) }
    val recognizer = remember { if (typed) null else SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy(); if (listening) onDuck(false) } }

    fun listen() {
        val r = recognizer ?: return
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                listening = false
                onDuck(false)
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                answer = text
                if (c.check(text)) onSolved()
            }
            override fun onError(error: Int) { listening = false; onDuck(false) }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        listening = true
        // Quiet the alarm so the microphone can hear the answer.
        onDuck(true)
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        )
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else typed = true
    }

    Text(question, fontSize = 26.sp, textAlign = TextAlign.Center)
    Text(stringResource(R.string.voice_hint, c.minWords), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (typed) {
        OutlinedTextField(
            value = answer, onValueChange = { answer = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { if (c.check(answer)) onSolved() }, enabled = c.check(answer), modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
            Text(stringResource(R.string.check), fontSize = 20.sp)
        }
    } else {
        if (answer.isNotBlank()) Text("“$answer”", fontSize = 18.sp, textAlign = TextAlign.Center)
        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) listen() else mic.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = !listening,
            modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch + 8.dp),
        ) { Text(if (listening) "🎙 " + stringResource(R.string.voice_listening) else "🎙 " + stringResource(R.string.voice_speak), fontSize = 20.sp) }
        TextButton(onClick = { typed = true }) { Text(stringResource(R.string.voice_type_instead)) }
    }
}

private fun questionText(q: VoiceQuestion): Int = when (q) {
    VoiceQuestion.FIRST_TASK -> R.string.voice_q_first_task
    VoiceQuestion.GRATEFUL -> R.string.voice_q_grateful
    VoiceQuestion.WHY_UP -> R.string.voice_q_why_up
    VoiceQuestion.TODAY_GOAL -> R.string.voice_q_today_goal
}

/**
 * Stand up: the phone must be held upright (gravity along its long axis) for [seconds]
 * without a break. Lying in bed the phone lies flat or tilted, so this needs getting up.
 */
@Composable
fun StandUpChallenge(seconds: Int, onSolved: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        var uprightSince = 0L
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                val (x, y, z) = e.values
                val upright = y > 8.0f && abs(x) < 3.5f && abs(z) < 6.0f
                if (!upright) { uprightSince = 0L; progress = 0f; return }
                if (uprightSince == 0L) uprightSince = e.timestamp
                val held = (e.timestamp - uprightSince) / 1_000_000_000f
                progress = (held / seconds).coerceAtMost(1f)
                if (held >= seconds) { done = true; onSolved() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            // No accelerometer: never leave an alarm that cannot be stopped.
            onSolved()
            return@DisposableEffect onDispose { }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    Text(stringResource(R.string.stand_up_prompt, seconds), fontSize = 22.sp, textAlign = TextAlign.Center)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
}
