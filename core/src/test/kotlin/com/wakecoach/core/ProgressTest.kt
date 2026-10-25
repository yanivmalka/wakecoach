package com.wakecoach.core

import com.wakecoach.core.feedback.Progress
import com.wakecoach.core.feedback.WakeOutcome
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val today = LocalDate.of(2026, 10, 25) // Sunday

    private fun at(daysBack: Int, snoozes: Int) =
        WakeOutcome(today.minusDays(daysBack.toLong()).atTime(6, 30).atZone(zone).toInstant(), snoozes, secondsToDismiss = 120, feedback = null)

    @Test fun weeklyBucketsStreaksAndTrend() {
        // Last week (days 7..1 back): no snoozes. The week before (14..8 back): 2 snoozes each.
        val outcomes = (1..7).map { at(it, 0) } + (8..14).map { at(it, 2) }
        val p = Progress.from(outcomes, zone, today, DayOfWeek.SUNDAY, count = 3)
        assertEquals(3, p.weeks.size)
        assertEquals(listOf(7, 7, 0), p.weeks.map { it.mornings })
        assertEquals(2.0, p.weeks[0].avgSnoozes)
        assertEquals(0.0, p.weeks[1].avgSnoozes)
        assertEquals(2.0, p.weeks[1].avgMinutesToUp)
        assertEquals(7, p.currentStreak)
        assertEquals(7, p.bestStreak)
        // Current week is empty, so there is no comparison yet.
        assertEquals(null, p.snoozeImprovement)
        assertEquals(2.0, Progress.from(outcomes, zone, today.minusDays(1), DayOfWeek.SUNDAY, 2).snoozeImprovement)
    }
}
