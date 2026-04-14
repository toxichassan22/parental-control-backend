package com.parentalcontrol.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_event")
data class DeviceEventEntity(
    @PrimaryKey val eventId: String,
    val type: String,
    val severity: String,
    val createdAt: Long,
    val payload: String,
)

