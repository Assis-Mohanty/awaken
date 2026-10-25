package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.CampaignEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

/** The 45-day Campaign (GD §6.8). At most one row is ACTIVE at a time. */
@Dao
interface CampaignDao {
    @Upsert
    suspend fun upsert(campaign: CampaignEntity)

    @Query("SELECT * FROM campaign WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun active(): CampaignEntity?

    /** The active campaign, or else the most recently started one (so a finished campaign can show its result). */
    @Query("SELECT * FROM campaign ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, start_date DESC LIMIT 1")
    fun observeCurrent(): Flow<CampaignEntity?>

    @Query("SELECT COUNT(*) FROM campaign WHERE status = 'COMPLETED'")
    suspend fun countCompleted(): Int

    @Query("SELECT * FROM habit WHERE campaign_id = :campaignId")
    suspend fun habits(campaignId: String): List<HabitEntity>

    @Query("SELECT * FROM habit WHERE campaign_id = :campaignId")
    fun observeHabits(campaignId: String): Flow<List<HabitEntity>>
}
