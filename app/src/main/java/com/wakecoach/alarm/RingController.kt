package com.wakecoach.alarm

import com.wakecoach.data.repeatDays
import com.wakecoach.data.profile
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.wakecoach.audio.AlarmPlayer
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.core.message.Phrases
import com.wakecoach.core.model.Profile
import com.wakecoach.core.share.WakeStatusTracker
import com.wakecoach.data.WakeRepository
import com.wakecoach.data.WakeSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

data class RingState(
    val request: RingRequest,
    val label: String,
    val profile: Profile,
    val challenge: Challenge,
    val message: String,
) {
    val canSnooze: Boolean get() = profile.snooze.canSnooze(request.snoozeCount)
    val snoozeMinutes: Int get() = profile.snooze.minutesFor(request.snoozeCount)
}

/** Owns the currently ringing alarm: sound, wake lock, and the snooze/dismiss transitions. */
class RingController(
    private val context: Context,
    private val repository: WakeRepository,
    private val scheduler: AlarmScheduler,
    private val statusTracker: WakeStatusTracker,
) {
    private val _state = MutableStateFlow<RingState?>(null)
    val state: StateFlow<RingState?> = _state

    private val player = AlarmPlayer(context)
    private val challenges = ChallengeGenerator()
    private var wakeLock: PowerManager.WakeLock? = null

    suspend fun start(incoming: RingRequest) {
        if (_state.value != null) return // already ringing; rescheduleAll on dismiss re-arms the rest
        val alarm = repository.alarms.byId(incoming.alarmId) ?: return stop()

        val now = System.currentTimeMillis()
        val request = if (incoming.firstRingAt == 0L) incoming.copy(firstRingAt = now) else incoming
        if (request.snoozeCount == 0) {
            // Arm the next occurrence now, so a never-dismissed alarm still rings tomorrow.
            if (alarm.repeatDays.isEmpty()) repository.alarms.upsert(alarm.copy(enabled = false))
            else scheduler.schedule(alarm)
        }

        val lang = Lang.of(Locale.getDefault())
        val profile = alarm.profile
        val challenge = challenges.generate(
            type = challenges.pickType(profile.challenges),
            intensity = profile.intensity,
            phrases = Phrases.forLang(lang),
            registeredCode = null, // code registration arrives with the scanner in a later phase
        )
        val message = MessageComposer(lang).morningLine(repository.stats(), repository.priorityFor(LocalDate.now()))

        acquireWakeLock()
        player.start(profile)
        statusTracker.ringing(Instant.ofEpochMilli(now))
        _state.value = RingState(request, alarm.label, profile, challenge, message)
    }

    /** Returns false when the profile has no snoozes left. */
    fun snooze(): Boolean {
        val s = _state.value ?: return false
        if (!s.canSnooze) return false
        val next = s.request.copy(snoozeCount = s.request.snoozeCount + 1)
        scheduler.scheduleSnooze(next, s.snoozeMinutes)
        statusTracker.snoozed(Instant.now(), next.snoozeCount)
        stop()
        return true
    }

    /** Records the morning and returns the session id for the feedback screen. */
    suspend fun dismiss(): Long? {
        val s = _state.value ?: return null
        stop()
        val now = System.currentTimeMillis()
        val id = repository.recordSession(
            WakeSessionEntity(
                alarmId = s.request.alarmId,
                scheduledAt = s.request.scheduledAt,
                firstRingAt = s.request.firstRingAt,
                dismissedAt = now,
                snoozeCount = s.request.snoozeCount,
                challengeType = s.challenge.type.name,
            )
        )
        statusTracker.dismissed(Instant.ofEpochMilli(now))
        // Random 25-40 min so the second tiredness sample is not predictable.
        scheduler.scheduleFollowUp(id, Duration.ofMinutes(25L + Random.nextLong(16)))
        scheduler.rescheduleAll()
        return id
    }

    private fun stop() {
        player.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _state.value = null
        context.stopService(Intent(context, AlarmService::class.java))
    }

    private fun acquireWakeLock() {
        val pm = context.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeCoach:ring").apply {
            acquire(30 * 60_000L) // hard cap in case the user never responds
        }
    }
}
