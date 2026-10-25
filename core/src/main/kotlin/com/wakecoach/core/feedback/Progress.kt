package com.wakecoach.core.feedback

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One calendar week of mornings, for the progress chart (requirement 13). */
data class WeekStats(
    val weekStart: LocalDate,
    val mornings: Int,
    val avgSnoozes: Double,
    /** Average minutes from first ring to dismissal. */
    val avgMinutesToUp: Double,
    val avgMood: Double?,
    val noSnoozeMornings: Int,
)

data class Progress(
    val weeks: List<WeekStats>,
    val currentStreak: Int,
    val bestStreak: Int,
) {
    /** Last week compared with the one before; positive means fewer snoozes (better). */
    val snoozeImprovement: Double?
        get() = if (weeks.size < 2 || weeks[weeks.size - 1].mornings == 0 || weeks[weeks.size - 2].mornings == 0) null
        else weeks[weeks.size - 2].avgSnoozes - weeks[weeks.size - 1].avgSnoozes

    companion object {
        /** Weeks start on [firstDay] (Sunday in Israel); the last [count] weeks up to [today]. */
        fun from(outcomes: List<WakeOutcome>, zone: ZoneId, today: LocalDate, firstDay: DayOfWeek, count: Int = 6): Progress {
            val sorted = outcomes.sortedBy { it.at }
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
            val weeks = (count - 1 downTo 0).map { back ->
                val start = thisWeek.minusWeeks(back.toLong())
                val inWeek = sorted.filter {
                    val d = it.at.atZone(zone).toLocalDate()
                    !d.isBefore(start) && d.isBefore(start.plusWeeks(1))
                }
                WeekStats(
                    weekStart = start,
                    mornings = inWeek.size,
                    avgSnoozes = inWeek.map { it.snoozes }.average().orZero(),
                    avgMinutesToUp = inWeek.map { it.secondsToDismiss / 60.0 }.average().orZero(),
                    avgMood = inWeek.mapNotNull { it.feedback?.mood }.takeIf { it.isNotEmpty() }?.average(),
                    noSnoozeMornings = inWeek.count { it.snoozes == 0 },
                )
            }
            var best = 0
            var run = 0
            sorted.forEach { if (it.snoozes == 0) { run++; best = maxOf(best, run) } else run = 0 }
            return Progress(weeks, currentStreak = run, bestStreak = best)
        }

        private fun Double.orZero() = if (isNaN()) 0.0 else this
    }
}
