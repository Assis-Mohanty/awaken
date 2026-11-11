package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.BodyMeasureLogEntity
import com.assismohanty.awaken.core.data.db.entity.DiaryNoteEntity
import com.assismohanty.awaken.core.data.db.entity.FastingSessionEntity
import com.assismohanty.awaken.core.data.db.entity.MealPlanEntryEntity
import com.assismohanty.awaken.core.data.db.entity.MeasureKind
import com.assismohanty.awaken.core.data.db.entity.ScanFeedbackEntity
import com.assismohanty.awaken.core.data.db.entity.WaterLogEntity
import com.assismohanty.awaken.core.data.db.entity.WeightLogEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Water, weight, measurements, fasting, day notes, the meal plan and scanner feedback (Diet v2). Trackers: rows are edited and deleted freely. */
@Dao
interface BodyLogDao {
    // Water

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_log WHERE game_date = :gameDate")
    fun observeWaterMl(gameDate: LocalDate): Flow<Int>

    @Query("SELECT * FROM water_log WHERE game_date >= :since ORDER BY logged_at")
    suspend fun waterSince(since: LocalDate): List<WaterLogEntity>

    @Upsert
    suspend fun upsertWater(row: WaterLogEntity)

    @Query("DELETE FROM water_log WHERE id = :id")
    suspend fun deleteWater(id: String)

    // Weight and measurements

    @Query("SELECT * FROM weight_log WHERE game_date >= :since ORDER BY game_date, logged_at")
    fun observeWeights(since: LocalDate): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log WHERE game_date >= :since ORDER BY game_date, logged_at")
    suspend fun weightsSince(since: LocalDate): List<WeightLogEntity>

    @Query("SELECT * FROM weight_log ORDER BY game_date DESC, logged_at DESC LIMIT 1")
    suspend fun latestWeight(): WeightLogEntity?

    @Upsert
    suspend fun upsertWeight(row: WeightLogEntity)

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun deleteWeight(id: String)

    @Query("SELECT * FROM body_measure_log WHERE kind = :kind ORDER BY game_date")
    fun observeMeasures(kind: MeasureKind): Flow<List<BodyMeasureLogEntity>>

    @Upsert
    suspend fun upsertMeasure(row: BodyMeasureLogEntity)

    @Query("DELETE FROM body_measure_log WHERE id = :id")
    suspend fun deleteMeasure(id: String)

    // Fasting

    @Query("SELECT * FROM fasting_session WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActiveFast(): Flow<FastingSessionEntity?>

    @Query("SELECT * FROM fasting_session WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun activeFast(): FastingSessionEntity?

    @Query("SELECT * FROM fasting_session WHERE ended_at IS NOT NULL ORDER BY started_at DESC LIMIT :limit")
    fun observeRecentFasts(limit: Int): Flow<List<FastingSessionEntity>>

    @Upsert
    suspend fun upsertFast(row: FastingSessionEntity)

    // Day notes

    @Query("SELECT * FROM diary_note WHERE game_date = :gameDate")
    fun observeNote(gameDate: LocalDate): Flow<DiaryNoteEntity?>

    @Upsert
    suspend fun upsertNote(note: DiaryNoteEntity)

    @Query("DELETE FROM diary_note WHERE game_date = :gameDate")
    suspend fun deleteNote(gameDate: LocalDate)

    // Meal plan

    @Query("UPDATE meal_plan_entry SET meal = :to WHERE meal = :from")
    suspend fun movePlannedMeal(from: String, to: String)

    @Query("SELECT * FROM meal_plan_entry WHERE game_date = :gameDate ORDER BY meal, name")
    fun observePlan(gameDate: LocalDate): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry WHERE game_date BETWEEN :from AND :to ORDER BY game_date, meal, name")
    fun observePlanBetween(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry WHERE id = :id")
    suspend fun planEntry(id: String): MealPlanEntryEntity?

    @Upsert
    suspend fun upsertPlanEntry(row: MealPlanEntryEntity)

    @Query("DELETE FROM meal_plan_entry WHERE id = :id")
    suspend fun deletePlanEntry(id: String)

    // Scanner feedback (stays on the phone)

    @Upsert
    suspend fun upsertFeedback(row: ScanFeedbackEntity)

    @Query("SELECT COUNT(*) FROM scan_feedback WHERE kind = :kind")
    suspend fun feedbackCount(kind: com.assismohanty.awaken.core.data.db.entity.ScanKind): Int

    @Query("SELECT COUNT(*) FROM scan_feedback WHERE kind = :kind AND chosen_food_id IS NOT NULL")
    suspend fun feedbackHits(kind: com.assismohanty.awaken.core.data.db.entity.ScanKind): Int
}
