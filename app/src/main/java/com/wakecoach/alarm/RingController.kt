package com.wakecoach.alarm

import com.wakecoach.data.repeatDays
import com.wakecoach.data.profile
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.wakecoach.core.audio.BuiltinSound
import com.wakecoach.core.audio.SoundPlan
import com.wakecoach.core.audio.SoundRef
import com.wakecoach.core.audio.SoundStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import com.wakecoach.audio.AlarmPlayer
import com.wakecoach.core.adapt.AdaptiveEngine
import com.wakecoach.core.wake.RelapseCheck
import com.wakecoach.core.adapt.MorningInput
import com.wakecoach.core.audio.SoundContext
import com.wakecoach.core.model.ChallengeType
import java.time.ZoneId
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
import com.wakecoach.data.GuardRepository
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

private const val TAG = "RingController"
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
    private val guard: GuardRepository,
    private val wakeCheck: WakeCheck,
) {
    private val _state = MutableStateFlow<RingState?>(null)
    val state: StateFlow<RingState?> = _state

    private val player = AlarmPlayer(context)
    private val challenges = ChallengeGenerator()
    private var wakeLock: PowerManager.WakeLock? = null
    // Alarms rung today, so consecutive reminders get different sounds.
    private var ringDay: LocalDate? = null
    private var ringsToday = 0
    private val ringing = AtomicBoolean(false)

    suspend fun start(incoming: RingRequest) {
        // Claim the ring at once: two alarms landing together must not both start.
        if (!ringing.compareAndSet(false, true)) return
        try {
            startClaimed(incoming)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Safety net: whatever broke while preparing, the alarm still sounds.
            Log.e(TAG, "ring setup failed; ringing with defaults", e)
            if (_state.value == null) {
                val request = incoming.copy(firstRingAt = incoming.firstRingAt.takeIf { it != 0L } ?: System.currentTimeMillis())
                ringWith(RingState(request, AlarmKind.WAKE, "", Profiles.BALANCED, Challenge.None, ""), fallbackPlan())
            }
        }
    }

    /**
     * Order matters: load the alarm, start the sound, publish the screen state, and only then
     * add the extras (message, quote, calendar, recording). Extras may fail; the ring may not.
     */
    private suspend fun startClaimed(incoming: RingRequest) {
        val alarm = repository.alarms.byId(incoming.alarmId)
        if (alarm == null) { stop(); return } // deleted meanwhile

        val now = System.currentTimeMillis()
        val request = if (incoming.firstRingAt == 0L) incoming.copy(firstRingAt = now) else incoming
        // A "still awake?" check rings like a one-tap reminder, whatever the alarm normally is.
        val kind = if (request.mode == RingMode.ASK) AlarmKind.REMINDER else alarm.alarmKind
        // Only the first ring of a morning counts as a habit: not snoozes, not a re-delivered ring.
        if (incoming.firstRingAt == 0L) guarded("ring log") { guard.logRing(alarm, now) }
        guarded("re-arm") {
            if (kind == AlarmKind.TEST) {
                // A test ring is single-use; drop its row now so it never shows up or re-arms.
                repository.alarms.delete(alarm)
            } else if (request.snoozeCount == 0) {
                // Arm the next occurrence now, so a never-dismissed alarm still rings tomorrow.
                if (alarm.repeatDays.isEmpty()) repository.alarms.upsert(alarm.copy(enabled = false))
                else scheduler.schedule(alarm)
            }
        }

        val lang = Lang.of(Locale.getDefault())
        // Requirement 4: read the user's recent mornings and decide how hard to push today.
        val adapt = if (kind == AlarmKind.WAKE) guarded("adapt") {
            AdaptiveEngine().plan(
                MorningInput(
                    history = repository.recentOutcomes(),
                    zone = ZoneId.systemDefault(),
                    day = LocalDate.now().dayOfWeek,
                    importance = alarm.importance,
                    baseIntensity = alarm.profile.intensity,
                )
            )
        } else null
        val base = if (request.mode == RingMode.ASK) {
            val label = context.getString(R.string.still_awake)
            RingState(request, kind, label, Profiles.REMINDER.copy(snooze = SnoozePolicy(0, 0)), Challenge.None, message = label)
        } else if (kind.isNudge) {
            // A test has nothing to snooze to: its alarm row is already gone.
            val profile = if (kind == AlarmKind.TEST) Profiles.REMINDER.copy(snooze = SnoozePolicy(0, 0)) else Profiles.REMINDER
            RingState(request, kind, alarm.label, profile, Challenge.None, message = alarm.label)
        } else {
            val profile = alarm.profile.let { p -> adapt?.let { p.copy(intensity = it.intensity) } ?: p }
            // A surprise morning always has a challenge, even on a no-challenge profile.
            val types = if (adapt?.surprise == true && profile.challenges.all { it == ChallengeType.NONE }) {
                listOf(ChallengeType.MATH)
            } else profile.challenges
            val challenge = challenges.generate(
                type = challenges.pickType(types),
                intensity = profile.intensity,
                phrases = Phrases.forLang(lang),
                registeredCode = null, // code registration arrives with the scanner in a later phase
            )
            RingState(request, kind, alarm.label, profile, challenge, message = "")
        }

        val event = if (kind == AlarmKind.WAKE) guarded("calendar") { calendar.firstMorningEvent(LocalDate.now()) } else null
        val today = LocalDate.now()
        if (ringDay != today) { ringDay = today; ringsToday = 0 }
        val plan = SoundSelector.choose(
            SoundContext(
                kind = kind,
                // A risky morning sounds more urgent than the alarm's own setting.
                importance = maxOf(
                    alarm.importance,
                    when { (adapt?.risk ?: 0) >= 80 -> 3; (adapt?.risk ?: 0) >= 60 -> 2; else -> 1 },
                ),
                hour = LocalTime.now().hour,
                ringsToday = ringsToday++,
                snoozeCount = request.snoozeCount,
                hasEarlyEvent = event?.start?.isBefore(LocalTime.of(10, 0)) == true,
                customUri = alarm.soundUri,
            )
        )
        ringWith(base, plan)
        if (kind != AlarmKind.WAKE) return

        // Extras for wake-ups. Each is optional and must never undo the ring.
        guarded("status") { statusTracker.ringing(Instant.ofEpochMilli(now)) }
        val eventLine = event?.let { context.getString(R.string.event_today, it.start.format(HHMM), it.title) }
        val morning = guarded("message") {
            val composer = MessageComposer(lang)
            listOfNotNull(
                if (request.mode == RingMode.RELAPSE) context.getString(R.string.relapse_line) else null,
                adapt?.let { composer.riskLine(it.reasons, LocalDate.now().dayOfWeek) },
                composer.morningLine(repository.stats(), repository.priorityFor(LocalDate.now())),
            ).joinToString("\n")
        }
        val quote = guarded("quote") { quotes.next(lang) }
        _state.update { current ->
            if (current?.request != request) current
            else current.copy(message = listOfNotNull(eventLine, morning).joinToString("\n"), quote = quote)
        }
        // Still in bed after a while? A familiar voice says why it matters. Sooner after a snooze.
        guarded("recording") {
            if (settings.current().playRecordings) {
                recordings.randomForMorning()?.let { clip ->
                    player.overlay(java.io.File(clip.path), if (request.snoozeCount > 0) 20_000L else 30_000L)
                }
            }
        }
    }

    private fun ringWith(state: RingState, plan: SoundPlan) {
        acquireWakeLock()
        player.start(state.profile, plan)
        _state.value = state
    }

    private fun fallbackPlan() = SoundPlan(listOf(SoundStage(SoundRef.Builtin(BuiltinSound.URGENT_BEEP), 0)))

    private inline fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "optional ring step '$what' failed", e)
        null
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
            // Answering "still awake?" is proof enough; a reminder has nothing more to do.
            scheduler.rescheduleAll()
            return null
        }
        // A re-ring after going back to sleep is the same morning: no new session or check-in,
        // but one more surprise check while re-rings remain.
        if (s.request.mode == RingMode.RELAPSE) {
            armWakeCheck(s)
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
        armWakeCheck(s)
        scheduler.rescheduleAll()
        return id
    }

    private suspend fun armWakeCheck(s: RingState) {
        if (s.kind != AlarmKind.WAKE || !WakeCheck.mayRingAgain(s.request)) return
        guarded("wake check") {
            if (settings.current().wakeCheck) {
                wakeCheck.arm(s.request, scheduler, RelapseCheck.delayMinutes(s.profile.intensity))
            }
        }
    }

    private fun stop() {
        player.stop()
        ringing.set(false)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _state.value = null
        context.stopService(Intent(context, AlarmService::class.java))
    }

    private fun acquireWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        val pm = context.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeCoach:ring").apply {
            acquire(30 * 60_000L) // hard cap in case the user never responds
        }
    }
}
