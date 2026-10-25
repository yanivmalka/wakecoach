package com.wakecoach.core.challenge

import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import kotlin.random.Random

/** A task that must be completed to dismiss the alarm (requirement 3). */
sealed interface Challenge {
    val type: ChallengeType

    data object None : Challenge {
        override val type = ChallengeType.NONE
    }

    data class Math(val question: String, val answer: Int) : Challenge {
        override val type = ChallengeType.MATH
        fun check(input: String): Boolean = input.trim().toIntOrNull() == answer
    }

    data class TypePhrase(val phrase: String) : Challenge {
        override val type = ChallengeType.TYPE_PHRASE

        /** Case- and whitespace-insensitive, so sleepy thumbs are not punished for spacing. */
        fun check(input: String): Boolean = normalize(input) == normalize(phrase)

        private fun normalize(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    data class Shake(val shakes: Int) : Challenge {
        override val type = ChallengeType.SHAKE
    }

    data class Steps(val steps: Int) : Challenge {
        override val type = ChallengeType.STEPS
    }

    /** Guided breathing (requirement 12): slow in, hold, out, a few rounds. Can't be rushed. */
    data class Breathe(val rounds: Int, val inhaleSec: Int = 4, val holdSec: Int = 2, val exhaleSec: Int = 5) : Challenge {
        override val type = ChallengeType.BREATHE
        val totalSec: Int get() = rounds * (inhaleSec + holdSec + exhaleSec)
    }

    /** Spoken dialog (requirement 12): answer a question out loud with a few words. */
    data class Voice(val question: VoiceQuestion, val minWords: Int) : Challenge {
        override val type = ChallengeType.VOICE
        fun check(answer: String): Boolean = answer.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= minWords
    }

    /** Get out of bed: hold the phone upright, standing, for a few seconds. */
    data class StandUp(val seconds: Int) : Challenge {
        override val type = ChallengeType.STAND_UP
    }

    /** User registered a barcode/QR somewhere away from the bed (e.g. the bathroom). */
    data class ScanCode(val expected: String) : Challenge {
        override val type = ChallengeType.SCAN_CODE
        fun check(scanned: String): Boolean = scanned == expected
    }
}

class ChallengeGenerator(private val random: Random = Random.Default) {

    fun generate(
        type: ChallengeType,
        intensity: Intensity,
        phrases: List<String>,
        registeredCode: String?,
    ): Challenge = when (type) {
        ChallengeType.NONE -> Challenge.None
        ChallengeType.MATH -> math(intensity)
        ChallengeType.TYPE_PHRASE ->
            if (phrases.isEmpty()) math(intensity) else Challenge.TypePhrase(phrases.random(random))
        ChallengeType.SHAKE -> Challenge.Shake(shakes = 15 * intensity.level)
        ChallengeType.STEPS -> Challenge.Steps(steps = 10 + 10 * intensity.level)
        // Without a registered code the scan challenge cannot be solved; fall back to steps.
        ChallengeType.SCAN_CODE ->
            registeredCode?.let { Challenge.ScanCode(it) } ?: Challenge.Steps(10 + 10 * intensity.level)
        ChallengeType.BREATHE -> Challenge.Breathe(rounds = 2 + intensity.level)
        ChallengeType.VOICE -> Challenge.Voice(VoiceQuestion.entries.random(random), minWords = 2 + intensity.level / 2)
        ChallengeType.STAND_UP -> Challenge.StandUp(seconds = 4 + 2 * intensity.level)
    }

    fun math(intensity: Intensity): Challenge.Math = when (intensity) {
        Intensity.GENTLE -> {
            val a = random.nextInt(2, 10); val b = random.nextInt(2, 10)
            Challenge.Math("$a + $b", a + b)
        }
        Intensity.BALANCED -> {
            val a = random.nextInt(11, 50); val b = random.nextInt(11, 50)
            Challenge.Math("$a + $b", a + b)
        }
        Intensity.FIRM -> {
            val a = random.nextInt(3, 13); val b = random.nextInt(3, 13); val c = random.nextInt(5, 40)
            Challenge.Math("$a × $b + $c", a * b + c)
        }
        Intensity.HARDCORE -> {
            val a = random.nextInt(12, 30); val b = random.nextInt(3, 10); val c = random.nextInt(10, 99)
            Challenge.Math("$a × $b − $c", a * b - c)
        }
    }

    /** Picks one of the profile's challenge types at random each morning to avoid habituation. */
    fun pickType(allowed: List<ChallengeType>): ChallengeType =
        if (allowed.isEmpty()) ChallengeType.NONE else allowed.random(random)
}

/** Questions for the voice challenge; the app localizes them. */
enum class VoiceQuestion { FIRST_TASK, GRATEFUL, WHY_UP, TODAY_GOAL }
