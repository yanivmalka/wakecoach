package com.wakecoach.core.schedule

import java.time.Duration
import java.time.ZonedDateTime

data class BedtimePlan(
    /** When to show "start winding down". */
    val reminderAt: ZonedDateTime,
    /** When the user should be in bed, lights out. */
    val lightsOutAt: ZonedDateTime,
)

object BedtimeCalculator {

    /**
     * Works backwards from the wake-up time: sleep goal plus the time the user
     * usually needs to fall asleep, then a wind-down window before that.
     */
    fun plan(
        wakeAt: ZonedDateTime,
        sleepGoal: Duration,
        fallAsleepMinutes: Int = 15,
        windDownMinutes: Int = 30,
    ): BedtimePlan {
        val lightsOut = wakeAt.minus(sleepGoal).minusMinutes(fallAsleepMinutes.toLong())
        return BedtimePlan(
            reminderAt = lightsOut.minusMinutes(windDownMinutes.toLong()),
            lightsOutAt = lightsOut,
        )
    }
}
