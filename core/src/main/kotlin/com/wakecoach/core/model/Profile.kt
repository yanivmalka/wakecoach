package com.wakecoach.core.model

/** How hard the app pushes. Drives challenge difficulty, snooze rules and tactics. */
enum class Intensity(val level: Int) {
    GENTLE(1), BALANCED(2), FIRM(3), HARDCORE(4);

    fun stepUp(): Intensity = entries.getOrElse(ordinal + 1) { this }
    fun stepDown(): Intensity = entries.getOrElse(ordinal - 1) { this }
}

enum class ChallengeType { NONE, MATH, TYPE_PHRASE, SHAKE, STEPS, SCAN_CODE }

data class SnoozePolicy(
    val maxSnoozes: Int,
    val snoozeMinutes: Int,
    /** Each snooze is this many minutes shorter than the previous one (never below 1). */
    val shrinkMinutes: Int = 0,
) {
    val allowed: Boolean get() = maxSnoozes > 0

    fun canSnooze(snoozesSoFar: Int): Boolean = snoozesSoFar < maxSnoozes

    fun minutesFor(snoozeIndex: Int): Int =
        (snoozeMinutes - snoozeIndex * shrinkMinutes).coerceAtLeast(1)
}

data class Profile(
    val id: String,
    val intensity: Intensity,
    val challenges: List<ChallengeType>,
    val snooze: SnoozePolicy,
    /** Seconds from first sound to full volume. */
    val crescendoSeconds: Int,
    val vibrate: Boolean,
)

/** Built-in profiles the user picks from (requirement 5). Names are localized in the app. */
object Profiles {
    val GENTLE = Profile(
        id = "gentle",
        intensity = Intensity.GENTLE,
        challenges = listOf(ChallengeType.NONE),
        snooze = SnoozePolicy(maxSnoozes = 3, snoozeMinutes = 9),
        crescendoSeconds = 90,
        vibrate = false,
    )
    val BALANCED = Profile(
        id = "balanced",
        intensity = Intensity.BALANCED,
        challenges = listOf(ChallengeType.MATH),
        snooze = SnoozePolicy(maxSnoozes = 2, snoozeMinutes = 8, shrinkMinutes = 2),
        crescendoSeconds = 60,
        vibrate = true,
    )
    val FIRM = Profile(
        id = "firm",
        intensity = Intensity.FIRM,
        challenges = listOf(ChallengeType.MATH, ChallengeType.TYPE_PHRASE, ChallengeType.SHAKE),
        snooze = SnoozePolicy(maxSnoozes = 1, snoozeMinutes = 5),
        crescendoSeconds = 30,
        vibrate = true,
    )
    val SOLDIER = Profile(
        id = "soldier",
        intensity = Intensity.HARDCORE,
        challenges = listOf(ChallengeType.MATH, ChallengeType.STEPS),
        snooze = SnoozePolicy(maxSnoozes = 0, snoozeMinutes = 0),
        crescendoSeconds = 5,
        vibrate = true,
    )
    val HEAVY_SLEEPER = Profile(
        id = "heavy_sleeper",
        intensity = Intensity.HARDCORE,
        challenges = listOf(ChallengeType.STEPS, ChallengeType.SCAN_CODE, ChallengeType.SHAKE),
        snooze = SnoozePolicy(maxSnoozes = 1, snoozeMinutes = 4),
        crescendoSeconds = 15,
        vibrate = true,
    )

    val all = listOf(GENTLE, BALANCED, FIRM, SOLDIER, HEAVY_SLEEPER)

    fun byId(id: String): Profile = all.firstOrNull { it.id == id } ?: BALANCED
}
