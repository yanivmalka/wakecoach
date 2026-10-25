package com.wakecoach.core

import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.feedback.Feedback
import com.wakecoach.core.feedback.FeedbackStats
import com.wakecoach.core.feedback.WakeOutcome
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.model.SnoozePolicy
import com.wakecoach.core.schedule.AlarmTimeCalculator
import com.wakecoach.core.schedule.BedtimeCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmTimeCalculatorTest {
    private val tlv = ZoneId.of("Asia/Jerusalem")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), tlv)

    @Test fun oneShotLaterToday() {
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(7, 0), emptySet(), at("2026-10-20", "06:00"))
        assertEquals(at("2026-10-20", "07:00"), next)
    }

    @Test fun oneShotAlreadyPassedGoesToTomorrow() {
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(7, 0), emptySet(), at("2026-10-20", "07:00"))
        assertEquals(at("2026-10-21", "07:00"), next)
    }

    @Test fun repeatSkipsToNextSelectedDay() {
        // 2026-10-20 is a Tuesday; next Sunday is the 25th.
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(6, 30), setOf(DayOfWeek.SUNDAY), at("2026-10-20", "08:00"))
        assertEquals(at("2026-10-25", "06:30"), next)
    }

    @Test fun repeatSameDayNextWeekWhenPassed() {
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(6, 30), setOf(DayOfWeek.TUESDAY), at("2026-10-20", "08:00"))
        assertEquals(at("2026-10-27", "06:30"), next)
    }

    @Test fun skipOncePassesOverThatDay() {
        // Repeats Sun and Tue; skipping Sun 25th lands on Tue 27th.
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY)
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(6, 30), days, at("2026-10-24", "22:00"), LocalDate.parse("2026-10-25"))
        assertEquals(at("2026-10-27", "06:30"), next)
        // Weekly alarm skipping its only day moves a full week.
        val weekly = AlarmTimeCalculator.nextTrigger(LocalTime.of(6, 30), setOf(DayOfWeek.SUNDAY), at("2026-10-24", "22:00"), LocalDate.parse("2026-10-25"))
        assertEquals(at("2026-11-01", "06:30"), weekly)
    }

    @Test fun dstGapShiftsForward() {
        // Israel springs forward on 2026-03-27 at 02:00 -> 03:00.
        val next = AlarmTimeCalculator.nextTrigger(LocalTime.of(2, 30), emptySet(), at("2026-03-27", "01:00"))
        assertEquals(LocalTime.of(3, 30), next.toLocalTime())
        assertTrue(next.isAfter(at("2026-03-27", "01:00")))
    }
}

class BedtimeCalculatorTest {
    @Test fun worksBackwardsFromWake() {
        val wake = ZonedDateTime.of(2026, 10, 21, 6, 30, 0, 0, ZoneId.of("UTC"))
        val plan = BedtimeCalculator.plan(wake, Duration.ofHours(8), fallAsleepMinutes = 15, windDownMinutes = 30)
        assertEquals(LocalTime.of(22, 15), plan.lightsOutAt.toLocalTime())
        assertEquals(LocalTime.of(21, 45), plan.reminderAt.toLocalTime())
    }
}

class SnoozeAndProfileTest {
    @Test fun shrinkingSnooze() {
        val p = SnoozePolicy(maxSnoozes = 3, snoozeMinutes = 8, shrinkMinutes = 3)
        assertEquals(listOf(8, 5, 2, 1), (0..3).map(p::minutesFor))
        assertTrue(p.canSnooze(2)); assertFalse(p.canSnooze(3))
    }

    @Test fun soldierHasNoSnooze() {
        assertFalse(Profiles.SOLDIER.snooze.allowed)
        assertEquals(Profiles.BALANCED, Profiles.byId("unknown"))
    }

    @Test fun intensityClamps() {
        assertEquals(Intensity.HARDCORE, Intensity.HARDCORE.stepUp())
        assertEquals(Intensity.GENTLE, Intensity.GENTLE.stepDown())
    }
}

class ChallengeTest {
    private val gen = ChallengeGenerator(Random(42))

    @Test fun mathAnswersAreCorrectForAllLevels() {
        Intensity.entries.forEach { level ->
            repeat(50) {
                val m = gen.math(level)
                val expr = m.question.replace("×", "*").replace("−", "-").split(" ")
                var acc = expr[0].toInt(); var i = 1
                // left-to-right but × binds first: forms are "a + b", "a * b + c", "a * b - c"
                if (expr.size == 5) { acc *= expr[2].toInt(); i = 3 }
                acc = if (expr[i] == "+") acc + expr[i + 1].toInt() else acc - expr[i + 1].toInt()
                assertEquals(acc, m.answer, m.question)
                assertTrue(m.check(" ${m.answer} "))
            }
        }
    }

    @Test fun phraseCheckIgnoresCaseAndSpacing() {
        assertTrue(Challenge.TypePhrase("Every morning is new").check("every  morning is new "))
        assertFalse(Challenge.TypePhrase("Every morning is new").check("every morning"))
    }

    @Test fun scanWithoutCodeFallsBackToSteps() {
        val c = gen.generate(ChallengeType.SCAN_CODE, Intensity.FIRM, emptyList(), registeredCode = null)
        assertTrue(c is Challenge.Steps)
    }
}

class FeedbackTest {
    private fun outcome(day: Long, snoozes: Int, ease: Int?) = WakeOutcome(
        at = Instant.ofEpochSecond(day * 86_400), snoozes = snoozes, secondsToDismiss = 30,
        feedback = ease?.let { Feedback(Instant.ofEpochSecond(day * 86_400), mood = 4, wakeEase = it, tiredness = 2) },
    )

    @Test fun statsAndStreak() {
        val stats = FeedbackStats.from(listOf(outcome(3, 0, 5), outcome(1, 2, 2), outcome(2, 0, 4)))
        assertEquals(2, stats.noSnoozeStreak)
        assertEquals(4.5, stats.avgEaseNoSnooze)
        assertEquals(2.0, stats.avgEaseWithSnooze)
    }

    @Test fun messageMentionsPriority() {
        val stats = FeedbackStats.from(emptyList())
        val line = MessageComposer(Lang.EN, Random(1)).morningLine(stats, "job interview")
        assertTrue("job interview" in line)
    }

    @Test fun ratingsValidated() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            Feedback(Instant.EPOCH, mood = 6, wakeEase = 3, tiredness = 3)
        }
    }
}

class MorningDateTest {
    @Test fun lateNightStillMeansThisMorning() {
        val d = java.time.LocalDate.of(2026, 10, 21)
        assertEquals(d, com.wakecoach.core.schedule.MorningDate.upcoming(d.atTime(1, 30)))
        assertEquals(d.plusDays(1), com.wakecoach.core.schedule.MorningDate.upcoming(d.atTime(22, 0)))
    }
}

class ReminderProfileTest {
    @Test fun reminderIsQuickAndUnchallenged() {
        val r = Profiles.REMINDER
        assertEquals(listOf(ChallengeType.NONE), r.challenges)
        assertTrue(r.snooze.allowed)
        // Reminders are not offered as a wake-up profile.
        assertFalse(r in Profiles.all)
    }
}

class RiskLineTest {
    @Test fun explainsWithoutRevealingSurprise() {
        val he = com.wakecoach.core.message.MessageComposer(Lang.HE)
        val en = MessageComposer(Lang.EN)
        val hard = listOf(com.wakecoach.core.adapt.RiskReason.HARD_WEEKDAY, com.wakecoach.core.adapt.RiskReason.RECENT_SNOOZES)
        assertTrue(en.riskLine(hard, DayOfWeek.SUNDAY)!!.startsWith("Sundays"))
        assertTrue(he.riskLine(hard, DayOfWeek.SUNDAY)!!.startsWith("ימי ראשון"), he.riskLine(hard, DayOfWeek.SUNDAY))
        assertEquals(null, en.riskLine(listOf(com.wakecoach.core.adapt.RiskReason.SURPRISE), DayOfWeek.MONDAY))
    }
}

class NewChallengesTest {
    private val gen = ChallengeGenerator(Random(9))

    @Test fun generatesAndChecks() {
        val b = gen.generate(ChallengeType.BREATHE, Intensity.FIRM, emptyList(), null) as Challenge.Breathe
        assertEquals(5, b.rounds)
        assertEquals(55, b.totalSec)
        val v = gen.generate(ChallengeType.VOICE, Intensity.HARDCORE, emptyList(), null) as Challenge.Voice
        assertEquals(4, v.minWords)
        assertTrue(v.check("call the bank then gym"))
        assertFalse(v.check("gym"))
        val s = gen.generate(ChallengeType.STAND_UP, Intensity.GENTLE, emptyList(), null) as Challenge.StandUp
        assertEquals(6, s.seconds)
    }
}
