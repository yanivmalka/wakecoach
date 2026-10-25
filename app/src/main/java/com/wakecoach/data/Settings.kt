package com.wakecoach.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** [com.wakecoach.core.content.QuoteCategory] keys shown in the morning; all by default. */
    val quoteCategories: Set<String> = setOf("faith", "philosophy", "facts", "jokes"),
    val recentQuoteIds: List<Int> = emptyList(),
    /** Play a personal recording over a wake-up that is not answered. */
    val playRecordings: Boolean = true,
    /** Ask when a regular alarm is missing or switched off. */
    val guardEnabled: Boolean = true,
    /** [java.time.DayOfWeek.getValue] of days the guard stays quiet; Saturday by default. */
    val restDays: Set<Int> = setOf(6),
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
        val QUOTE_CATEGORIES = stringSetPreferencesKey("quote_categories")
        val RECENT_QUOTES = stringPreferencesKey("recent_quotes")
        val PLAY_RECORDINGS = booleanPreferencesKey("play_recordings")
        val GUARD = booleanPreferencesKey("guard_enabled")
        val REST_DAYS = stringSetPreferencesKey("rest_days")
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
            quoteCategories = it[Keys.QUOTE_CATEGORIES] ?: UserSettings().quoteCategories,
            recentQuoteIds = it[Keys.RECENT_QUOTES].orEmpty().split(',').mapNotNull(String::toIntOrNull),
            playRecordings = it[Keys.PLAY_RECORDINGS] ?: true,
            guardEnabled = it[Keys.GUARD] ?: true,
            restDays = it[Keys.REST_DAYS]?.mapNotNull(String::toIntOrNull)?.toSet() ?: UserSettings().restDays,
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
            it[Keys.QUOTE_CATEGORIES] = next.quoteCategories
            it[Keys.RECENT_QUOTES] = next.recentQuoteIds.joinToString(",")
            it[Keys.PLAY_RECORDINGS] = next.playRecordings
            it[Keys.GUARD] = next.guardEnabled
            it[Keys.REST_DAYS] = next.restDays.map(Int::toString).toSet()
        }
    }
}
