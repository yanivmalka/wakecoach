package com.wakecoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, WakeSessionEntity::class, FeedbackEntity::class, PriorityEntity::class, RecordingEntity::class,
        RingLogEntity::class, GuardDecisionEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun sessions(): SessionDao
    abstract fun priorities(): PriorityDao
    abstract fun recordings(): RecordingDao
    abstract fun guard(): GuardDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wakecoach.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** 0.2: alarms gain a kind (wake-up or daytime reminder). Existing alarms are wake-ups. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN kind TEXT NOT NULL DEFAULT 'WAKE'")
            }
        }

        /** 0.4: each alarm may pick its own sound (null keeps the automatic choice). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN soundUri TEXT")
            }
        }

        /** 0.4: personal voice recordings. SQL mirrors Room's schema for RecordingEntity exactly. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL, " +
                        "`owner` TEXT NOT NULL, `purpose` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** 0.5: alarm guard (ring log + answers) and "skip once". SQL mirrors the entities exactly. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN skipDate INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ring_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alarmId` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `label` TEXT NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `guard_decisions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `slotKey` TEXT NOT NULL, " +
                        "`date` INTEGER, `decision` TEXT NOT NULL)"
                )
            }
        }
    }
}
