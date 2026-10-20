package com.wakecoach.core.feedback

import java.time.Instant

/** Morning self-report, each value 1..5 (requirement 7). */
data class Feedback(
    val at: Instant,
    val mood: Int,
    val wakeEase: Int,
    val tiredness: Int,
    /** Second tiredness sample taken ~30 minutes later, if the user answered. */
    val laterTiredness: Int? = null,
) {
    init {
        listOfNotNull(mood, wakeEase, tiredness, laterTiredness).forEach {
            require(it in 1..5) { "ratings are 1..5, got $it" }
        }
    }
}

/** Outcome of one morning, as recorded by the ringing flow. */
data class WakeOutcome(
    val at: Instant,
    val snoozes: Int,
    /** Seconds from first ring to dismissal. */
    val secondsToDismiss: Long,
    val feedback: Feedback?,
)

data class FeedbackStats(
    val mornings: Int,
    val avgMood: Double?,
    val avgWakeEase: Double?,
    val avgTiredness: Double?,
    val avgSnoozes: Double,
    /** Consecutive most-recent mornings without snoozing. */
    val noSnoozeStreak: Int,
    /** Average wake ease on mornings without snooze, to show "it feels better when you get up at once". */
    val avgEaseNoSnooze: Double?,
    val avgEaseWithSnooze: Double?,
) {
    companion object {
        fun from(outcomes: List<WakeOutcome>): FeedbackStats {
            val sorted = outcomes.sortedBy { it.at }
            val fb = sorted.mapNotNull { it.feedback }
            fun avg(xs: List<Int>) = if (xs.isEmpty()) null else xs.average()
            return FeedbackStats(
                mornings = sorted.size,
                avgMood = avg(fb.map { it.mood }),
                avgWakeEase = avg(fb.map { it.wakeEase }),
                avgTiredness = avg(fb.map { it.tiredness }),
                avgSnoozes = if (sorted.isEmpty()) 0.0 else sorted.map { it.snoozes }.average(),
                noSnoozeStreak = sorted.asReversed().takeWhile { it.snoozes == 0 }.size,
                avgEaseNoSnooze = avg(sorted.filter { it.snoozes == 0 }.mapNotNull { it.feedback?.wakeEase }),
                avgEaseWithSnooze = avg(sorted.filter { it.snoozes > 0 }.mapNotNull { it.feedback?.wakeEase }),
            )
        }
    }
}
