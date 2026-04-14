package com.parentalcontrol.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parentalcontrol.agent.data.local.entity.UsageDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UsageDailyEntity)

    @Query("SELECT * FROM usage_daily ORDER BY dayKey DESC LIMIT :limit")
    fun observeRecent(limit: Int = 7): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily WHERE dayKey = :dayKey LIMIT 1")
    suspend fun findByDayKey(dayKey: String): UsageDailyEntity?
}

