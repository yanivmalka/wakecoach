package com.wakecoach.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime

object MorningDate {
    /** Hour before which "tomorrow" still means the coming morning (a late night, not a new day). */
    const val NEW_DAY_HOUR = 5

    /** The morning that "tomorrow" refers to when the user is going to bed at [now]. */
    fun upcoming(now: LocalDateTime): LocalDate =
        if (now.hour < NEW_DAY_HOUR) now.toLocalDate() else now.toLocalDate().plusDays(1)
}
