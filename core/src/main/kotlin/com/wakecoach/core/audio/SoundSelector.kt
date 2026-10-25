package com.wakecoach.core.audio

import com.wakecoach.core.model.AlarmKind

/** Built-in sounds shipped in res/raw, plus the phone's own ringtone as the last resort. */
enum class BuiltinSound { CHIME_SOFT, BIRDS_MORNING, RISE_BRIGHT, URGENT_BEEP, PHONE_RINGTONE }

sealed interface SoundRef {
    data class Builtin(val sound: BuiltinSound) : SoundRef
    /** A sound the user picked from the phone (content:// URI as text). */
    data class Custom(val uri: String) : SoundRef

    /** Text form stored on the alarm: "builtin:NAME" or a phone sound URI. */
    fun encode(): String = when (this) {
        is Builtin -> PREFIX + sound.name
        is Custom -> uri
    }

    companion object {
        private const val PREFIX = "builtin:"

        fun decode(stored: String?): SoundRef? = when {
            stored.isNullOrBlank() -> null
            stored.startsWith(PREFIX) -> BuiltinSound.entries.firstOrNull { it.name == stored.removePrefix(PREFIX) }?.let(::Builtin)
            else -> Custom(stored)
        }
    }
}

/** One step of the ring: play [sound] from [startAfterSec] seconds after the first ring. */
data class SoundStage(val sound: SoundRef, val startAfterSec: Int)

data class SoundPlan(val stages: List<SoundStage>) {
    init { require(stages.isNotEmpty() && stages.first().startAfterSec == 0) }
}

data class SoundContext(
    val kind: AlarmKind,
    /** 1 normal, 2 important, 3 critical. */
    val importance: Int,
    val hour: Int,
    /** Alarms that already rang today, so back-to-back reminders do not sound alike. */
    val ringsToday: Int,
    val snoozeCount: Int,
    /** A calendar event early that morning makes getting up more urgent. */
    val hasEarlyEvent: Boolean,
    /** The user's own pick for this alarm ([SoundRef.encode] form), or null for automatic. */
    val customUri: String? = null,
)

/**
 * Chooses how the alarm sounds and how it escalates (requirement 6): soft before sunrise,
 * brighter and faster when it matters, and every snooze starts further up the ladder.
 */
object SoundSelector {

    fun choose(c: SoundContext): SoundPlan {
        if (c.kind.isNudge) {
            val first = if (c.ringsToday % 2 == 0) BuiltinSound.CHIME_SOFT else BuiltinSound.BIRDS_MORNING
            val opening = SoundRef.decode(c.customUri) ?: SoundRef.Builtin(first)
            return SoundPlan(listOf(SoundStage(opening, 0), SoundStage(SoundRef.Builtin(BuiltinSound.RISE_BRIGHT), 45)))
        }

        val urgent = c.importance >= 3 || c.hasEarlyEvent
        val opening: SoundRef = SoundRef.decode(c.customUri) ?: SoundRef.Builtin(
            when {
                urgent -> BuiltinSound.RISE_BRIGHT
                c.hour < 6 -> BuiltinSound.CHIME_SOFT
                else -> BuiltinSound.BIRDS_MORNING
            }
        )
        val ladder = listOf(
            opening,
            SoundRef.Builtin(BuiltinSound.URGENT_BEEP),
            SoundRef.Builtin(BuiltinSound.PHONE_RINGTONE),
        )
        // Seconds before stepping up; shorter when it matters, halved by each snooze.
        val base = when {
            urgent -> 30
            c.importance == 2 -> 45
            c.hour < 6 -> 90
            else -> 60
        }
        val step = (base shr c.snoozeCount.coerceAtMost(3)).coerceAtLeast(10)
        val remaining = ladder.drop(c.snoozeCount.coerceAtMost(ladder.size - 1))
        return SoundPlan(remaining.mapIndexed { i, s -> SoundStage(s, i * step) })
    }
}
