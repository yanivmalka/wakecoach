package com.wakecoach.ui.skills

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wakecoach.R
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.skill.Param
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.Skills

fun skillEmoji(type: ChallengeType): String = when (type) {
    ChallengeType.CATCH_SUN -> "☀️"
    ChallengeType.BREATHE -> "🌬️"
    ChallengeType.VOICE -> "🗣️"
    ChallengeType.STAND_UP -> "🧍"
    ChallengeType.SHAKE -> "📳"
    ChallengeType.STEPS -> "🚶"
    else -> "⏰"
}

@StringRes
fun skillName(type: ChallengeType): Int = when (type) {
    ChallengeType.CATCH_SUN -> R.string.skill_catch_sun
    ChallengeType.BREATHE -> R.string.skill_breathe
    ChallengeType.VOICE -> R.string.skill_voice
    ChallengeType.STAND_UP -> R.string.skill_stand_up
    ChallengeType.SHAKE -> R.string.skill_shake
    ChallengeType.STEPS -> R.string.skill_steps
    else -> R.string.skill_other
}

@StringRes
fun skillDescription(type: ChallengeType): Int = when (type) {
    ChallengeType.CATCH_SUN -> R.string.skill_catch_sun_desc
    ChallengeType.BREATHE -> R.string.skill_breathe_desc
    ChallengeType.VOICE -> R.string.skill_voice_desc
    ChallengeType.STAND_UP -> R.string.skill_stand_up_desc
    ChallengeType.SHAKE -> R.string.skill_shake_desc
    ChallengeType.STEPS -> R.string.skill_steps_desc
    else -> R.string.skill_other
}

@StringRes
fun paramName(type: ChallengeType, p: Param): Int = when (p) {
    Param.ROUNDS -> R.string.param_rounds
    Param.GRID -> R.string.param_grid
    Param.LOOKALIKE -> R.string.param_lookalike
    Param.COUNT -> if (type == ChallengeType.STEPS) R.string.param_steps else R.string.param_shakes
    Param.SECONDS -> R.string.param_seconds
    Param.INHALE -> R.string.param_inhale
    Param.HOLD -> R.string.param_hold
    Param.EXHALE -> R.string.param_exhale
    Param.WORDS -> R.string.param_words
}

/** How a value reads: "4×4", "on/off", or the number. */
@Composable
fun paramValue(p: Param, v: Int): String = when (p) {
    Param.GRID -> "$v×$v"
    Param.LOOKALIKE -> stringResource(if (v == 1) R.string.on else R.string.off)
    else -> v.toString()
}

/** One line, e.g. "Rounds 5 · Grid 3×3". */
@Composable
fun skillSummary(config: SkillConfig): String {
    val parts = Skills.specs(config.type).map { s ->
        stringResource(paramName(config.type, s.param)) + " " + paramValue(s.param, config[s.param])
    }
    return parts.joinToString(" · ")
}
