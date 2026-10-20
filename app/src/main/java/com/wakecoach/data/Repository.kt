package com.wakecoach.data

import com.wakecoach.core.feedback.Feedback
import com.wakecoach.core.feedback.FeedbackStats
import com.wakecoach.core.feedback.WakeOutcome
import java.time.Instant
import java.time.LocalDate

class WakeRepository(private val db: AppDatabase) {
    val alarms = db.alarms()

    suspend fun recordSession(session: WakeSessionEntity): Long = db.sessions().insert(session)

    suspend fun saveFeedback(sessionId: Long, mood: Int, wakeEase: Int, tiredness: Int) =
        db.sessions().saveFeedback(
            FeedbackEntity(sessionId, System.currentTimeMillis(), mood, wakeEase, tiredness)
        )

    suspend fun saveLaterTiredness(sessionId: Long, value: Int) =
        db.sessions().setLaterTiredness(sessionId, value)

    suspend fun stats(): FeedbackStats = FeedbackStats.from(
        db.sessions().recent().map { r ->
            WakeOutcome(
                at = Instant.ofEpochMilli(r.firstRingAt),
                snoozes = r.snoozeCount,
                secondsToDismiss = (r.dismissedAt - r.firstRingAt) / 1000,
                feedback = if (r.mood != null && r.wakeEase != null && r.tiredness != null) {
                    Feedback(Instant.ofEpochMilli(r.at ?: r.firstRingAt), r.mood, r.wakeEase, r.tiredness, r.laterTiredness)
                } else null,
            )
        }
    )

    suspend fun priorityFor(date: LocalDate): String? = db.priorities().forDate(date.toString())?.text

    suspend fun savePriority(date: LocalDate, text: String) =
        db.priorities().save(PriorityEntity(date.toString(), text.trim()))
}
