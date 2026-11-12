package com.wakecoach.core.challenge

import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import com.wakecoach.core.model.Profile
import com.wakecoach.core.skill.Param
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.SkillMode
import com.wakecoach.core.skill.Skills
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

    /**
     * "Catch the sun": each round shows a grid of night symbols with one sun in a new place; tap
     * it. Needs open eyes and a moving hand, not concentration. A wrong tap costs nothing.
     */
    data class CatchSun(val boards: List<SunBoard>) : Challenge {
        override val type = ChallengeType.CATCH_SUN
        val rounds: Int get() = boards.size
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
        ChallengeType.CATCH_SUN -> catchSun(intensity)
        ChallengeType.TYPE_PHRASE ->
            if (phrases.isEmpty()) catchSun(intensity) else Challenge.TypePhrase(phrases.random(random))
        ChallengeType.SHAKE -> Challenge.Shake(shakes = 15 * intensity.level)
        ChallengeType.STEPS -> Challenge.Steps(steps = 10 + 8 * intensity.level)
        // Without a registered code the scan challenge cannot be solved; fall back to steps.
        ChallengeType.SCAN_CODE ->
            registeredCode?.let { Challenge.ScanCode(it) } ?: Challenge.Steps(10 + 8 * intensity.level)
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

    /**
     * Gentle mornings: few rounds on a 3x3 grid. Harder ones: more rounds on 4x4, and the hardest
     * adds a full moon, the one look-alike, so the eyes have to actually open.
     */
    fun catchSun(intensity: Intensity): Challenge.CatchSun =
        fromConfig(Skills.defaults(ChallengeType.CATCH_SUN, intensity), emptyList()) as Challenge.CatchSun

    private fun catchSun(rounds: Int, columns: Int, lookalike: Boolean): Challenge.CatchSun {
        val decoys = if (lookalike) SunBoard.NIGHT + SunBoard.LOOKALIKE else SunBoard.NIGHT
        var last = -1
        val boards = List(rounds) {
            val cells = columns * columns
            // Never the same cell twice in a row, so each round needs a fresh look.
            val sun = (0 until cells).filter { it != last }.random(random).also { last = it }
            SunBoard(columns, List(cells) { i -> if (i == sun) SunBoard.SUN else decoys.random(random) }, sun)
        }
        return Challenge.CatchSun(boards)
    }

    /** Builds a challenge from a tuned skill (practice area and user profiles). */
    fun fromConfig(config: SkillConfig, phrases: List<String>): Challenge = when (config.type) {
        ChallengeType.CATCH_SUN -> catchSun(config[Param.ROUNDS], config[Param.GRID], config[Param.LOOKALIKE] == 1)
        ChallengeType.BREATHE -> Challenge.Breathe(
            rounds = config[Param.ROUNDS], inhaleSec = config[Param.INHALE],
            holdSec = config[Param.HOLD], exhaleSec = config[Param.EXHALE],
        )
        ChallengeType.VOICE -> Challenge.Voice(VoiceQuestion.entries.random(random), minWords = config[Param.WORDS])
        ChallengeType.STAND_UP -> Challenge.StandUp(seconds = config[Param.SECONDS])
        ChallengeType.SHAKE -> Challenge.Shake(shakes = config[Param.COUNT])
        ChallengeType.STEPS -> Challenge.Steps(steps = config[Param.COUNT])
        else -> generate(config.type, Intensity.BALANCED, phrases, registeredCode = null)
    }

    /**
     * The challenges for one ring of [profile]. A custom profile runs its skills in order
     * ([SkillMode.CHAIN]) or one per ring ([SkillMode.ESCALATE]); a built-in picks one at random.
     */
    fun forRing(
        profile: Profile,
        snoozeCount: Int,
        phrases: List<String>,
        registeredCode: String? = null,
        fallback: List<ChallengeType> = profile.challenges,
    ): List<Challenge> {
        val skills = profile.skills
        if (skills.isEmpty()) return listOf(generate(pickType(fallback), profile.intensity, phrases, registeredCode))
        return when (profile.skillMode) {
            SkillMode.CHAIN -> skills.map { fromConfig(it, phrases) }
            SkillMode.ESCALATE -> listOf(fromConfig(skills[snoozeCount.coerceIn(0, skills.size - 1)], phrases))
        }
    }

    /** Picks one of the profile's challenge types at random each morning to avoid habituation. */
    fun pickType(allowed: List<ChallengeType>): ChallengeType =
        if (allowed.isEmpty()) ChallengeType.NONE else allowed.random(random)
}

/** Questions for the voice challenge; the app localizes them. */
enum class VoiceQuestion { FIRST_TASK, GRATEFUL, WHY_UP, TODAY_GOAL }

/** One round of [Challenge.CatchSun]: a square grid, row by row, with the sun at [sunIndex]. */
data class SunBoard(val columns: Int, val cells: List<String>, val sunIndex: Int) {
    fun isSun(index: Int): Boolean = index == sunIndex

    companion object {
        const val SUN = "☀️"
        val NIGHT = listOf("🌙", "⭐", "☁️", "💤", "🌑")
        const val LOOKALIKE = "🌕"
    }
}
