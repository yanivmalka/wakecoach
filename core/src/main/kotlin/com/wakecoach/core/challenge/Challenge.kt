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
