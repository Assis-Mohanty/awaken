package com.assismohanty.awaken.core.data.catalog.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * The bundled, read-only food catalog (`assets/nutrition/foods.db`, Diet v2 plan §3.2). Built by
 * `tools/catalog/build-catalog.mjs` from the exported schema of this class; never holds user data, so a version bump
 * simply copies the new asset over the old file.
 */
@Database(
    version = CatalogDatabase.VERSION,
    exportSchema = true,
    entities = [
        CatalogSourceEntity::class,
        CatalogFoodEntity::class,
        CatalogPortionEntity::class,
        CatalogAliasEntity::class,
        CatalogFoodFts::class,
        CatalogMetaEntity::class,
    ],
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val VERSION = 1
        const val NAME = "foods.db"
        const val ASSET_PATH = "nutrition/foods.db"
    }
}

/** A dataset the foods come from, with its licence and the attribution it requires. */
@Entity(tableName = "source")
data class CatalogSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val licence: String,
    val url: String,
    val version: String,
    val attribution: String,
)

/** One food with its nutrients per 100 g. A missing value is unknown, never zero. */
@Entity(
    tableName = "food",
    foreignKeys = [ForeignKey(CatalogSourceEntity::class, parentColumns = ["id"], childColumns = ["source_id"])],
    indices = [Index("source_id")],
)
data class CatalogFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    /** CURATED, DERIVED or ESTIMATED. */
    val tier: String,
    /** VEG, EGG, NONVEG, or null when unknown. */
    @ColumnInfo(name = "diet_tag") val dietTag: String?,
    val category: String?,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    /** Every other nutrient by [com.assismohanty.awaken.engine.body.Nutrient.key]; may carry a `_from` note. */
    @ColumnInfo(name = "nutrients_json") val nutrientsJson: String,
    @ColumnInfo(name = "kcal_low") val kcalLow: Double?,
    @ColumnInfo(name = "kcal_high") val kcalHigh: Double?,
    val popularity: Int,
)

/** A household measure: "1 katori" = 150 g. */
@Entity(
    tableName = "portion",
    primaryKeys = ["food_id", "position"],
    foreignKeys = [ForeignKey(CatalogFoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
)
data class CatalogPortionEntity(
    @ColumnInfo(name = "food_id") val foodId: String,
    val position: Int,
    /** KATORI, ROTI, PIECE, CUP, TBSP, TSP, GLASS, SLICE, PLATE or OTHER. */
    val unit: String,
    val label: String,
    val grams: Double,
)

/** Another name for a food. [lang] is `en`, `hi` (Devanagari) or `hi-Latn` (transliteration). */
@Entity(
    tableName = "alias",
    primaryKeys = ["food_id", "alias"],
    foreignKeys = [ForeignKey(CatalogFoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
)
data class CatalogAliasEntity(
    @ColumnInfo(name = "food_id") val foodId: String,
    val alias: String,
    val lang: String,
)

/** Full-text index; `rowid` is the rowid of the food row. [keys] holds the search keys of the name and every alias. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "food_fts")
data class CatalogFoodFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val name: String,
    val aliases: String,
    val keys: String,
)

@Entity(tableName = "meta")
data class CatalogMetaEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface CatalogDao {
    /** Foods whose search keys match [match] (an FTS4 query), best first. */
    @Query(
        "SELECT * FROM food WHERE rowid IN (SELECT rowid FROM food_fts WHERE food_fts MATCH :match) " +
            "ORDER BY popularity DESC, length(name), name LIMIT :limit",
    )
    suspend fun search(match: String, limit: Int): List<CatalogFoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun food(id: String): CatalogFoodEntity?

    @Query("SELECT * FROM portion WHERE food_id IN (:ids) ORDER BY food_id, position")
    suspend fun portions(ids: List<String>): List<CatalogPortionEntity>

    @Query("SELECT COUNT(*) FROM food")
    suspend fun count(): Int

    @Query("SELECT * FROM source ORDER BY id")
    suspend fun sources(): List<CatalogSourceEntity>

    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun meta(key: String): String?
}
