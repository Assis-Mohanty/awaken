package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.FavouriteFoodEntity
import com.assismohanty.awaken.core.data.db.entity.MealReminderEntity
import com.assismohanty.awaken.core.data.db.entity.MealSlotEntity
import com.assismohanty.awaken.core.data.db.entity.RecipeEntity
import com.assismohanty.awaken.core.data.db.entity.RecipeIngredientEntity
import com.assismohanty.awaken.core.data.db.entity.SavedMealEntity
import com.assismohanty.awaken.core.data.db.entity.SavedMealItemEntity
import kotlinx.coroutines.flow.Flow

/** The hunter's own food library: meals, favourites, saved meals and recipes (Diet v2). Not a ledger; rows are edited and deleted freely. */
@Dao
interface FoodLibraryDao {
    // Meals

    @Query("SELECT * FROM meal_slot ORDER BY position, id")
    fun observeMealSlots(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slot ORDER BY position, id")
    suspend fun mealSlots(): List<MealSlotEntity>

    @Upsert
    suspend fun upsertMealSlot(slot: MealSlotEntity)

    /** Only custom meals can be deleted; the four built-in slots stay. */
    @Query("DELETE FROM meal_slot WHERE id = :id AND built_in = 0")
    suspend fun deleteCustomMealSlot(id: String): Int

    // Meal reminders

    @Query("SELECT * FROM meal_reminder")
    fun observeMealReminders(): Flow<List<MealReminderEntity>>

    @Query("SELECT * FROM meal_reminder")
    suspend fun mealReminders(): List<MealReminderEntity>

    @Upsert
    suspend fun upsertMealReminder(reminder: MealReminderEntity)

    @Query("DELETE FROM meal_reminder WHERE meal_id = :mealId")
    suspend fun deleteMealReminder(mealId: String)

    // Favourites

    @Query("SELECT food_id FROM favourite_food")
    fun observeFavouriteIds(): Flow<List<String>>

    @Query("SELECT food_id FROM favourite_food")
    suspend fun favouriteIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavourite(favourite: FavouriteFoodEntity): Long

    @Query("DELETE FROM favourite_food WHERE food_id = :foodId")
    suspend fun removeFavourite(foodId: String)

    // Saved meals

    @Query("SELECT * FROM saved_meal ORDER BY name COLLATE NOCASE")
    fun observeSavedMeals(): Flow<List<SavedMealEntity>>

    @Query("SELECT * FROM saved_meal WHERE id = :id")
    suspend fun savedMeal(id: String): SavedMealEntity?

    @Query("SELECT * FROM saved_meal_item WHERE saved_meal_id = :savedMealId ORDER BY position")
    suspend fun savedMealItems(savedMealId: String): List<SavedMealItemEntity>

    @Query("SELECT * FROM saved_meal_item ORDER BY saved_meal_id, position")
    fun observeAllSavedMealItems(): Flow<List<SavedMealItemEntity>>

    @Upsert
    suspend fun upsertSavedMeal(meal: SavedMealEntity)

    @Upsert
    suspend fun upsertSavedMealItems(items: List<SavedMealItemEntity>)

    @Query("DELETE FROM saved_meal_item WHERE saved_meal_id = :savedMealId")
    suspend fun deleteSavedMealItems(savedMealId: String)

    @Query("DELETE FROM saved_meal WHERE id = :id")
    suspend fun deleteSavedMeal(id: String)

    // Recipes

    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    suspend fun recipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun recipe(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredient WHERE recipe_id = :recipeId ORDER BY position")
    suspend fun recipeIngredients(recipeId: String): List<RecipeIngredientEntity>

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertRecipeIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredient WHERE recipe_id = :recipeId")
    suspend fun deleteRecipeIngredients(recipeId: String)

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteRecipe(id: String)
}
