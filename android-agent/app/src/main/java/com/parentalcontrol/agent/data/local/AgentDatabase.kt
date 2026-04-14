package com.parentalcontrol.agent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.parentalcontrol.agent.data.local.dao.DeviceEventDao
import com.parentalcontrol.agent.data.local.dao.UsageDailyDao
import com.parentalcontrol.agent.data.local.entity.DeviceEventEntity
import com.parentalcontrol.agent.data.local.entity.UsageDailyEntity

@Database(
    entities = [UsageDailyEntity::class, DeviceEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun deviceEventDao(): DeviceEventDao

    companion object {
        fun create(context: Context): AgentDatabase {
            return Room.databaseBuilder(
                context,
                AgentDatabase::class.java,
                "agent.db",
            ).fallbackToDestructiveMigration().build()
        }
    }
}

