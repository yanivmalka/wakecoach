package com.wakecoach.core

import com.wakecoach.core.audio.BuiltinSound
import com.wakecoach.core.audio.SoundContext
import com.wakecoach.core.audio.SoundRef
import com.wakecoach.core.audio.SoundSelector
import com.wakecoach.core.model.AlarmKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SoundSelectorTest {
    private fun ctx(
        kind: AlarmKind = AlarmKind.WAKE, importance: Int = 1, hour: Int = 7, rings: Int = 0,
        snoozes: Int = 0, early: Boolean = false, custom: String? = null,
    ) = SoundContext(kind, importance, hour, rings, snoozes, early, custom)

    private fun first(c: SoundContext) = SoundSelector.choose(c).stages.first().sound

    @Test fun softBeforeSunriseBirdsAfter() {
        assertEquals(SoundRef.Builtin(BuiltinSound.CHIME_SOFT), first(ctx(hour = 5)))
        assertEquals(SoundRef.Builtin(BuiltinSound.BIRDS_MORNING), first(ctx(hour = 7)))
    }

    @Test fun urgentStartsBrightAndEscalatesFast() {
        val plan = SoundSelector.choose(ctx(importance = 3))
        assertEquals(SoundRef.Builtin(BuiltinSound.RISE_BRIGHT), plan.stages[0].sound)
        assertEquals(30, plan.stages[1].startAfterSec)
        assertEquals(SoundRef.Builtin(BuiltinSound.RISE_BRIGHT), first(ctx(early = true)))
    }

    @Test fun snoozeClimbsTheLadder() {
        val plan = SoundSelector.choose(ctx(snoozes = 1))
        assertEquals(SoundRef.Builtin(BuiltinSound.URGENT_BEEP), plan.stages[0].sound)
        assertEquals(0, plan.stages[0].startAfterSec)
        assertEquals(30, plan.stages[1].startAfterSec) // 60 halved
        assertEquals(SoundRef.Builtin(BuiltinSound.PHONE_RINGTONE), first(ctx(snoozes = 5)))
    }

    @Test fun customSoundOpensThenEscalates() {
        val plan = SoundSelector.choose(ctx(custom = "content://x"))
        assertEquals(SoundRef.Custom("content://x"), plan.stages[0].sound)
        assertEquals(3, plan.stages.size)
    }

    @Test fun backToBackRemindersDiffer() {
        assertNotEquals(first(ctx(kind = AlarmKind.REMINDER, rings = 0)), first(ctx(kind = AlarmKind.REMINDER, rings = 1)))
    }

    @Test fun storedPickRoundTrips() {
        val b = SoundRef.Builtin(BuiltinSound.BIRDS_MORNING)
        assertEquals(b, SoundRef.decode(b.encode()))
        assertEquals(b, first(ctx(hour = 4, custom = b.encode())))
        assertEquals(null, SoundRef.decode(""))
    }
}
