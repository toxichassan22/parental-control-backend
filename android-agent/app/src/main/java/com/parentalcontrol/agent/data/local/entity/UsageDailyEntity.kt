package com.parentalcontrol.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_daily")
data class UsageDailyEntity(
    @PrimaryKey val dayKey: String,
    val estimatedBytes: Long,
    val source: String,
    val lockedCount: Int,
    val updatedAt: Long,
)

