package com.wakecoach.core.adapt

import com.wakecoach.core.feedback.WakeOutcome
import com.wakecoach.core.model.Intensity
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.random.Random

/** What the engine knows the evening before / the moment a wake-up rings. */
data class MorningInput(
    /** Past wake-ups, any order. */
    val history: List<WakeOutcome>,
    val zone: ZoneId,
    val day: DayOfWeek,
    /** The alarm's own setting: 1 normal, 2 important, 3 critical. */
    val importance: Int,
    /** Estimated sleep before this alarm, if known (bedtime sensing comes later). */
    val expectedSleepHours: Double? = null,
    /** The profile the user picked; the engine adjusts around it. */
    val baseIntensity: Intensity,
)

enum class RiskReason { RECENT_SNOOZES, HARD_WEEKDAY, SHORT_SLEEP, IMPORTANT_DAY, TIRED_LATELY, GOOD_STREAK, SURPRISE }

data class MorningPlan(
    /** 0 (will surely get up) .. 100 (very likely to oversleep). */
    val risk: Int,
    val intensity: Intensity,
    /** An unannounced harder morning, so a good streak never turns into autopilot (requirement 13). */
    val surprise: Boolean,
    val reasons: List<RiskReason>,
)

/**
 * Rule-based "morning risk" from the user's own history (requirement 4): recent snoozes,
 * how this weekday usually goes, sleep, stakes and tiredness. It raises intensity when the
 * morning looks hard and eases off only slowly after success, never below one step under
 * the user's chosen profile, and still throws in an occasional surprise.
 */
class AdaptiveEngine(private val random: Random = Random.Default) {

    fun plan(input: MorningInput): MorningPlan {
        val sorted = input.history.sortedBy { it.at }
        val recent = sorted.takeLast(7)
        val reasons = mutableListOf<RiskReason>()
        var risk = 20

        val avgSnoozes = if (recent.isEmpty()) 0.0 else recent.map { it.snoozes }.average()
        if (avgSnoozes >= 0.5) {
            risk += (avgSnoozes * 15).toInt().coerceAtMost(40)
            reasons += RiskReason.RECENT_SNOOZES
        }

        val sameDay = sorted.filter { it.at.atZone(input.zone).dayOfWeek == input.day }.takeLast(4)
        if (sameDay.size >= 2) {
            val badShare = sameDay.count { it.snoozes > 0 }.toDouble() / sameDay.size
            if (badShare >= 0.5) {
                risk += (badShare * 25).toInt()
                reasons += RiskReason.HARD_WEEKDAY
            }
        }

        input.expectedSleepHours?.let { h ->
            when {
                h < 6 -> { risk += 20; reasons += RiskReason.SHORT_SLEEP }
                h < 7 -> { risk += 10; reasons += RiskReason.SHORT_SLEEP }
            }
        }

        when (input.importance) {
            3 -> { risk += 10; reasons += RiskReason.IMPORTANT_DAY }
            2 -> { risk += 5; reasons += RiskReason.IMPORTANT_DAY }
        }

        val tiredness = recent.mapNotNull { it.feedback?.tiredness }
        if (tiredness.size >= 3 && tiredness.average() >= 4.0) {
            risk += 10
            reasons += RiskReason.TIRED_LATELY
        }

        val streak = sorted.asReversed().takeWhile { it.snoozes == 0 }.size
        if (streak >= 3) {
            risk -= if (streak >= 5) 15 else 8
            reasons += RiskReason.GOOD_STREAK
        }

        risk = risk.coerceIn(0, 100)

        var intensity = input.baseIntensity
        when {
            risk >= 80 -> intensity = intensity.stepUp().stepUp()
            risk >= 60 -> intensity = intensity.stepUp()
            // Ease off only after a real streak, and only one step: never give up entirely.
            risk <= 15 && streak >= 5 -> intensity = intensity.stepDown()
        }

        // The better the streak, the more a surprise is warranted; it never announces itself.
        val surprise = streak >= 3 && random.nextDouble() < SURPRISE_CHANCE
        if (surprise) {
            intensity = maxOf(intensity, input.baseIntensity.stepUp())
            reasons += RiskReason.SURPRISE
        }

        return MorningPlan(risk, intensity, surprise, reasons)
    }

    companion object {
        const val SURPRISE_CHANCE = 0.2
    }
}
