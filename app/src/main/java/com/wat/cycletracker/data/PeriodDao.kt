package com.wat.cycletracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PeriodDao {

    @Insert
    suspend fun insert(entry: PeriodEntry): Long

    @Update
    suspend fun update(entry: PeriodEntry)

    @Delete
    suspend fun delete(entry: PeriodEntry)
    @Query(
        "SELECT * FROM period_entries ORDER BY startDate DESC"
    )
    suspend fun getAll(): List<PeriodEntry>
    @Query(
        "SELECT * FROM period_entries " +
                "ORDER BY startDate DESC LIMIT 1"
    )
    suspend fun getLatest(): PeriodEntry?
    @Query(
        "SELECT * FROM period_entries " +
                "WHERE startDate <= :endMillis " +
                "AND (endDate IS NULL OR endDate >= :startMillis) " +
                "ORDER BY startDate ASC"
    )
    suspend fun getBetween(
        startMillis: Long,
        endMillis: Long
    ): List<PeriodEntry>
}
