package com.wakecoach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakecoach.container
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wakecoach.core.schedule.MorningDate
import java.time.LocalDateTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val alarms: StateFlow<List<AlarmEntity>> =
        c.repository.alarms.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<UserSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun save(alarm: AlarmEntity) = viewModelScope.launch {
        c.repository.alarms.upsert(alarm)
        c.scheduler.cancel(alarm)
        c.scheduler.rescheduleAll()
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = save(alarm.copy(enabled = enabled))

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
