package com.wakecoach.core

import com.wakecoach.core.motion.WalkDetector
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalkDetectorTest {
    private val ms = 1_000_000L

    /** A stride bounce: magnitude oscillating around 1 g with [amp] at [stepsPerSec], sampled at 50 Hz. */
    private fun walk(d: WalkDetector, seconds: Int, stepsPerSec: Double, amp: Double) {
        for (i in 0 until seconds * 50) {
            val t = i * 20 * ms
            val g = 1.0 + amp * sin(2 * PI * stepsPerSec * i / 50.0)
            d.onSample(g.toFloat(), t)
        }
    }

    @Test fun `normal walking counts every step right away`() {
        val d = WalkDetector()
        walk(d, seconds = 10, stepsPerSec = 1.8, amp = 0.3)
        assertEquals(18, d.steps)
    }

    @Test fun `lying still counts nothing`() {
        val d = WalkDetector()
        walk(d, seconds = 10, stepsPerSec = 1.0, amp = 0.05)
        assertEquals(0, d.steps)
    }

    @Test fun `frantic shaking is capped by the minimum gap`() {
        val d = WalkDetector()
        walk(d, seconds = 10, stepsPerSec = 8.0, amp = 1.5)
        assertTrue(d.steps <= 36, "got ${d.steps}")
    }
}
