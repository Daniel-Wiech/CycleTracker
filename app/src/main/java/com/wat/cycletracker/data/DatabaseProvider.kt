package com.wat.cycletracker.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {

    private val MIGRATION_1_2 = object : Migration(1, 2) {

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `period_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startDate` INTEGER NOT NULL,
                    `endDate` INTEGER,
                    `notes` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Volatile
    private var INSTANCE: CycleDatabase? = null

    fun getDatabase(context: Context): CycleDatabase {
        return INSTANCE ?: synchronized(this) {

            val instance = Room.databaseBuilder(
                context.applicationContext,
                CycleDatabase::class.java,
                "cycle_tracker_database"
            )
                .addMigrations(MIGRATION_1_2)
                .build()

            INSTANCE = instance
            instance
        }
    }
}
