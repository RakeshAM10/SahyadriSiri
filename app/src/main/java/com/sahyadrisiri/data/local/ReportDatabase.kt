package com.sahyadrisiri.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database — single source of truth for offline-cached reports.
 * Version 1 — bump version + add migration if schema changes.
 */
@Database(
    entities = [ReportEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReportDatabase : RoomDatabase() {

    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: ReportDatabase? = null

        fun getInstance(context: Context): ReportDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ReportDatabase::class.java,
                    "sahyadri_siri_reports.db"
                )
                .fallbackToDestructiveMigration() // safe for v1 — add proper migrations later
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
