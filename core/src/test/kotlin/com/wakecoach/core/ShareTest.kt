package com.wakecoach.core

import com.wakecoach.core.share.Buddy
import com.wakecoach.core.share.WakeStatus
import com.wakecoach.core.share.WakeStatusTracker
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareTest {
    private fun t(min: Long) = Instant.ofEpochSecond(min * 60)

    @Test fun notUpReportedOncePerThreshold() {
        val sent = mutableListOf<WakeStatus>()
        val tracker = WakeStatusTracker({ sent += it })
        tracker.ringing(t(0))
        tracker.snoozed(t(5), 1)
        tracker.tick(t(9)); tracker.tick(t(10)); tracker.tick(t(11))
        tracker.tick(t(25))
        tracker.dismissed(t(26))
        tracker.tick(t(40))
        val notUp = sent.filterIsInstance<WakeStatus.NotUp>().map { it.minutes }
        assertEquals(listOf(10L, 20L), notUp)
        assertEquals(26L, (sent.last() as WakeStatus.Awake).minutesAfterAlarm)
    }

    @Test fun lateTickJumpsToHighestThresholdOnly() {
        val sent = mutableListOf<WakeStatus>()
        val tracker = WakeStatusTracker({ sent += it })
        tracker.ringing(t(0)); tracker.tick(t(30)); tracker.tick(t(31))
        assertEquals(listOf(20L), sent.filterIsInstance<WakeStatus.NotUp>().map { it.minutes })
    }

    @Test fun buddyNeedsMutualConsent() {
        val b = Buddy("u1", "Dan", invitedByMe = true, acceptedByThem = false, acceptedByMe = true)
        assertFalse(b.active)
        assertTrue(b.copy(acceptedByThem = true).mayNudge)
        assertFalse(b.copy(acceptedByThem = true, revoked = true).active)
    }
}
