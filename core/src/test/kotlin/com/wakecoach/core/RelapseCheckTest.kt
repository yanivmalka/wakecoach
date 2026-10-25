package com.wakecoach.core

import com.wakecoach.core.model.Intensity
import com.wakecoach.core.wake.RelapseCheck
import com.wakecoach.core.wake.RelapseVerdict
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelapseCheckTest {
    @Test fun verdicts() {
        assertEquals(RelapseVerdict.AWAKE, RelapseCheck.decide(stepsSinceDismiss = 0, interactive = true))
        assertEquals(RelapseVerdict.AWAKE, RelapseCheck.decide(40, interactive = false))
        assertEquals(RelapseVerdict.RELAPSE, RelapseCheck.decide(3, interactive = false))
        assertEquals(RelapseVerdict.ASK, RelapseCheck.decide(null, interactive = false))
    }

    @Test fun delaysStayInRangeAndHarderIsSooner() {
        val r = Random(3)
        repeat(100) {
            assertTrue(RelapseCheck.delayMinutes(Intensity.HARDCORE, r) in 4..7)
            assertTrue(RelapseCheck.delayMinutes(Intensity.GENTLE, r) in 7..10)
        }
    }
}
