package com.wakecoach.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.wakecoach.R
import com.wakecoach.alarm.AlarmScheduler
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.schedule.MorningDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

private val SUN_THU = setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)
private val SUN_FRI = SUN_THU + FRIDAY
private val ALL_DAYS = DayOfWeek.entries.toSet()

/** One alarm of a demo persona; the label is a string resource so it follows the app language. */
data class DemoAlarm(
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val profileId: String,
    @StringRes val label: Int,
    val kind: AlarmKind = AlarmKind.WAKE,
    val importance: Int = 2,
)

/**
 * Ready-made users for trying the app: their alarms plus four weeks of history (wake-ups,
 * snoozes, morning check-ins, ring log), so Progress, the adaptive engine and the alarm guard
 * have something to show.
 *
 * [snoozeChance] is the per-ring chance of snoozing again; [tiredness] is the typical 1..5 rating.
 */
enum class DemoPersona(
    @StringRes val title: Int,
    val emoji: String,
    val sleepGoalMinutes: Int,
    val snoozeChance: Double,
    val tiredness: Int,
    @StringRes val priority: Int,
    val alarms: List<DemoAlarm>,
) {
    STUDENT(
        R.string.persona_student, "🎓", 8 * 60, 0.55, 4, R.string.demo_pri_student,
        listOf(
            DemoAlarm(8, 0, SUN_THU, Profiles.FIRM.id, R.string.demo_lecture, importance = 3),
            DemoAlarm(10, 30, setOf(FRIDAY, SATURDAY), Profiles.GENTLE.id, R.string.demo_weekend, importance = 1),
            DemoAlarm(14, 0, setOf(MONDAY, WEDNESDAY), Profiles.BALANCED.id, R.string.demo_tutorial, AlarmKind.REMINDER),
            DemoAlarm(19, 0, setOf(SUNDAY, TUESDAY), Profiles.BALANCED.id, R.string.demo_shift, AlarmKind.REMINDER),
        ),
    ),
    PARENT(
        R.string.persona_parent, "👨‍👧", 7 * 60, 0.3, 4, R.string.demo_pri_parent,
        listOf(
            DemoAlarm(6, 15, SUN_FRI, Profiles.BALANCED.id, R.string.demo_wake_kids, importance = 3),
            DemoAlarm(7, 30, SUN_THU, Profiles.BALANCED.id, R.string.demo_drop_off, AlarmKind.REMINDER),
            DemoAlarm(16, 0, SUN_THU, Profiles.BALANCED.id, R.string.demo_pick_up, AlarmKind.REMINDER),
        ),
    ),
    TEACHER(
        R.string.persona_teacher, "🧑‍🏫", 7 * 60 + 30, 0.25, 3, R.string.demo_pri_teacher,
        listOf(
            DemoAlarm(6, 30, SUN_FRI, Profiles.BALANCED.id, R.string.demo_school_day, importance = 3),
            DemoAlarm(7, 40, SUN_FRI, Profiles.BALANCED.id, R.string.demo_first_bell, AlarmKind.REMINDER),
            DemoAlarm(20, 0, setOf(MONDAY), Profiles.BALANCED.id, R.string.demo_grade_tests, AlarmKind.REMINDER),
        ),
    ),
    PUPIL(
        R.string.persona_pupil, "🎒", 9 * 60, 0.6, 3, R.string.demo_pri_pupil,
        listOf(
            DemoAlarm(7, 0, SUN_FRI, Profiles.HEAVY_SLEEPER.id, R.string.demo_school, importance = 3),
            DemoAlarm(15, 30, setOf(MONDAY, WEDNESDAY), Profiles.BALANCED.id, R.string.demo_class, AlarmKind.REMINDER),
            DemoAlarm(21, 0, SUN_THU, Profiles.BALANCED.id, R.string.demo_pack_bag, AlarmKind.REMINDER),
        ),
    ),
    SOLDIER(
        R.string.persona_soldier, "🪖", 7 * 60, 0.05, 4, R.string.demo_pri_soldier,
        listOf(
            DemoAlarm(5, 30, SUN_THU, Profiles.SOLDIER.id, R.string.demo_morning_roll, importance = 3),
            DemoAlarm(6, 15, SUN_THU, Profiles.BALANCED.id, R.string.demo_formation, AlarmKind.REMINDER),
            DemoAlarm(9, 0, setOf(FRIDAY), Profiles.GENTLE.id, R.string.demo_weekend_home, importance = 1),
        ),
    ),
    BUS_DRIVER(
        R.string.persona_bus_driver, "🚌", 7 * 60, 0.15, 3, R.string.demo_pri_bus,
        listOf(
            DemoAlarm(4, 45, SUN_THU, Profiles.FIRM.id, R.string.demo_morning_shift, importance = 3),
            DemoAlarm(5, 20, SUN_THU, Profiles.BALANCED.id, R.string.demo_depot, AlarmKind.REMINDER),
            DemoAlarm(13, 0, SUN_THU, Profiles.BALANCED.id, R.string.demo_break, AlarmKind.REMINDER),
        ),
    ),
    TRUCK_DRIVER(
        R.string.persona_truck_driver, "🚚", 7 * 60, 0.2, 4, R.string.demo_pri_truck,
        listOf(
            DemoAlarm(3, 30, SUN_THU, Profiles.HEAVY_SLEEPER.id, R.string.demo_route, importance = 3),
            DemoAlarm(7, 30, SUN_THU, Profiles.BALANCED.id, R.string.demo_rest_stop, AlarmKind.REMINDER),
            DemoAlarm(11, 30, SUN_THU, Profiles.BALANCED.id, R.string.demo_rest_stop, AlarmKind.REMINDER),
        ),
    ),
    RETIREE(
        R.string.persona_retiree, "☕", 8 * 60, 0.2, 2, R.string.demo_pri_retiree,
        listOf(
            DemoAlarm(7, 30, ALL_DAYS, Profiles.GENTLE.id, R.string.demo_good_morning, importance = 1),
            DemoAlarm(9, 0, ALL_DAYS, Profiles.BALANCED.id, R.string.demo_morning_meds, AlarmKind.REMINDER),
            DemoAlarm(17, 0, setOf(TUESDAY), Profiles.BALANCED.id, R.string.demo_club, AlarmKind.REMINDER),
        ),
    ),
    GRANDPARENT(
        R.string.persona_grandparent, "👵", 8 * 60, 0.15, 2, R.string.demo_pri_grandparent,
        listOf(
            DemoAlarm(7, 0, ALL_DAYS, Profiles.GENTLE.id, R.string.demo_good_morning, importance = 1),
            DemoAlarm(12, 30, setOf(WEDNESDAY), Profiles.BALANCED.id, R.string.demo_grandkids_pickup, AlarmKind.REMINDER),
            DemoAlarm(10, 0, setOf(SUNDAY), Profiles.BALANCED.id, R.string.demo_call_grandkids, AlarmKind.REMINDER),
            DemoAlarm(18, 0, setOf(FRIDAY), Profiles.BALANCED.id, R.string.demo_family_dinner, AlarmKind.REMINDER),
        ),
    ),
}

/** Adds demo personas and wipes user data. Settings (theme, hand, tactics) survive a reset. */
class DemoData(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val settings: SettingsRepository,
) {
    data class Added(val alarms: Int, val mornings: Int)

    suspend fun add(persona: DemoPersona, historyDays: Int = 28): Added {
        val rng = Random(System.nanoTime())
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        var mornings = 0
        val alarms = db.withTransaction {
            persona.alarms.map { a ->
                val label = context.getString(a.label)
                val entity = AlarmEntity(
                    hour = a.hour, minute = a.minute, daysMask = AlarmEntity.maskOf(a.days),
                    profileId = a.profileId, importance = a.importance, label = label, kind = a.kind.name,
                )
                val id = db.alarms().upsert(entity)
                val saved = entity.copy(id = id)
                for (back in historyDays downTo 1) {
                    val date = today.minusDays(back.toLong())
                    if (date.dayOfWeek !in a.days) continue
                    val at = date.atTime(a.hour, a.minute).atZone(zone).toInstant().toEpochMilli()
                    db.guard().logRing(RingLogEntity(alarmId = id, kind = a.kind.name, label = label, at = at))
                    if (a.kind == AlarmKind.WAKE) {
                        // Older mornings snooze more, so Progress shows an improving trend.
                        val trend = 0.6 + 0.8 * back / historyDays
                        addMorning(persona, a, id, at, persona.snoozeChance * trend, rng)
                        mornings++
                    }
                }
                saved
            }
        }
        db.priorities().save(
            PriorityEntity(MorningDate.upcoming(LocalDateTime.now()).toString(), context.getString(persona.priority))
        )
        settings.update { it.copy(sleepGoalMinutes = persona.sleepGoalMinutes) }
        scheduler.rescheduleAll()
        return Added(alarms.size, mornings)
    }

    private suspend fun addMorning(persona: DemoPersona, a: DemoAlarm, alarmId: Long, at: Long, snoozeP: Double, rng: Random) {
        val profile = Profiles.byId(a.profileId)
        var snoozes = 0
        while (profile.snooze.canSnooze(snoozes) && rng.nextDouble() < snoozeP) snoozes++
        val snoozedMs = (0 until snoozes).sumOf { profile.snooze.minutesFor(it) * 60_000L }
        val dismissed = at + snoozedMs + rng.nextLong(20_000, 150_000)
        val sessionId = db.sessions().insert(
            WakeSessionEntity(
                alarmId = alarmId, scheduledAt = at, firstRingAt = at, dismissedAt = dismissed,
                snoozeCount = snoozes, challengeType = profile.challenges.random(rng).name,
            )
        )
        if (rng.nextDouble() > 0.85) return // not every morning gets a check-in
        val tired = (persona.tiredness + snoozes / 2 + rng.nextInt(-1, 2)).coerceIn(1, 5)
        val ease = (5 - snoozes - rng.nextInt(0, 2)).coerceIn(1, 5)
        val mood = (6 - tired + rng.nextInt(-1, 2)).coerceIn(1, 5)
        val later = if (rng.nextBoolean()) (tired - rng.nextInt(0, 3)).coerceIn(1, 5) else null
        db.sessions().saveFeedback(FeedbackEntity(sessionId, dismissed + 60_000, mood, ease, tired, later))
    }

    /** Deletes every alarm, wake-up, check-in, priority, recording and guard answer. */
    suspend fun resetAll() {
        db.alarms().all().forEach(scheduler::cancel)
        withContext(Dispatchers.IO) {
            db.recordings().all().forEach { File(it.path).delete() }
            db.clearAllTables()
        }
        scheduler.rescheduleAll()
    }
}
