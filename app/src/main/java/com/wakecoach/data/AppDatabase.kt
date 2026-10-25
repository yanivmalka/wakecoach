package com.wakecoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, WakeSessionEntity::class, FeedbackEntity::class, PriorityEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun sessions(): SessionDao
    abstract fun priorities(): PriorityDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wakecoach.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 0.2: alarms gain a kind (wake-up or daytime reminder). Existing alarms are wake-ups. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN kind TEXT NOT NULL DEFAULT 'WAKE'")
            }
        }
    }
}
