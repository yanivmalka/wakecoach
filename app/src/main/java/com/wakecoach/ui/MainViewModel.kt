package com.wakecoach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakecoach.container
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.alarmKind
import com.wakecoach.data.UserSettings
import com.wakecoach.core.schedule.AlarmTimeCalculator
import com.wakecoach.data.repeatDays
import com.wakecoach.data.skipLocalDate
import com.wakecoach.data.time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wakecoach.core.schedule.MorningDate
import java.time.LocalDateTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val alarms: StateFlow<List<AlarmEntity>> =
        c.repository.alarms.observeAll()
            .map { list -> list.filter { it.alarmKind != AlarmKind.TEST } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<UserSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun save(alarm: AlarmEntity) = viewModelScope.launch {
        c.repository.alarms.upsert(alarm)
        c.scheduler.cancel(alarm)
        c.scheduler.rescheduleAll()
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = save(alarm.copy(enabled = enabled, skipDate = null))

    /** A regular alarm the user is about to switch off; the screen asks "skip once or turn off?". */
    val pendingDisable = MutableStateFlow<AlarmEntity?>(null)

    /** Turning off a repeating alarm that rang often deserves a question; anything else just turns off. */
    fun requestToggle(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        val regular = alarm.repeatDays.isNotEmpty() && c.guard.recentRingCount(alarm) >= 5
        if (!enabled && regular) pendingDisable.value = alarm else setEnabled(alarm, enabled)
    }

    /** Skip only the next occurrence; the alarm stays on. */
    fun skipNext(alarm: AlarmEntity) = viewModelScope.launch {
        // Keep a skip that is still ahead; only one skip is stored, so never move it to a later day.
        val pending = alarm.skipLocalDate?.takeIf { !it.isBefore(java.time.LocalDate.now()) }
        val next = pending ?: AlarmTimeCalculator.nextTrigger(alarm.time, alarm.repeatDays, ZonedDateTime.now()).toLocalDate()
        c.repository.alarms.upsert(alarm.copy(skipDate = next.toEpochDay()))
        c.scheduler.cancel(alarm)
        c.scheduler.rescheduleAll()
        pendingDisable.value = null
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        c.scheduler.cancel(alarm)
        c.repository.alarms.delete(alarm)
        c.scheduler.rescheduleAll()
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) = viewModelScope.launch {
        c.settings.update(transform)
        c.scheduler.rescheduleAll()
    }

    suspend fun tomorrowPriority(): String = c.repository.priorityFor(MorningDate.upcoming(LocalDateTime.now())).orEmpty()

    fun saveTomorrowPriority(text: String) = viewModelScope.launch {
        c.repository.savePriority(MorningDate.upcoming(LocalDateTime.now()), text)
    }
}
