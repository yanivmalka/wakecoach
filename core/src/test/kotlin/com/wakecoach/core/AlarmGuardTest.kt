package com.wakecoach.core

import com.wakecoach.core.guard.AlarmGuard
import com.wakecoach.core.guard.GuardDecision
import com.wakecoach.core.guard.GuardResult
import com.wakecoach.core.guard.PlannedAlarm
import com.wakecoach.core.guard.RingRecord
import com.wakecoach.core.guard.SlotDetector
import com.wakecoach.core.model.AlarmKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmGuardTest {
    // 2026-10-25 is a Sunday.
    private val today = LocalDate.of(2026, 10, 25)

    /** Wake-ups at ~06:30 and a "Pick up the kids" reminder at ~16:00 every weekday Sun-Thu, for 4 weeks. */
    private val log = (1..28).flatMap { back ->
        val d = today.minusDays(back.toLong())
        if (d.dayOfWeek == DayOfWeek.FRIDAY || d.dayOfWeek == DayOfWeek.SATURDAY) emptyList()
        else listOf(
            RingRecord(AlarmKind.WAKE, "", d.atTime(6, 30 + back % 3)),
            RingRecord(AlarmKind.REMINDER, if (back % 2 == 0) "Pick up the kids" else "pick up  the kids", d.atTime(16, back % 5)),
        )
    }

    @Test fun learnsWeekdaySlotsButNotWeekend() {
        val slots = SlotDetector.detect(log, today)
        assertEquals(10, slots.size) // 5 days x (wake + reminder)
        assertTrue(slots.none { it.day == DayOfWeek.SATURDAY })
        val monWake = slots.first { it.day == DayOfWeek.MONDAY && it.kind == AlarmKind.WAKE }
        assertTrue(monWake.time in LocalTime.of(6, 29)..LocalTime.of(6, 33))
    }

    @Test fun missingTomorrowListsEverySlotTogether() {
        val slots = SlotDetector.detect(log, today)
        val monday = today.plusDays(1)
        val r = AlarmGuard.evaluate(slots, planned = emptyList(), date = monday, restDays = emptySet(), decisions = emptyList())
        assertTrue(r is GuardResult.Missing)
        assertEquals(listOf(AlarmKind.WAKE, AlarmKind.REMINDER), (r as GuardResult.Missing).slots.map { it.kind })
    }

    @Test fun coveredByNearbyAlarmWithSimilarLabel() {
        val slots = SlotDetector.detect(log, today)
        val planned = listOf(
            PlannedAlarm(LocalTime.of(6, 45), AlarmKind.WAKE, ""),
            PlannedAlarm(LocalTime.of(15, 50), AlarmKind.REMINDER, "pick up the kids from kindergarten"),
        )
        assertEquals(GuardResult.NoAction, AlarmGuard.evaluate(slots, planned, today.plusDays(1), emptySet(), emptyList()))
    }

    @Test fun restDaysAndDecisionsSilenceIt() {
        val slots = SlotDetector.detect(log, today)
        val monday = today.plusDays(1)
        assertEquals(GuardResult.NoAction, AlarmGuard.evaluate(slots, emptyList(), monday, setOf(DayOfWeek.MONDAY), emptyList()))
        val skips = slots.filter { it.day == DayOfWeek.MONDAY }.map { GuardDecision(it.key, monday, GuardDecision.Decision.NOT_THIS_TIME) }
        assertEquals(GuardResult.NoAction, AlarmGuard.evaluate(slots, emptyList(), monday, emptySet(), skips))
    }

    @Test fun offersToStopAfterThreeSkips() {
        val slot = SlotDetector.detect(log, today).first()
        val three = (1..3).map { GuardDecision(slot.key, today.plusWeeks(it.toLong()), GuardDecision.Decision.NOT_THIS_TIME) }
        assertTrue(AlarmGuard.shouldOfferStop(slot, three))
        assertTrue(!AlarmGuard.shouldOfferStop(slot, three.take(2)))
    }
}
