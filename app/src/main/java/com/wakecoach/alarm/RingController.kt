package com.wakecoach.alarm

import com.wakecoach.data.repeatDays
import com.wakecoach.data.profile
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.wakecoach.audio.AlarmPlayer
import com.wakecoach.core.audio.SoundContext
import com.wakecoach.core.audio.SoundSelector
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.content.Quote
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.core.message.Phrases
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.core.model.Profile
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.model.SnoozePolicy
import com.wakecoach.core.share.WakeStatusTracker
import com.wakecoach.R
import com.wakecoach.data.CalendarRepository
import com.wakecoach.data.QuoteRepository
import com.wakecoach.data.RecordingRepository
import com.wakecoach.data.SettingsRepository
import com.wakecoach.data.WakeRepository
import com.wakecoach.data.alarmKind
import com.wakecoach.data.WakeSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.random.Random

data class RingState(
    val request: RingRequest,
    val kind: AlarmKind,
    val label: String,
    val profile: Profile,
    val challenge: Challenge,
    val message: String,
    /** Morning line (faith/philosophy/fact/joke) for wake-ups; null for reminders. */
    val quote: Quote? = null,
) {
    val canSnooze: Boolean get() = profile.snooze.canSnooze(request.snoozeCount)
    val snoozeMinutes: Int get() = profile.snooze.minutesFor(request.snoozeCount)
}

private val HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** Owns the currently ringing alarm: sound, wake lock, and the snooze/dismiss transitions. */
class RingController(
    private val context: Context,
    private val repository: WakeRepository,
    private val scheduler: AlarmScheduler,
    private val statusTracker: WakeStatusTracker,
    private val quotes: QuoteRepository,
    private val recordings: RecordingRepository,
    private val calendar: CalendarRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<RingState?>(null)
    val state: StateFlow<RingState?> = _state

    private val player = AlarmPlayer(context)
    private val challenges = ChallengeGenerator()
    private var wakeLock: PowerManager.WakeLock? = null
    // Alarms rung today, so consecutive reminders get different sounds.
    private var ringDay: LocalDate? = null
    private var ringsToday = 0

    suspend fun start(incoming: RingRequest) {
        if (_state.value != null) return // already ringing; rescheduleAll on dismiss re-arms the rest
        val alarm = repository.alarms.byId(incoming.alarmId) ?: return stop()

        val now = System.currentTimeMillis()
        val request = if (incoming.firstRingAt == 0L) incoming.copy(firstRingAt = now) else incoming
        if (alarm.alarmKind == AlarmKind.TEST) {
            // A test ring is single-use; drop its row now so it never shows up or re-arms.
            repository.alarms.delete(alarm)
        } else if (request.snoozeCount == 0) {
            // Arm the next occurrence now, so a never-dismissed alarm still rings tomorrow.
            if (alarm.repeatDays.isEmpty()) repository.alarms.upsert(alarm.copy(enabled = false))
            else scheduler.schedule(alarm)
        }

        val lang = Lang.of(Locale.getDefault())
        val kind = alarm.alarmKind
        val state = if (kind.isNudge) {
            // A test has nothing to snooze to: its alarm row is already gone.
            val profile = if (kind == AlarmKind.TEST) Profiles.REMINDER.copy(snooze = SnoozePolicy(0, 0)) else Profiles.REMINDER
            RingState(request, kind, alarm.label, profile, Challenge.None, message = alarm.label)
        } else {
            val profile = alarm.profile
            val challenge = challenges.generate(
                type = challenges.pickType(profile.challenges),
                intensity = profile.intensity,
                phrases = Phrases.forLang(lang),
                registeredCode = null, // code registration arrives with the scanner in a later phase
            )
            val event = calendar.firstMorningEvent(LocalDate.now())
            val eventLine = event?.let { context.getString(R.string.event_today, it.start.format(HHMM), it.title) }
            val message = listOfNotNull(
                eventLine,
                MessageComposer(lang).morningLine(repository.stats(), repository.priorityFor(LocalDate.now())),
            ).joinToString("\n")
            statusTracker.ringing(Instant.ofEpochMilli(now))
            RingState(request, kind, alarm.label, profile, challenge, message, quote = quotes.next(lang))
        }

        val today = LocalDate.now()
        if (ringDay != today) { ringDay = today; ringsToday = 0 }
        val plan = SoundSelector.choose(
            SoundContext(
                kind = kind,
                importance = alarm.importance,
                hour = LocalTime.now().hour,
                ringsToday = ringsToday++,
                snoozeCount = request.snoozeCount,
                hasEarlyEvent = kind == AlarmKind.WAKE && calendar.firstMorningEvent(LocalDate.now())
                    ?.let { it.start.isBefore(LocalTime.of(10, 0)) } == true,
                customUri = alarm.soundUri,
            )
        )

        acquireWakeLock()
        player.start(state.profile, plan)
        // Still in bed after a while? A familiar voice says why it matters. Sooner after a snooze.
        if (kind == AlarmKind.WAKE && settings.current().playRecordings) {
            recordings.randomForMorning()?.let { clip ->
                player.overlay(java.io.File(clip.path), if (request.snoozeCount > 0) 20_000L else 30_000L)
            }
        }
        _state.value = state
    }

    /** Lowers the ring while a voice line plays over it. */
    fun duck(on: Boolean) = player.duck(on)

    /**
     * Returns false when the profile has no snoozes left. Reminders may pass their own
     * [minutes]; wake alarms always use the profile's shrinking snooze.
     */
    fun snooze(minutes: Int? = null): Boolean {
        val s = _state.value ?: return false
        if (!s.canSnooze) return false
        val next = s.request.copy(snoozeCount = s.request.snoozeCount + 1)
        val length = if (s.kind.isNudge && minutes != null) minutes else s.snoozeMinutes
        scheduler.scheduleSnooze(next, length)
        if (s.kind == AlarmKind.WAKE) statusTracker.snoozed(Instant.now(), next.snoozeCount)
        stop()
        return true
    }

    /** Records the morning and returns the session id for the feedback screen (null for reminders). */
    suspend fun dismiss(): Long? {
        val s = _state.value ?: return null
        stop()
        if (s.kind.isNudge) {
            scheduler.rescheduleAll()
            return null
        }
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
