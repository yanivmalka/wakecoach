package com.wakecoach.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.wakecoach.core.wake.RelapseCheck
import com.wakecoach.core.wake.RelapseVerdict
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Did you go back to sleep?" (requirement 8). On dismissal it notes the step counter; a few
 * minutes later the check compares it and the screen state, and re-rings if needed.
 * State is tiny and per-morning, so plain SharedPreferences is enough.
 */
class WakeCheck(private val context: Context) {
    private val prefs = context.getSharedPreferences("wake_check", Context.MODE_PRIVATE)

    data class Pending(val request: RingRequest, val stepsAtDismiss: Int?)

    suspend fun arm(request: RingRequest, scheduler: AlarmScheduler, delayMinutes: Int) {
        val steps = readSteps()
        prefs.edit()
            .putLong("alarm", request.alarmId)
            .putLong("first", request.firstRingAt)
            .putInt("relapses", request.relapses)
            .putInt("steps", steps ?: -1)
            .apply()
        scheduler.scheduleWakeCheck(java.time.Duration.ofMinutes(delayMinutes.toLong()))
    }

    fun clear() = prefs.edit().clear().apply()

    /** Runs the check; returns the request to re-ring with, or null when the user is awake. */
    suspend fun check(): RingRequest? {
        val alarmId = prefs.getLong("alarm", -1)
        if (alarmId < 0) return null
        val before = prefs.getInt("steps", -1).takeIf { it >= 0 }
        val now = readSteps()
        // The step counter resets on reboot; a lower value means we cannot compare.
        val delta = if (before != null && now != null && now >= before) now - before else null
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        val relapses = prefs.getInt("relapses", 0)
        val firstRing = prefs.getLong("first", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        clear()
        val mode = when (RelapseCheck.decide(delta, interactive)) {
            RelapseVerdict.AWAKE -> return null
            RelapseVerdict.RELAPSE -> RingMode.RELAPSE
            RelapseVerdict.ASK -> RingMode.ASK
        }
        return RingRequest(
            alarmId = alarmId,
            scheduledAt = System.currentTimeMillis(),
            // Non-zero: a re-ring is not a new morning for the ring log.
            firstRingAt = firstRing,
            snoozeCount = 1,
            mode = mode,
            relapses = relapses + 1,
        )
    }

    /** Current step counter, or null without the sensor/permission or if it doesn't answer quickly. */
    private suspend fun readSteps(): Int? {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toInt())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                // Without ACTIVITY_RECOGNITION registration fails quietly; the timeout covers it.
                if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                    cont.resume(null)
                }
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    companion object {
        fun mayRingAgain(request: RingRequest) = request.relapses < RelapseCheck.MAX_RELAPSES
    }
}
