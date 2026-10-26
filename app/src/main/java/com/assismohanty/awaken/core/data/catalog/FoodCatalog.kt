package com.assismohanty.awaken.core.data.catalog

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class FoodSource { USDA_SR, USDA_FNDDS, CUSTOM }

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

/** The bundled USDA FoodData Central catalog (public domain; tools/catalog/README.md). Loaded once, off the main thread. */
@Singleton
class FoodCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: Indexed? = null

    private class Indexed(val foods: List<Food>, val names: List<String>) {
        val byId: Map<String, Food> = foods.associateBy { it.id }
    }

    private suspend fun indexed(): Indexed = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val foods = parse(text)
            Indexed(foods, foods.map { CatalogSearch.normalize(it.name) })
        }.also { cached = it }
    }

    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<Food> = indexed().let { index ->
        withContext(ioDispatcher) { CatalogSearch.search(index.foods, index.names, query, limit) }
    }

    suspend fun byId(id: String): Food? = indexed().byId[id]

    suspend fun size(): Int = indexed().foods.size

    companion object {
        const val ASSET_PATH = "nutrition/foods.json"
        const val SEARCH_LIMIT = 50

        /** `{"nutrients":[keys…],"foods":[[id,name,"S"|"F",[values…],[[label,grams]…]]…]}` as written by build-foods.mjs. */
        fun parse(text: String): List<Food> {
            val root = Json.parseToJsonElement(text).jsonObject
            val columns = root.getValue("nutrients").jsonArray.map { Nutrient.fromKey(it.jsonPrimitive.content) }
            return root.getValue("foods").jsonArray.map { element ->
                val row = element.jsonArray
                val values = row[3].jsonArray
                val nutrients = buildMap {
                    columns.forEachIndexed { i, nutrient ->
                        val value = values[i]
                        if (nutrient != null && value !is JsonNull) value.jsonPrimitive.doubleOrNull?.let { put(nutrient, it) }
                    }
                }
                Food(
                    id = row[0].jsonPrimitive.content,
                    name = row[1].jsonPrimitive.content,
                    source = if (row[2].jsonPrimitive.content == "F") FoodSource.USDA_FNDDS else FoodSource.USDA_SR,
                    per100 = Nutrients(nutrients),
                    portions = row[4].jsonArray.map { it.toPortion() },
                )
            }
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
