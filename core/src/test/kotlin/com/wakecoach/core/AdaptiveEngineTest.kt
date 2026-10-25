package com.wakecoach.core

import com.wakecoach.core.adapt.AdaptiveEngine
import com.wakecoach.core.adapt.MorningInput
import com.wakecoach.core.adapt.RiskReason
import com.wakecoach.core.feedback.Feedback
import com.wakecoach.core.feedback.WakeOutcome
import com.wakecoach.core.model.Intensity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveEngineTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val start = LocalDate.of(2026, 10, 4) // a Sunday

    private fun day(i: Int, snoozes: Int, tired: Int? = null) = start.plusDays(i.toLong()).atTime(6, 30).atZone(zone).toInstant().let {
        WakeOutcome(it, snoozes, 60, tired?.let { t -> Feedback(it, 3, 3, t) })
    }

    /** Never surprises, so tests see only the rule-based part. */
    private val noSurprise = AdaptiveEngine(object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = 0.99
    })

    private fun input(history: List<WakeOutcome>, day: DayOfWeek = DayOfWeek.SUNDAY, importance: Int = 1, sleep: Double? = null) =
        MorningInput(history, zone, day, importance, sleep, Intensity.BALANCED)

    @Test fun snoozyWeekRaisesIntensity() {
        val plan = noSurprise.plan(input((0..6).map { day(it, snoozes = 3) }, sleep = 5.5))
        assertTrue(plan.risk >= 80, "risk ${plan.risk}")
        assertEquals(Intensity.HARDCORE, plan.intensity)
        assertTrue(RiskReason.RECENT_SNOOZES in plan.reasons && RiskReason.SHORT_SLEEP in plan.reasons)
    }

    @Test fun hardWeekdayIsRecognised() {
        // Sundays (0, 7, 14) snoozed, other days fine.
        val h = (0..20).map { day(it, snoozes = if (it % 7 == 0) 2 else 0) }
        val sunday = noSurprise.plan(input(h, DayOfWeek.SUNDAY))
        val tuesday = noSurprise.plan(input(h, DayOfWeek.TUESDAY))
        assertTrue(RiskReason.HARD_WEEKDAY in sunday.reasons)
        assertFalse(RiskReason.HARD_WEEKDAY in tuesday.reasons)
        assertTrue(sunday.risk > tuesday.risk)
    }

    @Test fun longStreakEasesOnlyOneStep() {
        val plan = noSurprise.plan(input((0..9).map { day(it, snoozes = 0) }))
        assertEquals(Intensity.GENTLE, plan.intensity) // BALANCED stepped down once
        assertTrue(RiskReason.GOOD_STREAK in plan.reasons)
    }

    @Test fun surpriseOnlyAfterStreakAndRaisesIntensity() {
        val always = AdaptiveEngine(object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextDouble() = 0.0
        })
        val streak = always.plan(input((0..9).map { day(it, 0) }))
        assertTrue(streak.surprise)
        assertEquals(Intensity.FIRM, streak.intensity)
        assertFalse(always.plan(input(listOf(day(0, 2)))).surprise)
    }

    @Test fun tiredAndImportantAddUp() {
        val h = (0..3).map { day(it, snoozes = 0, tired = 5) }
        val plan = noSurprise.plan(input(h, importance = 3))
        assertTrue(RiskReason.TIRED_LATELY in plan.reasons && RiskReason.IMPORTANT_DAY in plan.reasons)
    }
}
