package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.CustomFoodEntity
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.NutritionProfileEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Food diary (GD §13.1). Not a ledger: rows are edited and deleted freely. */
@Dao
interface DietDao {
    @Query("SELECT * FROM nutrition_profile WHERE id = 1")
    fun observeProfile(): Flow<NutritionProfileEntity?>

    @Query("SELECT * FROM nutrition_profile WHERE id = 1")
    suspend fun profile(): NutritionProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: NutritionProfileEntity)

    @Query("SELECT * FROM food_log WHERE game_date = :gameDate ORDER BY logged_at")
    fun observeDay(gameDate: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE id = :id")
    suspend fun logRow(id: String): FoodLogEntity?

    @Upsert
    suspend fun upsertLog(row: FoodLogEntity)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun deleteLog(id: String)

    /** The latest row of each distinct food, newest first: "recent foods" in search. */
    @Query(
        "SELECT * FROM food_log f WHERE f.logged_at = (SELECT MAX(g.logged_at) FROM food_log g WHERE g.food_id = f.food_id) " +
            "ORDER BY f.logged_at DESC LIMIT :limit",
    )
    suspend fun recentFoods(limit: Int): List<FoodLogEntity>

    @Query("SELECT * FROM custom_food ORDER BY name")
    fun observeCustomFoods(): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food")
    suspend fun customFoods(): List<CustomFoodEntity>

    @Upsert
    suspend fun upsertCustomFood(food: CustomFoodEntity)

    @Query("DELETE FROM custom_food WHERE id = :id")
    suspend fun deleteCustomFood(id: String)
}
