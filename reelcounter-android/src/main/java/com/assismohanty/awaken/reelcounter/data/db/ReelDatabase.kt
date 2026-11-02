package com.assismohanty.awaken.reelcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReelEventEntity::class, DailyTotalEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun dao(): ReelDao
}
