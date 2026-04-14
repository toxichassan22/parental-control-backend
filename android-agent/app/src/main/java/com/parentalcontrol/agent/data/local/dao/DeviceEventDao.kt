package com.parentalcontrol.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parentalcontrol.agent.data.local.entity.DeviceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DeviceEventEntity)

    @Query("SELECT * FROM device_event ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<DeviceEventEntity>>
}

