package com.wakecoach.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.model.Profile
import com.wakecoach.core.model.Profiles
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bit (dayOfWeek.value - 1) set for each repeat day; 0 = one-shot. */
    val daysMask: Int = 0,
    val profileId: String = Profiles.BALANCED.id,
    val importance: Int = 1,
    val label: String = "",
    val enabled: Boolean = true,
    /** [AlarmKind] name; stored as text so new kinds need no migration. Default must match MIGRATION_1_2. */
    @ColumnInfo(defaultValue = "WAKE")
    val kind: String = AlarmKind.WAKE.name,
) {
    companion object {
        fun maskOf(days: Set<DayOfWeek>) = days.fold(0) { m, d -> m or (1 shl (d.value - 1)) }
    }
}

// Derived views kept outside the entity so Room does not try to map them to columns.
val AlarmEntity.time: LocalTime get() = LocalTime.of(hour, minute)
val AlarmEntity.repeatDays: Set<DayOfWeek>
    get() = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet()
val AlarmEntity.profile: Profile get() = Profiles.byId(profileId)
val AlarmEntity.alarmKind: AlarmKind get() = AlarmKind.entries.firstOrNull { it.name == kind } ?: AlarmKind.WAKE

@Entity(tableName = "wake_sessions")
data class WakeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val scheduledAt: Long,
    val firstRingAt: Long,
    val dismissedAt: Long,
    val snoozeCount: Int,
    val challengeType: String,
)

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val sessionId: Long,
    val at: Long,
    val mood: Int,
    val wakeEase: Int,
    val tiredness: Int,
    val laterTiredness: Int? = null,
)

/** "What matters tomorrow", keyed by the ISO date of the morning it applies to. */
@Entity(tableName = "priorities")
data class PriorityEntity(
    @PrimaryKey val date: String,
    val text: String,
)

/** Row shape of the sessions LEFT JOIN feedback query. */
data class SessionWithFeedback(
    val id: Long,
    val firstRingAt: Long,
    val dismissedAt: Long,
    val snoozeCount: Int,
    val at: Long?,
    val mood: Int?,
    val wakeEase: Int?,
    val tiredness: Int?,
    val laterTiredness: Int?,
)
