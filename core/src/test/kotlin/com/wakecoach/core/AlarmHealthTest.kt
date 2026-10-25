package com.wakecoach.core

import com.wakecoach.core.health.AlarmHealth
import com.wakecoach.core.health.HealthCheck
import com.wakecoach.core.health.HealthItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmHealthTest {
    @Test fun summaryCountsCritical() {
        val checks = listOf(
            HealthCheck(HealthItem.NOTIFICATIONS, ok = true),
            HealthCheck(HealthItem.ALARM_VOLUME, ok = false),
            HealthCheck(HealthItem.BATTERY_OPTIMIZATION, ok = false),
        )
        val s = AlarmHealth.summarize(checks)
        assertEquals(2, s.problems)
        assertEquals(1, s.critical)
        assertEquals(
            listOf(HealthItem.BATTERY_OPTIMIZATION, HealthItem.ALARM_VOLUME, HealthItem.NOTIFICATIONS),
            AlarmHealth.ordered(checks).map { it.item },
        )
    }

    @Test fun allGood() {
        assertTrue(AlarmHealth.summarize(listOf(HealthCheck(HealthItem.DND_SILENCE, true))).allGood)
    }
}
