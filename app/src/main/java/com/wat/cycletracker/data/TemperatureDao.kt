package com.wat.cycletracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface TemperatureDao {

    @Insert
    suspend fun insert(entry: TemperatureEntry)

    @Query("SELECT * FROM temperature_entries ORDER BY measuredAt DESC")
    suspend fun getAll(): List<TemperatureEntry>

    @Query("SELECT * FROM temperature_entries WHERE measuredAt BETWEEN :start AND :end ORDER BY measuredAt ASC")
    suspend fun getBetween(
        start: Long,
        end: Long
    ): List<TemperatureEntry>

    @Delete
    suspend fun delete(entry: TemperatureEntry)
}
