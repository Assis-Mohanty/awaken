package com.assismohanty.awaken.reelcounter.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reel_event",
    indices = [
        Index(value = ["pkg", "session_id", "item_index"], unique = true),
        Index(value = ["day"]),
    ],
)
data class ReelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pkg") val pkg: String,
    /** Epoch millis of when the video came into view. */
    @ColumnInfo(name = "ts") val ts: Long,
    @ColumnInfo(name = "item_index") val itemIndex: Int,
    /** ISO game day, yyyy-MM-dd. */
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "session_id") val sessionId: Long,
)
