package com.wakecoach.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wakecoach.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class UserSettings(
    val leftHanded: Boolean = false,
    val sleepGoalMinutes: Int = 7 * 60 + 30,
    val bedtimeReminders: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckMs: Long = 0,
    /** Samsung's "never sleeping apps" list cannot be read, so the user confirms it. */
    val samsungNeverSleepConfirmed: Boolean = false,
    val healthIntroShown: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LEFT = booleanPreferencesKey("left_handed")
        val GOAL = intPreferencesKey("sleep_goal_minutes")
        val BEDTIME = booleanPreferencesKey("bedtime_reminders")
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_UPDATE = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SAMSUNG_CONFIRMED = booleanPreferencesKey("samsung_never_sleep_confirmed")
        val HEALTH_INTRO = booleanPreferencesKey("health_intro_shown")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map {
        UserSettings(
            leftHanded = it[Keys.LEFT] ?: false,
            sleepGoalMinutes = it[Keys.GOAL] ?: UserSettings().sleepGoalMinutes,
            bedtimeReminders = it[Keys.BEDTIME] ?: true,
            themeMode = it[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { m -> m.name == name } }
                ?: ThemeMode.SYSTEM,
            autoCheckUpdates = it[Keys.AUTO_UPDATE] ?: true,
            lastUpdateCheckMs = it[Keys.LAST_UPDATE_CHECK] ?: 0,
            samsungNeverSleepConfirmed = it[Keys.SAMSUNG_CONFIRMED] ?: false,
            healthIntroShown = it[Keys.HEALTH_INTRO] ?: false,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        context.dataStore.edit {
            it[Keys.LEFT] = next.leftHanded
            it[Keys.GOAL] = next.sleepGoalMinutes
            it[Keys.BEDTIME] = next.bedtimeReminders
            it[Keys.THEME] = next.themeMode.name
            it[Keys.AUTO_UPDATE] = next.autoCheckUpdates
            it[Keys.LAST_UPDATE_CHECK] = next.lastUpdateCheckMs
            it[Keys.SAMSUNG_CONFIRMED] = next.samsungNeverSleepConfirmed
            it[Keys.HEALTH_INTRO] = next.healthIntroShown
        }
    }
}
