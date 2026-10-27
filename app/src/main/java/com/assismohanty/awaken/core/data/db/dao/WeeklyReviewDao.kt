package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assismohanty.awaken.core.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {
    /** -1 when the week already has a review (GD §14.4: one per week, idempotent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(review: WeeklyReviewEntity): Long

    @Query("SELECT COUNT(*) FROM weekly_review")
    suspend fun count(): Int

    @Query("SELECT iso_week FROM weekly_review")
    fun observeSubmittedWeeks(): Flow<List<String>>

    @Query("SELECT iso_week FROM weekly_review")
    suspend fun submittedWeeks(): List<String>
}
