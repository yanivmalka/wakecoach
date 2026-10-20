package com.wakecoach.core.share

import java.time.Duration
import java.time.Instant

/** What approved buddies see. Deliberately coarse: no ratings, no sensor data. */
sealed interface WakeStatus {
    val at: Instant

    data class Scheduled(override val at: Instant, val alarmAt: Instant) : WakeStatus
    data class Ringing(override val at: Instant) : WakeStatus
    data class Snoozing(override val at: Instant, val snoozes: Int) : WakeStatus
    data class Awake(override val at: Instant, val minutesAfterAlarm: Long) : WakeStatus
    /** Raised when the user is still not up [minutes] after the first ring. */
    data class NotUp(override val at: Instant, val minutes: Long) : WakeStatus
}

/** Receives status changes; the Firebase implementation arrives in phase 5. */
fun interface StatusPublisher {
    fun publish(status: WakeStatus)

    companion object {
        val NONE = StatusPublisher { }
    }
}

/**
 * Turns alarm events into [WakeStatus] updates for buddies. Only the first "not up"
 * crossing per threshold is published so buddies are not spammed.
 */
class WakeStatusTracker(
    private val publisher: StatusPublisher,
    private val notUpThresholds: List<Duration> = listOf(Duration.ofMinutes(10), Duration.ofMinutes(20)),
) {
    private var firstRing: Instant? = null
    private var reported = mutableSetOf<Duration>()
    var current: WakeStatus? = null
        private set

    fun scheduled(now: Instant, alarmAt: Instant) {
        firstRing = null; reported = mutableSetOf()
        emit(WakeStatus.Scheduled(now, alarmAt))
    }

    fun ringing(now: Instant) {
        if (firstRing == null) firstRing = now
        emit(WakeStatus.Ringing(now))
    }

    fun snoozed(now: Instant, snoozes: Int) = emit(WakeStatus.Snoozing(now, snoozes))

    fun dismissed(now: Instant) {
        val start = firstRing ?: now
        emit(WakeStatus.Awake(now, Duration.between(start, now).toMinutes()))
    }

    /** Call periodically while ringing/snoozing. */
    fun tick(now: Instant) {
        val start = firstRing ?: return
        if (current is WakeStatus.Awake) return
        val elapsed = Duration.between(start, now)
        notUpThresholds.filter { elapsed >= it && it !in reported }.maxOrNull()?.let {
            reported += notUpThresholds.filter { t -> t <= elapsed }
            emit(WakeStatus.NotUp(now, it.toMinutes()))
        }
    }

    private fun emit(s: WakeStatus) {
        current = s
        publisher.publish(s)
    }
}

/** A buddy relationship requires both sides to agree and can be revoked by either. */
data class Buddy(
    val uid: String,
    val displayName: String,
    val invitedByMe: Boolean,
    val acceptedByThem: Boolean,
    val acceptedByMe: Boolean,
    val revoked: Boolean = false,
    val canWakeMe: Boolean = true,
) {
    val active: Boolean get() = acceptedByThem && acceptedByMe && !revoked
    val mayNudge: Boolean get() = active && canWakeMe
}
