package com.wakecoach.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wakecoach.R
import com.wakecoach.WakeCoachApp
import com.wakecoach.container
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.ui.MainActivity
import com.wakecoach.ui.feedback.FeedbackActivity
import com.wakecoach.ui.ringing.RingingActivity
import kotlinx.coroutines.launch
import com.wakecoach.core.schedule.MorningDate
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, AlarmService::class.java).putExtras(intent)
        try {
            ContextCompat.startForegroundService(context, service)
        } catch (e: IllegalStateException) {
            // Android 12 with exact alarms revoked: no background FGS start. Surface the ring UI
            // instead; it starts the service itself once it is in the foreground.
            val ui = PendingIntent.getActivity(
                context, 12,
                Intent(context, RingingActivity::class.java).putExtras(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, WakeCoachApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_ringing))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(ui, true)
                .setContentIntent(ui)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIF_FALLBACK, n)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWith(context) { context.container.scheduler.rescheduleAll() }
    }
}

/** "Time to wind down" with tomorrow's goal, and a tap into the evening question. */
class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lightsOut = intent.getLongExtra(EXTRA_LIGHTS_OUT, 0)
        goAsyncWith(context) {
            val c = context.container
            val priority = c.repository.priorityFor(MorningDate.upcoming(LocalDateTime.now()))
            val minutes = ((lightsOut - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
            val event = c.calendar.firstMorningEvent(MorningDate.upcoming(LocalDateTime.now()))
            val text = listOfNotNull(
                MessageComposer(Lang.of(Locale.getDefault())).bedtimeLine(minutes, priority),
                event?.let { context.getString(R.string.event_tomorrow, it.start.format(DateTimeFormatter.ofPattern("HH:mm")), it.title) },
            ).joinToString("\n")
            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_EVENING, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            notify(context, NOTIF_BEDTIME, context.getString(R.string.notif_bedtime_title), text, open)
            // Re-arm for the next night.
            c.scheduler.rescheduleAll()
        }
    }

    companion object {
        const val EXTRA_LIGHTS_OUT = "lights_out"
    }
}

/** Second tiredness sample ~30 minutes after getting up. */
class FollowUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION, -1)
        if (sessionId < 0) return
        val open = PendingIntent.getActivity(
            context, 11, FeedbackActivity.laterIntent(context, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            context, NOTIF_FOLLOW_UP,
            context.getString(R.string.notif_followup_title), context.getString(R.string.notif_followup_text), open,
        )
    }

    companion object {
        const val EXTRA_SESSION = "session_id"
    }
}

private const val NOTIF_BEDTIME = 20
private const val NOTIF_FOLLOW_UP = 21
private const val NOTIF_FALLBACK = 22

private fun notify(context: Context, id: Int, title: String, text: String, open: PendingIntent) {
    val n = NotificationCompat.Builder(context, WakeCoachApp.CHANNEL_COACH)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    // POST_NOTIFICATIONS may be denied; NotificationManager drops the post silently then.
    context.getSystemService(NotificationManager::class.java).notify(id, n)
}

/** Runs suspend work past onReceive while keeping the process alive until it finishes. */
private fun BroadcastReceiver.goAsyncWith(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.appScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/**
 * Alarm guard check (fires at 21:00 for tomorrow and 08:00 for the rest of today): if a regular
 * wake-up or daytime reminder has no alarm, ask once, listing everything missing together.
 */
class GuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWith(context) {
            val c = context.container
            val now = LocalDateTime.now()
            val (date, notBefore) = GuardNotifier.window(now)
            val missing = c.guard.missing(date, notBefore)
            if (missing.isNotEmpty()) GuardNotifier.show(context, date, missing)
            c.scheduler.rescheduleAll()
        }
    }
}

/** "Add all" / "Not this time" straight from the guard notification. */
class GuardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = LocalDate.ofEpochDay(intent.getLongExtra(GuardNotifier.EXTRA_DATE, LocalDate.now().toEpochDay()))
        goAsyncWith(context) {
            val c = context.container
            // Recompute rather than trust stale extras: the user may have added alarms meanwhile.
            val notBefore = if (date == LocalDate.now()) LocalDateTime.now() else null
            val missing = c.guard.missing(date, notBefore)
            when (intent.action) {
                GuardNotifier.ACTION_ADD_ALL -> c.guard.addAlarms(missing, date)
                GuardNotifier.ACTION_SKIP_ALL -> c.guard.notThisTime(missing, date)
            }
            context.getSystemService(NotificationManager::class.java).cancel(GuardNotifier.NOTIF_ID)
            c.scheduler.rescheduleAll()
        }
    }
}

object GuardNotifier {
    const val NOTIF_ID = 23
    const val EXTRA_DATE = "guard_date"
    const val ACTION_ADD_ALL = "com.wakecoach.GUARD_ADD_ALL"
    const val ACTION_SKIP_ALL = "com.wakecoach.GUARD_SKIP_ALL"

    /** In the evening look at tomorrow; in the morning at today's slots still 2+ hours away. */
    fun window(now: LocalDateTime): Pair<LocalDate, LocalDateTime?> =
        if (now.hour >= 12) now.toLocalDate().plusDays(1) to null
        else now.toLocalDate() to now.plusHours(2)

    fun describe(context: Context, slots: List<com.wakecoach.core.guard.Slot>): String {
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        return slots.joinToString(", ") { s ->
            if (s.kind == com.wakecoach.core.model.AlarmKind.WAKE) "${context.getString(R.string.kind_wake)} ${s.time.format(hhmm)}"
            else "“${s.label}” ${s.time.format(hhmm)}"
        }
    }

    fun show(context: Context, date: LocalDate, slots: List<com.wakecoach.core.guard.Slot>) {
        val day = context.getString(if (date == LocalDate.now()) R.string.guard_today else R.string.guard_tomorrow)
        val text = context.getString(R.string.guard_text, day, describe(context, slots))
        fun action(name: String, code: Int) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, GuardActionReceiver::class.java).setAction(name).putExtra(EXTRA_DATE, date.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 13,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_GUARD, date.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, WakeCoachApp.CHANNEL_COACH)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.guard_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.guard_add_all), action(ACTION_ADD_ALL, 14))
            .addAction(0, context.getString(R.string.guard_not_this_time), action(ACTION_SKIP_ALL, 15))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}

/** The surprise "back to sleep?" check a few minutes after a wake-up was dismissed. */
class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWith(context) {
            val c = context.container
            if (!c.settings.current().wakeCheck) { c.wakeCheck.clear(); return@goAsyncWith }
            c.wakeCheck.check()?.let(c.scheduler::ringNow)
        }
    }
}
