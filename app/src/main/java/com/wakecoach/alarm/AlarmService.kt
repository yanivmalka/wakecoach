package com.wakecoach.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wakecoach.R
import com.wakecoach.WakeCoachApp
import com.wakecoach.container
import com.wakecoach.ui.ringing.RingingActivity
import kotlinx.coroutines.launch

/** Keeps the process in the foreground while ringing and surfaces the full-screen ring UI. */
class AlarmService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.let(RingRequest::from)
        val ui = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, WakeCoachApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notif_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(ui, true)
            .setContentIntent(ui)
            .setOngoing(true)
            .build()
        // Must be called promptly after startForegroundService, before any async work.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        val controller = container.ringController
        if (request == null) {
            if (controller.state.value == null) stopSelf()
        } else {
            container.appScope.launch { controller.start(request) }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
