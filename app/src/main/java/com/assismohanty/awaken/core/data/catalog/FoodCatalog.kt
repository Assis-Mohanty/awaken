package com.assismohanty.awaken.core.data.catalog

import android.content.Context
import androidx.room.Room
import com.assismohanty.awaken.core.data.catalog.db.CatalogDao
import com.assismohanty.awaken.core.data.catalog.db.CatalogDatabase
import com.assismohanty.awaken.core.data.catalog.db.CatalogFoodEntity
import com.assismohanty.awaken.core.data.catalog.db.CatalogPortionEntity
import com.assismohanty.awaken.core.data.catalog.db.CatalogSourceEntity
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.FoodSearch
import com.assismohanty.awaken.engine.body.SearchCandidate
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class FoodSource { USDA_SR, USDA_FNDDS, COFID, AWAKEN, CUSTOM, RECIPE }

/** A household measure: "1 cup" = 163 g. */
data class Portion(val label: String, val grams: Double)

/** A food with its nutrients per 100 g. */
data class Food(
    val id: String,
    val name: String,
    val source: FoodSource,
    val per100: Nutrients,
    val portions: List<Portion> = emptyList(),
)

/**
 * The bundled food catalog (`assets/nutrition/foods.db`; sources and licences in `tools/catalog/README.md`). Room copies
 * the asset on first use and every query runs off the main thread.
 */
@Singleton
class FoodCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dao: CatalogDao by lazy {
        Room.databaseBuilder(context, CatalogDatabase::class.java, CatalogDatabase.NAME)
            .createFromAsset(CatalogDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(true) // no user data lives here: a new asset replaces the old file
            .build()
            .catalogDao()
    }

    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<Food> = withContext(ioDispatcher) {
        val match = matchOf(query)
        if (match.isEmpty()) emptyList() else dao.search(match, limit).withPortions()
    }

    suspend fun byId(id: String): Food? = withContext(ioDispatcher) { dao.food(id)?.let { listOf(it).withPortions().first() } }

    /** The foods with these ids, in the order given; unknown ids are skipped. */
    suspend fun byIds(ids: List<String>): List<Food> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        val found = dao.foods(ids).withPortions().associateBy { it.id }
        ids.mapNotNull { found[it] }
    }

    /** Up to [limit] foods matching [query], unordered for ranking: see `FoodSearchUseCase`. Empty for a blank query. */
    suspend fun candidates(query: String, limit: Int = CANDIDATE_LIMIT): List<SearchCandidate> = withContext(ioDispatcher) {
        val match = matchOf(query)
        if (match.isEmpty()) emptyList() else dao.candidates(match, limit).map { c ->
            SearchCandidate(c.id, c.name, c.aliases?.split(ALIAS_SEPARATOR).orEmpty(), c.popularity, tierRank(c.tier))
        }
    }

    suspend fun size(): Int = withContext(ioDispatcher) { dao.count() }

    /** Every source in the catalog, for the data sources screen. */
    suspend fun sources(): List<CatalogSourceEntity> = withContext(ioDispatcher) { dao.sources() }

    private fun matchOf(query: String): String =
        FoodSearch.key(query).split(' ').filter { it.isNotEmpty() }.joinToString(" ") { "keys:$it*" }

    private suspend fun List<CatalogFoodEntity>.withPortions(): List<Food> {
        if (isEmpty()) return emptyList()
        val portions = dao.portions(map { it.id }).groupBy { it.foodId }
        return map { it.toFood(portions[it.id].orEmpty()) }
    }

    private fun CatalogFoodEntity.toFood(portions: List<CatalogPortionEntity>): Food {
        val known = buildMap {
            put(Nutrient.KCAL, kcal)
            protein?.let { put(Nutrient.PROTEIN, it) }
            carbs?.let { put(Nutrient.CARBS, it) }
            fat?.let { put(Nutrient.FAT, it) }
        }
        return Food(
            id = id,
            name = name,
            source = when (sourceId) {
                "usda_fndds" -> FoodSource.USDA_FNDDS
                "cofid" -> FoodSource.COFID
                "awaken" -> FoodSource.AWAKEN
                else -> FoodSource.USDA_SR
            },
            per100 = Nutrients(nutrientsFromJson(nutrientsJson).values + known),
            portions = portions.map { Portion(it.label, it.grams) },
        )
    }

    companion object {
        const val SEARCH_LIMIT = 50
        const val CANDIDATE_LIMIT = 500
        private const val ALIAS_SEPARATOR = " | "

        /** CURATED, DERIVED, ESTIMATED: best first. */
        fun tierRank(tier: String): Int = when (tier) {
            "CURATED" -> 0
            "DERIVED" -> 1
            else -> 2
        }

        private fun JsonElement.toPortion(): Portion {
            val pair = jsonArray
            return Portion(pair[0].jsonPrimitive.content, pair[1].jsonPrimitive.doubleOrNull ?: 0.0)
        }

        // Shared JSON shapes for snapshot columns (food_log.nutrients_json, custom_food.*_json).

        fun nutrientsToJson(nutrients: Nutrients): String =
            JsonObject(nutrients.values.entries.associate { (n, v) -> n.key to JsonPrimitive(v) }).toString()

        fun nutrientsFromJson(text: String): Nutrients = Nutrients(
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull().orEmpty().mapNotNull { (key, value) ->
                val nutrient = Nutrient.fromKey(key) ?: return@mapNotNull null
                (value as? JsonPrimitive)?.doubleOrNull?.let { nutrient to it }
            }.toMap(),
        )

        fun portionsToJson(portions: List<Portion>): String =
            JsonArray(portions.map { JsonArray(listOf(JsonPrimitive(it.label), JsonPrimitive(it.grams))) }).toString()

        fun portionsFromJson(text: String): List<Portion> =
            runCatching { Json.parseToJsonElement(text).jsonArray.map { it.toPortion() } }.getOrDefault(emptyList())
    }
}
