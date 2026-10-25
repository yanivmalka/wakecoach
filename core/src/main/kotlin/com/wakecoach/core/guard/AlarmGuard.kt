package com.wakecoach.core.guard

import com.wakecoach.core.model.AlarmKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.abs

/** One alarm that actually rang. */
data class RingRecord(val kind: AlarmKind, val label: String, val at: LocalDateTime)

/** A recurring habit learned from the ring log: "Sun-Thu around 16:00, pick up the kids". */
data class Slot(val day: DayOfWeek, val time: LocalTime, val kind: AlarmKind, val label: String) {
    /** Stable key for remembering the user's answers about this slot. */
    val key: String get() = "${day.value}|${kind.name}|${labelKey(label)}|${time.hour}"
}

/** An alarm planned for the target day. */
data class PlannedAlarm(val time: LocalTime, val kind: AlarmKind, val label: String)

/** Things the user said about a slot on a given date (or "stop tracking" with date = null). */
data class GuardDecision(val slotKey: String, val date: LocalDate?, val decision: Decision) {
    enum class Decision { NOT_THIS_TIME, STOP_TRACKING }
}

internal fun labelKey(label: String) = label.lowercase().replace(Regex("\\s+"), " ").trim()

internal fun sameLabel(a: String, b: String): Boolean {
    val x = labelKey(a)
    val y = labelKey(b)
    return x == y || (x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x)))
}

private fun minutesApart(a: LocalTime, b: LocalTime) = abs(ChronoUnit.MINUTES.between(a, b))

object SlotDetector {
    const val WINDOW_MINUTES = 30L

    /**
     * Groups rings by weekday, kind and label, clusters their times within ±30 minutes, and keeps
     * clusters seen in at least [minWeeks] of the last [weeks] weeks before [today].
     */
    fun detect(log: List<RingRecord>, today: LocalDate, weeks: Int = 4, minWeeks: Int = 3): List<Slot> {
        val from = today.minusWeeks(weeks.toLong())
        val recent = log.filter { !it.at.toLocalDate().isBefore(from) && it.at.toLocalDate().isBefore(today) && it.kind != AlarmKind.TEST }
        val slots = mutableListOf<Slot>()
        recent.groupBy { Triple(it.at.dayOfWeek, it.kind, if (it.kind == AlarmKind.WAKE) "" else labelKey(it.label)) }
            .forEach { (_, records) ->
                val clusters = mutableListOf<MutableList<RingRecord>>()
                records.sortedBy { it.at.toLocalTime() }.forEach { r ->
                    val c = clusters.lastOrNull()
                    if (c != null && minutesApart(c.first().at.toLocalTime(), r.at.toLocalTime()) <= WINDOW_MINUTES * 2) c += r
                    else clusters += mutableListOf(r)
                }
                clusters.forEach { c ->
                    val weeksSeen = c.map { it.at.get(IsoFields.WEEK_BASED_YEAR) * 100 + it.at.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.toSet()
                    if (weeksSeen.size >= minWeeks) {
                        val times = c.map { it.at.toLocalTime().toSecondOfDay() }.sorted()
                        val median = LocalTime.ofSecondOfDay(times[times.size / 2].toLong()).withSecond(0)
                        slots += Slot(c.first().at.dayOfWeek, median, c.first().kind, c.last().label)
                    }
                }
            }
        return slots.sortedBy { it.time }
    }
}

sealed interface GuardResult {
    data object NoAction : GuardResult
    /** Regular alarms with nothing planned for [date]; ask once, listing them together. */
    data class Missing(val date: LocalDate, val slots: List<Slot>) : GuardResult
}

object AlarmGuard {

    /**
     * Compares [date]'s regular slots with what is planned for that day. A slot is covered by an
     * alarm of the same kind within ±30 minutes (and, for reminders, a similar label).
     */
    fun evaluate(
        slots: List<Slot>,
        planned: List<PlannedAlarm>,
        date: LocalDate,
        restDays: Set<DayOfWeek>,
        decisions: List<GuardDecision>,
    ): GuardResult {
        if (date.dayOfWeek in restDays) return GuardResult.NoAction
        val missing = slots
            .filter { it.day == date.dayOfWeek }
            .filterNot { slot ->
                decisions.any { d ->
                    d.slotKey == slot.key && (d.decision == GuardDecision.Decision.STOP_TRACKING || d.date == date)
                }
            }
            .filterNot { slot ->
                planned.any { p ->
                    p.kind == slot.kind && minutesApart(p.time, slot.time) <= SlotDetector.WINDOW_MINUTES &&
                        (slot.kind == AlarmKind.WAKE || sameLabel(p.label, slot.label))
                }
            }
        return if (missing.isEmpty()) GuardResult.NoAction else GuardResult.Missing(date, missing)
    }

    /** After three "not this time" answers in a row for a slot, offer to stop tracking it. */
    fun shouldOfferStop(slot: Slot, decisions: List<GuardDecision>): Boolean =
        decisions.filter { it.slotKey == slot.key && it.date != null }
            .sortedBy { it.date }
            .takeLast(3)
            .let { last -> last.size == 3 && last.all { it.decision == GuardDecision.Decision.NOT_THIS_TIME } }
}
