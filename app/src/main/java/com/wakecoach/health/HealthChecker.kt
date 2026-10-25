package com.wakecoach.health

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.wakecoach.alarm.AlarmScheduler
import com.wakecoach.core.health.HealthCheck
import com.wakecoach.core.health.HealthItem
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.data.AlarmEntity
import com.wakecoach.data.alarmKind

/** Reads the phone settings that decide whether an alarm will actually ring. */
class HealthChecker(private val context: Context, private val scheduler: AlarmScheduler) {

    val isSamsung: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun run(enabledAlarms: List<AlarmEntity>, samsungConfirmed: Boolean, wakeCheck: Boolean = false): List<HealthCheck> = buildList {
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(AlarmManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)

        add(HealthCheck(HealthItem.NOTIFICATIONS, NotificationManagerCompat.from(context).areNotificationsEnabled()))
        if (Build.VERSION.SDK_INT >= 34) add(HealthCheck(HealthItem.FULL_SCREEN, nm.canUseFullScreenIntent()))
        if (Build.VERSION.SDK_INT >= 31) add(HealthCheck(HealthItem.EXACT_ALARMS, am.canScheduleExactAlarms()))
        val real = enabledAlarms.filter { it.alarmKind != AlarmKind.TEST }
        add(HealthCheck(HealthItem.NEXT_ALARM_REGISTERED, real.all(scheduler::isArmed)))
        add(HealthCheck(HealthItem.BATTERY_OPTIMIZATION, pm.isIgnoringBatteryOptimizations(context.packageName)))
        if (Build.VERSION.SDK_INT >= 28) {
            val activity = context.getSystemService(ActivityManager::class.java)
            add(HealthCheck(HealthItem.BACKGROUND_RESTRICTED, !activity.isBackgroundRestricted))
        }
        if (isSamsung) add(HealthCheck(HealthItem.SAMSUNG_SLEEPING, samsungConfirmed))
        add(HealthCheck(HealthItem.ALARM_VOLUME, audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0))
        add(HealthCheck(HealthItem.DND_SILENCE, nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE))
        if (wakeCheck && Build.VERSION.SDK_INT >= 29) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            add(HealthCheck(HealthItem.STEP_ACCESS, granted))
        }
    }

    /**
     * Opens the system screen that fixes [item]. Returns false for items fixed inside the app
     * (volume, re-arming, Samsung confirmation), which the caller handles itself.
     */
    fun openFix(activity: Activity, item: HealthItem): Boolean {
        val pkg = Uri.parse("package:${context.packageName}")
        val candidates: List<Intent> = when (item) {
            HealthItem.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
            HealthItem.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= 34) listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) else emptyList()
            HealthItem.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= 31) listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) else emptyList()
            HealthItem.BATTERY_OPTIMIZATION -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            HealthItem.BACKGROUND_RESTRICTED -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            HealthItem.SAMSUNG_SLEEPING -> listOf(
                // Device care > Battery on One UI; the component has moved between versions.
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            )
            HealthItem.DND_SILENCE -> listOf(Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
            // Asked in-app first; after two denials Android only allows it from app settings.
            HealthItem.ALARM_VOLUME, HealthItem.NEXT_ALARM_REGISTERED, HealthItem.STEP_ACCESS -> return false
        }
        for (intent in candidates) {
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // try the next, more generic screen
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** Sets the alarm stream to 70% so a muted alarm volume cannot silence the morning. */
    fun raiseAlarmVolume() {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7f).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
    }
}
