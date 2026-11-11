package com.assismohanty.awaken.core.data.catalog.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * The optional packaged-food pack (`assets/nutrition/packaged.db`, Diet v2 plan section 6.2): products with a barcode and
 * their nutrients per 100 g. It is its own file, never merged into `foods.db` (the Open Database License shares alike). No
 * pack ships until it can be offered publicly (plan Q4); without the file the app simply finds nothing here.
 */
@Database(version = PackagedDatabase.VERSION, exportSchema = true, entities = [PackagedProductEntity::class])
abstract class PackagedDatabase : RoomDatabase() {
    abstract fun packagedDao(): PackagedDao

    companion object {
        const val VERSION = 1
        const val NAME = "packaged.db"
        const val ASSET_DIR = "nutrition"
        const val ASSET_PATH = "$ASSET_DIR/$NAME"
    }
}

/** One product, keyed by its 13-digit barcode. A missing nutrient is unknown, never zero. */
@Entity(tableName = "product")
data class PackagedProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "serving_grams") val servingGrams: Double?,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val sugars: Double?,
    @ColumnInfo(name = "sat_fat") val satFat: Double?,
    val fiber: Double?,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double?,
)

@Dao
interface PackagedDao {
    @Query("SELECT * FROM product WHERE barcode = :barcode")
    suspend fun product(barcode: String): PackagedProductEntity?
}
