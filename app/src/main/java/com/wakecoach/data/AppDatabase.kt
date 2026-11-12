package com.wakecoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, WakeSessionEntity::class, FeedbackEntity::class, PriorityEntity::class, RecordingEntity::class,
        RingLogEntity::class, GuardDecisionEntity::class, SkillPresetEntity::class, CustomProfileEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun sessions(): SessionDao
    abstract fun priorities(): PriorityDao
    abstract fun recordings(): RecordingDao
    abstract fun guard(): GuardDao
    abstract fun skills(): SkillDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wakecoach.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

        /** 0.7: one-time reminders on chosen weekdays. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN once INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.7: saved skills and user-made profiles. SQL mirrors the entities exactly. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skill_presets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`config` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`intensity` TEXT NOT NULL, `maxSnoozes` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL, " +
                        "`shrinkMinutes` INTEGER NOT NULL, `crescendoSeconds` INTEGER NOT NULL, `vibrate` INTEGER NOT NULL, " +
                        "`skills` TEXT NOT NULL, `mode` TEXT NOT NULL)"
                )
            }
        }
    }
}
