package com.wakecoach

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.wakecoach.alarm.AlarmScheduler
import com.wakecoach.alarm.RingController
import com.wakecoach.alarm.WakeCheck
import com.wakecoach.core.share.StatusPublisher
import com.wakecoach.core.share.WakeStatusTracker
import com.wakecoach.data.AppDatabase
import com.wakecoach.data.CalendarRepository
import com.wakecoach.data.GuardRepository
import com.wakecoach.data.RecordingRepository
import com.wakecoach.data.QuoteRepository
import com.wakecoach.data.SettingsRepository
import com.wakecoach.data.WakeRepository
import com.wakecoach.health.HealthChecker
import com.wakecoach.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class WakeCoachApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                // The alarm sound is played by RingController, not by the notification.
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COACH, getString(R.string.channel_coach), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_ALARM = "alarm"
        const val CHANNEL_COACH = "coach"
    }
}

/** Manual DI: one instance of each long-lived dependency. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(context)
    val repository = WakeRepository(db)
    val settings = SettingsRepository(context)
    val scheduler = AlarmScheduler(context, repository, settings)
    // Phase 5 swaps in a Firebase-backed publisher for approved buddies.
    val statusTracker = WakeStatusTracker(StatusPublisher.NONE)
    val quotes = QuoteRepository(context, settings)
    val recordings = RecordingRepository(context, db.recordings())
    val calendar = CalendarRepository(context)
    val guard = GuardRepository(db, settings)
    val wakeCheck = WakeCheck(context)
    val ringController = RingController(
        context, repository, scheduler, statusTracker, quotes, recordings, calendar, settings, guard, wakeCheck,
    )
    val updater = Updater(context)
    val health = HealthChecker(context, scheduler)
}

val Context.container: AppContainer get() = (applicationContext as WakeCoachApp).container
