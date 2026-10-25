package com.wakecoach.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class MorningEvent(val start: LocalTime, val title: String)

/** Reads the first timed event of a morning from the phone's calendars (optional, READ_CALENDAR). */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** First non-all-day event between midnight and noon on [date], or null. */
    fun firstMorningEvent(date: LocalDate): MorningEvent? {
        if (!hasPermission()) return null
        val zone = ZoneId.systemDefault()
        val from = ZonedDateTime.of(date, LocalTime.MIDNIGHT, zone).toInstant().toEpochMilli()
        val to = ZonedDateTime.of(date, LocalTime.NOON, zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toString()).appendPath(to.toString()).build()
        val projection = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) != 0) continue
                    val begin = c.getLong(0)
                    if (begin !in from until to) continue
                    val time = java.time.Instant.ofEpochMilli(begin).atZone(zone).toLocalTime()
                    return@use MorningEvent(time, c.getString(1).orEmpty())
                }
                null
            }
        }.getOrNull()
    }
}
