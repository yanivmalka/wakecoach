package com.wakecoach.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmTimeCalculator {

    /**
     * Next moment the alarm should fire strictly after [now].
     * Empty [repeatDays] means a one-shot alarm (today if still ahead, else tomorrow).
     * A wall-clock time that falls into a DST gap is shifted forward by the zone rules.
     * [skipDate] ("skip once") passes over the occurrence on that day.
     */
    fun nextTrigger(
        time: LocalTime,
        repeatDays: Set<DayOfWeek>,
        now: ZonedDateTime,
        skipDate: LocalDate? = null,
    ): ZonedDateTime {
        val zone: ZoneId = now.zone
        for (offset in 0..14) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (repeatDays.isNotEmpty() && date.dayOfWeek !in repeatDays) continue
            if (date == skipDate) continue
            val candidate = ZonedDateTime.of(date, time, zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("unreachable: a matching day always exists within 15 days")
    }
}
