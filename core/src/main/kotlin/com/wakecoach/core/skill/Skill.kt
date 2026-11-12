package com.wakecoach.core.skill

import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity

/** A setting of a skill. Names are stored, so never rename an entry. */
enum class Param { ROUNDS, GRID, LOOKALIKE, COUNT, SECONDS, INHALE, HOLD, EXHALE, WORDS }

/** One adjustable setting: range, step and the default used when nothing was chosen. */
data class ParamSpec(val param: Param, val min: Int, val max: Int, val default: Int, val step: Int = 1) {
    fun clamp(v: Int): Int = v.coerceIn(min, max)
}

/**
 * A wake-up method ("skill") with its settings, e.g. catch-the-sun with 6 rounds on a 4x4 grid.
 * Stored as text by [encode]; unknown or out-of-range values fall back to the defaults.
 */
data class SkillConfig(val type: ChallengeType, val values: Map<Param, Int> = emptyMap()) {
    operator fun get(p: Param): Int {
        val spec = Skills.spec(type, p) ?: return values[p] ?: 0
        return spec.clamp(values[p] ?: spec.default)
    }

    fun with(p: Param, v: Int): SkillConfig = copy(values = values + (p to v))

    /** All settings with defaults filled in, in display order. */
    fun resolved(): Map<Param, Int> = Skills.specs(type).associate { it.param to get(it.param) }

    fun encode(): String = type.name + "|" + resolved().entries.joinToString(",") { "${it.key.name}=${it.value}" }

    companion object {
        fun decode(s: String): SkillConfig? {
            val parts = s.trim().split('|', limit = 2)
            val type = ChallengeType.entries.firstOrNull { it.name == parts[0] } ?: return null
            val values = parts.getOrNull(1).orEmpty().split(',').mapNotNull { kv ->
                val (k, v) = kv.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val p = Param.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                v.toIntOrNull()?.let { p to it }
            }.toMap()
            return SkillConfig(type, values)
        }

        fun encodeList(list: List<SkillConfig>): String = list.joinToString("\n") { it.encode() }
        fun decodeList(s: String): List<SkillConfig> = s.lines().filter { it.isNotBlank() }.mapNotNull(::decode)
    }
}

/** The skills a user can practice, tune and put in a profile. */
object Skills {
    /**
     * From light to heavy: what it takes from someone half asleep. Looking and tapping, then
     * breathing, talking, standing, and finally moving the whole body. Math and typing are left out.
     */
    val all: List<ChallengeType> = listOf(
        ChallengeType.CATCH_SUN, ChallengeType.BREATHE, ChallengeType.VOICE,
        ChallengeType.STAND_UP, ChallengeType.SHAKE, ChallengeType.STEPS,
    )

    fun weight(type: ChallengeType): Int = all.indexOf(type).let { if (it < 0) all.size else it }

    /** Stable sort from light to heavy; equal skills keep the user's order. */
    fun <T> lightToHeavy(items: List<T>, typeOf: (T) -> ChallengeType): List<T> = items.sortedBy { weight(typeOf(it)) }

    fun specs(type: ChallengeType): List<ParamSpec> = when (type) {
        ChallengeType.CATCH_SUN -> listOf(
            ParamSpec(Param.ROUNDS, 2, 12, 5),
            ParamSpec(Param.GRID, 3, 5, 3),
            ParamSpec(Param.LOOKALIKE, 0, 1, 0),
        )
        ChallengeType.BREATHE -> listOf(
            ParamSpec(Param.ROUNDS, 1, 8, 3),
            ParamSpec(Param.INHALE, 2, 8, 4),
            ParamSpec(Param.HOLD, 0, 8, 2),
            ParamSpec(Param.EXHALE, 2, 10, 5),
        )
        ChallengeType.VOICE -> listOf(ParamSpec(Param.WORDS, 1, 10, 3))
        ChallengeType.STAND_UP -> listOf(ParamSpec(Param.SECONDS, 3, 60, 8))
        ChallengeType.SHAKE -> listOf(ParamSpec(Param.COUNT, 5, 100, 30, step = 5))
        ChallengeType.STEPS -> listOf(ParamSpec(Param.COUNT, 5, 100, 26))
        else -> emptyList()
    }

    fun spec(type: ChallengeType, p: Param): ParamSpec? = specs(type).firstOrNull { it.param == p }

    /** What a built-in profile of [intensity] would use, so practice and plans match real mornings. */
    fun defaults(type: ChallengeType, intensity: Intensity): SkillConfig {
        val lvl = intensity.level
        return when (type) {
            ChallengeType.CATCH_SUN -> SkillConfig(type, mapOf(
                Param.ROUNDS to listOf(4, 5, 6, 7)[lvl - 1],
                Param.GRID to if (lvl <= 2) 3 else 4,
                Param.LOOKALIKE to if (intensity == Intensity.HARDCORE) 1 else 0,
            ))
            ChallengeType.BREATHE -> SkillConfig(type, mapOf(Param.ROUNDS to 2 + lvl))
            ChallengeType.VOICE -> SkillConfig(type, mapOf(Param.WORDS to 2 + lvl / 2))
            ChallengeType.STAND_UP -> SkillConfig(type, mapOf(Param.SECONDS to 4 + 2 * lvl))
            ChallengeType.SHAKE -> SkillConfig(type, mapOf(Param.COUNT to 15 * lvl))
            ChallengeType.STEPS -> SkillConfig(type, mapOf(Param.COUNT to 10 + 8 * lvl))
            else -> SkillConfig(type)
        }
    }

    /**
     * A ready plan for a new profile, light to heavy: open the eyes, then the body, then get up.
     * The user edits it from here.
     */
    fun suggestedPlan(intensity: Intensity): List<SkillConfig> = when (intensity) {
        Intensity.GENTLE -> listOf(ChallengeType.CATCH_SUN)
        Intensity.BALANCED -> listOf(ChallengeType.CATCH_SUN, ChallengeType.BREATHE)
        Intensity.FIRM -> listOf(ChallengeType.CATCH_SUN, ChallengeType.STAND_UP, ChallengeType.VOICE)
        Intensity.HARDCORE -> listOf(ChallengeType.CATCH_SUN, ChallengeType.STAND_UP, ChallengeType.STEPS)
    }.map { defaults(it, intensity) }.let { plan -> lightToHeavy(plan) { it.type } }
}

/** How a profile's skills are used on a morning. */
enum class SkillMode {
    /** All of them, one after the other, to turn the alarm off. */
    CHAIN,

    /** One per ring: the first on the first ring, the next after each snooze. */
    ESCALATE,
}
