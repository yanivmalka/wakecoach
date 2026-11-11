package com.wakecoach.core.motion

/**
 * Counts walking steps from raw accelerometer magnitude, with no latency.
 *
 * The system step detector batches events and often reports nothing for the first several
 * steps, so a walking challenge feels stuck. This counts each bounce of a normal stride:
 * the magnitude (in g) must rise above [high] after having dropped below [low], and steps
 * closer than [minGapNs] apart are ignored (no one walks faster than ~3.5 steps a second).
 * A shake also counts, but only one per [minGapNs], so walking is never slower than shaking.
 */
class WalkDetector(
    private val high: Float = 1.18f,
    private val low: Float = 1.02f,
    private val minGapNs: Long = 280_000_000L,
) {
    var steps = 0
        private set
    private var armed = true
    private var lastStepNs = Long.MIN_VALUE / 2

    /** Feeds one sample; returns true when it counted a step. */
    fun onSample(g: Float, timestampNs: Long): Boolean {
        if (g < low) armed = true
        if (armed && g > high && timestampNs - lastStepNs >= minGapNs) {
            armed = false
            lastStepNs = timestampNs
            steps++
            return true
        }
        return false
    }
}
