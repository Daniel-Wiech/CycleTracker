package com.wat.cycletracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperature_entries")
data class TemperatureEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val measuredAt: Long,
    val temperatureCelsius: Double,
    val source: String
)
