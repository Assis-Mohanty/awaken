package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.FoodLibraryDao
import com.assismohanty.awaken.engine.body.FoodSearch
import com.assismohanty.awaken.engine.body.SearchCandidate
import com.assismohanty.awaken.engine.body.SearchSignals
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One search over everything the hunter can log (Diet v2 plan 3.5): the catalog, their own foods and their recipes, ranked
 * together by `:engine` [FoodSearch.rank] (favourites, then what they logged lately, then exact and prefix matches, then
 * how common the food is). A blank query shows what they logged most recently.
 */
@Singleton
class FoodSearchUseCase @Inject constructor(
    private val catalog: FoodCatalog,
    private val dietDao: DietDao,
    private val library: FoodLibraryDao,
    private val recipes: RecipeBook,
    private val foodLibrary: FoodLibrary,
) {
    suspend fun search(query: String, today: LocalDate, limit: Int = FoodCatalog.SEARCH_LIMIT): List<Food> {
        if (FoodSearch.key(query).isEmpty()) return recent(limit)
        val tokens = FoodSearch.key(query).split(' ')
        val own = dietDao.customFoods().filter { matches(tokens, "${it.name} ${it.brand.orEmpty()}") }
        val myRecipes = recipes.allFoods().filter { matches(tokens, it.name) }
        val candidates = catalog.candidates(query) +
            own.map { SearchCandidate(it.id, it.name, listOfNotNull(it.brand), OWN_POPULARITY) } +
            myRecipes.map { SearchCandidate(it.id, it.name, emptyList(), OWN_POPULARITY) }
        val signals = SearchSignals(
            favourites = library.favouriteIds().toSet(),
            recentUse = dietDao.foodUseSince(today.minusDays(RECENT_DAYS)).associate { it.foodId to it.uses },
        )
        val ranked = FoodSearch.rank(candidates, query, signals, limit).map { it.id }
        val ownById = own.associate { it.id to it.toFood() } + myRecipes.associateBy { it.id }
        val fromCatalog = catalog.byIds(ranked.filter { it !in ownById }).associateBy { it.id }
        return ranked.mapNotNull { ownById[it] ?: fromCatalog[it] }
    }

    /** The foods logged most recently, newest first, each once. */
    suspend fun recent(limit: Int = RECENT_LIMIT): List<Food> =
        dietDao.recentFoods(limit).map { row ->
            // A food that has since been deleted still shows, from the numbers saved with the diary row.
            foodLibrary.foodById(row.foodId) ?: Food(row.foodId, row.name, FoodSource.USDA_SR, FoodCatalog.nutrientsFromJson(row.nutrientsJson))
        }

    /** The starred foods, A to Z. */
    suspend fun favourites(): List<Food> = library.favouriteIds().mapNotNull { foodLibrary.foodById(it) }.sortedBy { it.name.lowercase() }

    /** Every query word must start some word of [text], as in the catalog's own full-text search. */
    private fun matches(tokens: List<String>, text: String): Boolean {
        val words = FoodSearch.key(text).split(' ')
        return tokens.all { t -> words.any { it.startsWith(t) } }
    }

    companion object {
        const val RECENT_DAYS = 30L
        const val RECENT_LIMIT = 20

        /** The hunter's own foods and recipes rank above generic foods of the same match quality (the most common catalog food is 99). */
        const val OWN_POPULARITY = 100
    }
}
