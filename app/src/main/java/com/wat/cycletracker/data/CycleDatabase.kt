package com.wat.cycletracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TemperatureEntry::class,
        PeriodEntry::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CycleDatabase : RoomDatabase() {

    abstract fun temperatureDao(): TemperatureDao

    abstract fun periodDao(): PeriodDao
}
