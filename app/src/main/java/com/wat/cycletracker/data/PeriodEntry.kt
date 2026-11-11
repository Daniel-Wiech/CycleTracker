package com.wat.cycletracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "period_entries")
data class PeriodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: Long,
    val endDate: Long? = null,
    val notes: String = ""
)
