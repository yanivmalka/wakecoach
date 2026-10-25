package com.wakecoach.data

import com.wakecoach.core.guard.AlarmGuard
import com.wakecoach.core.guard.GuardDecision
import com.wakecoach.core.guard.GuardResult
import com.wakecoach.core.guard.PlannedAlarm
import com.wakecoach.core.guard.RingRecord
import com.wakecoach.core.guard.Slot
import com.wakecoach.core.guard.SlotDetector
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.schedule.AlarmTimeCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Connects the alarm guard logic in core to the ring log, alarms and saved answers. */
class GuardRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val dao = db.guard()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun logRing(alarm: AlarmEntity, at: Long) {
        if (alarm.alarmKind == AlarmKind.TEST) return
        dao.logRing(RingLogEntity(alarmId = alarm.id, kind = alarm.kind, label = alarm.label, at = at))
        // Keep about two months: enough for four-week habits, small enough to stay fast.
        dao.pruneRings(at - 60L * 24 * 3600 * 1000)
    }

    /** How often [alarm] rang in the last two weeks, to decide whether turning it off deserves a question. */
    suspend fun recentRingCount(alarm: AlarmEntity): Int =
        dao.ringCount(alarm.id, System.currentTimeMillis() - 14L * 24 * 3600 * 1000)

    /**
     * Regular alarms missing on [date]. With [notBefore] (the morning check for today), only
     * slots at least that late are considered, so we never ask about something already past.
     */
    suspend fun missing(date: LocalDate, notBefore: LocalDateTime? = null): List<Slot> {
        val s = settings.current()
        if (!s.guardEnabled) return emptyList()
        val since = date.minusDays(35).atStartOfDay(zone).toInstant().toEpochMilli()
        val log = dao.ringsSince(since).map {
            RingRecord(
                AlarmKind.entries.firstOrNull { k -> k.name == it.kind } ?: AlarmKind.WAKE,
                it.label,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.at), zone),
            )
        }
        val slots = SlotDetector.detect(log, today = date)
        val result = AlarmGuard.evaluate(slots, planned(date), date, restDays(s), decisions())
        val missing = (result as? GuardResult.Missing)?.slots.orEmpty()
        return if (notBefore == null) missing else missing.filter { !date.atTime(it.time).isBefore(notBefore) }
    }

    suspend fun decisions(): List<GuardDecision> = dao.decisions().map {
        GuardDecision(
            it.slotKey,
            it.date?.let(LocalDate::ofEpochDay),
            GuardDecision.Decision.entries.firstOrNull { d -> d.name == it.decision } ?: GuardDecision.Decision.NOT_THIS_TIME,
        )
    }

    suspend fun notThisTime(slots: List<Slot>, date: LocalDate) = slots.forEach {
        dao.decide(GuardDecisionEntity(slotKey = it.key, date = date.toEpochDay(), decision = GuardDecision.Decision.NOT_THIS_TIME.name))
    }

    suspend fun stopTracking(slot: Slot) =
        dao.decide(GuardDecisionEntity(slotKey = slot.key, date = null, decision = GuardDecision.Decision.STOP_TRACKING.name))

    /** One-shot alarms for [slots] on [date], with the usual time and label. */
    suspend fun addAlarms(slots: List<Slot>, date: LocalDate): List<AlarmEntity> = slots.map { slot ->
        val today = LocalDate.now(zone)
        AlarmEntity(
            hour = slot.time.hour,
            minute = slot.time.minute,
            label = slot.label,
            kind = slot.kind.name,
            // A one-shot alarm rings at its next occurrence; skip today when the slot is for a later day.
            skipDate = if (date.isAfter(today)) today.toEpochDay() else null,
        ).let { it.copy(id = db.alarms().upsert(it)) }
    }

    private suspend fun planned(date: LocalDate): List<PlannedAlarm> {
        val startOfDay = date.atStartOfDay(zone).minusSeconds(1)
        val now = ZonedDateTime.now(zone)
        return db.alarms().enabled()
            .filter { it.alarmKind != AlarmKind.TEST }
            .mapNotNull { a ->
                // A one-shot rings at its first occurrence from now (then turns off), so it only
                // covers [date] if that first occurrence falls on it.
                val from = if (a.repeatDays.isEmpty()) maxOf(now, startOfDay) else startOfDay
                val next = AlarmTimeCalculator.nextTrigger(a.time, a.repeatDays, from, a.skipLocalDate)
                if (next.toLocalDate() == date) PlannedAlarm(next.toLocalTime(), a.alarmKind, a.label) else null
            }
    }

    private fun restDays(s: UserSettings): Set<DayOfWeek> = s.restDays.mapNotNull { v -> DayOfWeek.entries.firstOrNull { it.value == v } }.toSet()

    fun shouldOfferStop(slot: Slot, decisions: List<GuardDecision>) = AlarmGuard.shouldOfferStop(slot, decisions)
}
