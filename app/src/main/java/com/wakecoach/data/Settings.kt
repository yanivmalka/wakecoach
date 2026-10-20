package com.wakecoach.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class UserSettings(
    val leftHanded: Boolean = false,
    val sleepGoalMinutes: Int = 7 * 60 + 30,
    val bedtimeReminders: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LEFT = booleanPreferencesKey("left_handed")
        val GOAL = intPreferencesKey("sleep_goal_minutes")
        val BEDTIME = booleanPreferencesKey("bedtime_reminders")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map {
        UserSettings(
            leftHanded = it[Keys.LEFT] ?: false,
            sleepGoalMinutes = it[Keys.GOAL] ?: UserSettings().sleepGoalMinutes,
            bedtimeReminders = it[Keys.BEDTIME] ?: true,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        context.dataStore.edit {
            it[Keys.LEFT] = next.leftHanded
            it[Keys.GOAL] = next.sleepGoalMinutes
            it[Keys.BEDTIME] = next.bedtimeReminders
        }
    }
}
