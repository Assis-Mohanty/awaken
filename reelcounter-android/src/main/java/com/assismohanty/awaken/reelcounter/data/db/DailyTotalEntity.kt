package com.assismohanty.awaken.reelcounter.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "daily_total", primaryKeys = ["date", "pkg"])
data class DailyTotalEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "pkg") val pkg: String,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "seconds") val seconds: Long,
    @ColumnInfo(name = "snoozes") val snoozes: Int,
)
