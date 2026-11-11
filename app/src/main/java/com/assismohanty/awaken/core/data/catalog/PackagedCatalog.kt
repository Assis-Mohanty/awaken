package com.assismohanty.awaken.core.data.catalog

import android.content.Context
import androidx.room.Room
import com.assismohanty.awaken.core.data.catalog.db.PackagedDao
import com.assismohanty.awaken.core.data.catalog.db.PackagedDatabase
import com.assismohanty.awaken.core.data.catalog.db.PackagedProductEntity
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Products with a barcode from the optional pack. With no pack file in the app, every lookup is a miss and nothing is opened. */
@Singleton
class PackagedCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** True when this build carries a pack. */
    val available: Boolean by lazy { context.assets.list(PackagedDatabase.ASSET_DIR)?.contains(PackagedDatabase.NAME) == true }

    private val dao: PackagedDao by lazy {
        Room.databaseBuilder(context, PackagedDatabase::class.java, PackagedDatabase.NAME)
            .createFromAsset(PackagedDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(true) // read-only data: a new asset replaces the old file
            .build()
            .packagedDao()
    }

    /** The product for a canonical 13-digit [barcode], or null. */
    suspend fun lookup(barcode: String): Food? =
        if (!available) null else withContext(ioDispatcher) { dao.product(barcode)?.toFood() }

    private fun PackagedProductEntity.toFood(): Food {
        val values = buildMap {
            put(Nutrient.KCAL, kcal)
            protein?.let { put(Nutrient.PROTEIN, it) }
            carbs?.let { put(Nutrient.CARBS, it) }
            fat?.let { put(Nutrient.FAT, it) }
            sugars?.let { put(Nutrient.SUGARS, it) }
            satFat?.let { put(Nutrient.SAT_FAT, it) }
            fiber?.let { put(Nutrient.FIBER, it) }
            sodiumMg?.let { put(Nutrient.SODIUM, it) }
        }
        val title = if (brand.isNullOrBlank()) name else "$name ($brand)"
        return Food(
            id = "$ID_PREFIX$barcode",
            name = title,
            source = FoodSource.CUSTOM,
            per100 = Nutrients(values),
            portions = listOfNotNull(servingGrams?.takeIf { it > 0.0 }?.let { Portion(SERVING_LABEL, it) }),
        )
    }

    companion object {
        const val ID_PREFIX = "pack:"
        const val SERVING_LABEL = "1 serving"
    }
}
