package com.wakecoach.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: WakeSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFeedback(feedback: FeedbackEntity)

    @Query("UPDATE feedback SET laterTiredness = :value WHERE sessionId = :sessionId")
    suspend fun setLaterTiredness(sessionId: Long, value: Int)

    @Query(
        """SELECT s.id, s.firstRingAt, s.dismissedAt, s.snoozeCount,
                  f.at, f.mood, f.wakeEase, f.tiredness, f.laterTiredness
           FROM wake_sessions s LEFT JOIN feedback f ON f.sessionId = s.id
           ORDER BY s.firstRingAt DESC LIMIT :limit"""
    )
    suspend fun recent(limit: Int = 60): List<SessionWithFeedback>
}

@Dao
interface PriorityDao {
    @Upsert
    suspend fun save(p: PriorityEntity)

    @Query("SELECT * FROM priorities WHERE date = :date")
    suspend fun forDate(date: String): PriorityEntity?
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    suspend fun all(): List<RecordingEntity>

    @Insert
    suspend fun insert(r: RecordingEntity): Long

    @Delete
    suspend fun delete(r: RecordingEntity)
}

@Dao
interface GuardDao {
    @Insert
    suspend fun logRing(r: RingLogEntity)

    @Query("SELECT * FROM ring_log WHERE at >= :since")
    suspend fun ringsSince(since: Long): List<RingLogEntity>

    @Query("SELECT COUNT(*) FROM ring_log WHERE alarmId = :alarmId AND at >= :since")
    suspend fun ringCount(alarmId: Long, since: Long): Int

    @Insert
    suspend fun decide(d: GuardDecisionEntity)

    @Query("SELECT * FROM guard_decisions")
    suspend fun decisions(): List<GuardDecisionEntity>

    @Query("DELETE FROM ring_log WHERE at < :before")
    suspend fun pruneRings(before: Long)
}
