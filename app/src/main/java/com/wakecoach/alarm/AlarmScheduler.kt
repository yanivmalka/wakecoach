package com.wakecoach.alarm

import com.wakecoach.data.repeatDays
import com.wakecoach.data.time
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.schedule.AlarmTimeCalculator
import com.wakecoach.core.schedule.BedtimeCalculator
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.SettingsRepository
import com.wakecoach.data.WakeRepository
import com.wakecoach.data.alarmKind
import com.wakecoach.data.skipLocalDate
import java.time.LocalTime
import com.wakecoach.ui.MainActivity
import java.time.Duration
import java.time.ZonedDateTime

class AlarmScheduler(
    private val context: Context,
    private val repository: WakeRepository,
    private val settings: SettingsRepository,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    /** Re-arms every enabled alarm and the bedtime reminder. Safe to call any time. */
    suspend fun rescheduleAll() {
        val now = ZonedDateTime.now()
        // Test rows arm themselves via scheduleTest and are deleted when they ring.
        val alarms = repository.alarms.enabled().filter { it.alarmKind != AlarmKind.TEST }
        alarms.forEach { schedule(it, now) }
        scheduleBedtime(alarms, now)
        scheduleGuard(now)
    }

    /**
     * The alarm guard looks ahead twice a day: 21:00 checks tomorrow, 08:00 checks the rest of
     * today's reminders. Inexact is fine; it only asks a question.
     */
    private suspend fun scheduleGuard(now: ZonedDateTime) {
        val pi = PendingIntent.getBroadcast(
            context, GUARD_CODE, Intent(context, GuardReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!settings.current().guardEnabled) return
        val next = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))
            .flatMap { t -> listOf(now.with(t), now.plusDays(1).with(t)) }
            .filter { it.isAfter(now) }
            .min()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }

    fun schedule(alarm: AlarmEntity, now: ZonedDateTime = ZonedDateTime.now()) {
        val at = AlarmTimeCalculator.nextTrigger(alarm.time, alarm.repeatDays, now, alarm.skipLocalDate)
        val extras = RingRequest(alarm.id, at.toInstant().toEpochMilli(), firstRingAt = 0, snoozeCount = 0)
        setAlarmClock(at.toInstant().toEpochMilli(), ringIntent(alarm.id.toInt(), extras))
    }

    /** Rings [alarm] (a TEST row) after [seconds], through the same path as a real alarm. */
    fun scheduleTest(alarm: AlarmEntity, seconds: Int) {
        val at = System.currentTimeMillis() + seconds * 1000L
        setAlarmClock(at, ringIntent(alarm.id.toInt(), RingRequest(alarm.id, at, firstRingAt = 0, snoozeCount = 0)))
    }

    /**
     * Whether the system still holds this alarm's PendingIntent. They vanish after a reboot or a
     * force-stop if the boot receiver was blocked, which is exactly the silent failure to catch.
     */
    fun isArmed(alarm: AlarmEntity): Boolean = PendingIntent.getBroadcast(
        context, alarm.id.toInt(), Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    fun cancel(alarm: AlarmEntity) {
        am.cancel(ringIntent(alarm.id.toInt(), null))
        am.cancel(ringIntent(alarm.id.toInt() + SNOOZE_OFFSET, null))
    }

    fun scheduleSnooze(request: RingRequest, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarmClock(at, ringIntent(request.alarmId.toInt() + SNOOZE_OFFSET, request))
    }

    fun scheduleFollowUp(sessionId: Long, delay: Duration) {
        val pi = PendingIntent.getBroadcast(
            context, FOLLOW_UP_CODE,
            Intent(context, FollowUpReceiver::class.java).putExtra(FollowUpReceiver.EXTRA_SESSION, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay.toMillis(), pi)
    }

    private suspend fun scheduleBedtime(alarms: List<AlarmEntity>, now: ZonedDateTime) {
        val pi = PendingIntent.getBroadcast(
            context, BEDTIME_CODE, Intent(context, BedtimeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        val s = settings.current()
        if (!s.bedtimeReminders || alarms.isEmpty()) return

        // Plan the night before each upcoming wake-up and take the first reminder still ahead.
        // Daytime reminders do not define bedtime.
        val plan = alarms
            .filter { it.alarmKind == AlarmKind.WAKE }
            .map { AlarmTimeCalculator.nextTrigger(it.time, it.repeatDays, now, it.skipLocalDate) }
            .map { BedtimeCalculator.plan(it, Duration.ofMinutes(s.sleepGoalMinutes.toLong())) }
            .filter { it.reminderAt.isAfter(now) }
            .minByOrNull { it.reminderAt } ?: return

        val intent = Intent(context, BedtimeReceiver::class.java)
            .putExtra(BedtimeReceiver.EXTRA_LIGHTS_OUT, plan.lightsOutAt.toInstant().toEpochMilli())
        val armed = PendingIntent.getBroadcast(
            context, BEDTIME_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = plan.reminderAt.toInstant().toEpochMilli()
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, armed)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, armed)
    }

    private fun setAlarmClock(atMillis: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } catch (e: SecurityException) {
            // Exact alarms revoked (Android 12): degrade rather than silently not ringing.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun canExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun ringIntent(requestCode: Int, request: RingRequest?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        request?.writeTo(intent)
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val SNOOZE_OFFSET = 1_000_000
        private const val BEDTIME_CODE = 2_000_001
        private const val FOLLOW_UP_CODE = 2_000_002
        private const val GUARD_CODE = 2_000_003
    }
}

/** Everything a ring needs, carried through PendingIntent extras across snoozes. */
data class RingRequest(
    val alarmId: Long,
    val scheduledAt: Long,
    /** 0 until the first ring of this morning. */
    val firstRingAt: Long,
    val snoozeCount: Int,
) {
    fun writeTo(intent: Intent) {
        intent.putExtra(K_ID, alarmId).putExtra(K_SCHED, scheduledAt)
            .putExtra(K_FIRST, firstRingAt).putExtra(K_SNOOZE, snoozeCount)
    }

    companion object {
        private const val K_ID = "alarm_id"
        private const val K_SCHED = "scheduled_at"
        private const val K_FIRST = "first_ring_at"
        private const val K_SNOOZE = "snooze_count"

        fun from(intent: Intent): RingRequest? {
            val id = intent.getLongExtra(K_ID, -1)
            if (id < 0) return null
            return RingRequest(
                id, intent.getLongExtra(K_SCHED, 0), intent.getLongExtra(K_FIRST, 0), intent.getIntExtra(K_SNOOZE, 0),
            )
        }
    }
}
