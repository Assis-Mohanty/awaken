package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.FoodLibraryDao
import com.assismohanty.awaken.core.data.db.entity.RecipeEntity
import com.assismohanty.awaken.core.data.db.entity.RecipeIngredientEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Recipe
import com.assismohanty.awaken.engine.body.RecipeIngredient
import com.assismohanty.awaken.engine.body.RecipeMath
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An ingredient as the hunter adds it: the food's nutrients per 100 g are copied in, like a diary row. */
data class IngredientDraft(val foodId: String, val name: String, val grams: Double, val per100: Nutrients)

data class RecipeDraft(
    val id: String? = null,
    val name: String,
    val servings: Double,
    val cookedGrams: Double? = null,
    val note: String? = null,
    val ingredients: List<IngredientDraft>,
)

/** A saved recipe with its numbers, from `:engine` [RecipeMath]. */
data class RecipeDetail(
    val id: String,
    val name: String,
    val note: String?,
    val recipe: Recipe,
    val ingredientNames: List<String>,
    /** The ingredients as they were added, so the editor can change them. */
    val ingredients: List<IngredientDraft> = emptyList(),
) {
    val perServing: Nutrients get() = RecipeMath.perServing(recipe)
    val per100: Nutrients get() = RecipeMath.per100Cooked(recipe)
}

sealed interface RecipeSaveResult {
    data class Saved(val id: String) : RecipeSaveResult
    data object NeedsName : RecipeSaveResult
    data object NeedsIngredients : RecipeSaveResult
    data object BadServings : RecipeSaveResult
}

/**
 * The hunter's recipes (Diet v2). A recipe is saved with a copy of each ingredient's nutrients, so changing or removing a
 * catalog food never changes a recipe. A recipe is also a food: [food] gives it per 100 g of the finished dish, with
 * "1 serving" as its portion, so it is searched and logged like any other.
 */
@Singleton
class RecipeBook @Inject constructor(
    private val dao: FoodLibraryDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun observeRecipes(): Flow<List<RecipeEntity>> = dao.observeRecipes()

    suspend fun save(draft: RecipeDraft): RecipeSaveResult {
        val name = draft.name.trim().take(MAX_NAME)
        if (name.isEmpty()) return RecipeSaveResult.NeedsName
        if (draft.ingredients.isEmpty() || draft.ingredients.any { it.grams <= 0.0 }) return RecipeSaveResult.NeedsIngredients
        if (draft.servings <= 0.0 || draft.servings > MAX_SERVINGS || draft.cookedGrams?.let { it <= 0.0 } == true) return RecipeSaveResult.BadServings
        val id = draft.id ?: "$PREFIX${UUID.randomUUID()}"
        val now = clock.now()
        transactions.run {
            val created = dao.recipe(id)?.createdAt ?: now
            dao.upsertRecipe(RecipeEntity(id, name, draft.servings, draft.cookedGrams, draft.note?.trim()?.takeIf { it.isNotEmpty() }, created, now))
            dao.deleteRecipeIngredients(id)
            dao.upsertRecipeIngredients(
                draft.ingredients.mapIndexed { i, it ->
                    RecipeIngredientEntity("$id#$i", id, i, it.foodId, it.name, it.grams, FoodCatalog.nutrientsToJson(it.per100))
                },
            )
        }
        return RecipeSaveResult.Saved(id)
    }

    suspend fun delete(id: String) = transactions.run {
        dao.deleteRecipeIngredients(id)
        dao.deleteRecipe(id)
    }

    suspend fun detail(id: String): RecipeDetail? {
        val entity = dao.recipe(id) ?: return null
        val rows = dao.recipeIngredients(id)
        return RecipeDetail(
            id = id,
            name = entity.name,
            note = entity.note,
            recipe = Recipe(rows.map { RecipeIngredient(FoodCatalog.nutrientsFromJson(it.nutrientsJson), it.grams) }, entity.servings, entity.cookedGrams),
            ingredientNames = rows.map { it.name },
            ingredients = rows.map { IngredientDraft(it.foodId, it.name, it.grams, FoodCatalog.nutrientsFromJson(it.nutrientsJson)) },
        )
    }

    /** The recipe as a food, or null when it has no ingredients left to count. */
    suspend fun food(id: String): Food? = detail(id)?.toFood()

    suspend fun allFoods(): List<Food> = dao.recipes().mapNotNull { food(it.id) }

    private fun RecipeDetail.toFood(): Food? {
        if (recipe.ingredients.isEmpty() || recipe.rawGrams <= 0.0) return null
        val servingGrams = RecipeMath.servingGrams(recipe) ?: (recipe.rawGrams / recipe.servings)
        return Food(id, name, FoodSource.RECIPE, per100, listOf(Portion(SERVING_LABEL, servingGrams)))
    }

    companion object {
        const val PREFIX = "recipe:"
        const val SERVING_LABEL = "1 serving"
        const val MAX_NAME = 80
        const val MAX_SERVINGS = 100.0
    }
}
