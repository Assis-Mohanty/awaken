package com.assismohanty.awaken.core.domain

import android.graphics.Bitmap
import android.net.Uri
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.scan.DishClassifier
import com.assismohanty.awaken.core.scan.PhotoLoader
import com.assismohanty.awaken.engine.body.DishScan
import javax.inject.Inject

sealed interface DishScanResult {
    /** Up to three foods from the catalog, best first. */
    data class Suggested(val foods: List<Food>) : DishScanResult
    data object Unsure : DishScanResult
    /** The model could not run, or the picked photo could not be opened. */
    data object Failed : DishScanResult
}

/**
 * A photo of a meal to food suggestions (plan 5.1): the model names dishes, `:engine` [DishScan] turns them into food ids,
 * and the catalog supplies the foods, so every number logged comes from the database. The photo is recycled once read.
 */
class ScanDishUseCase @Inject constructor(
    private val classifier: DishClassifier,
    private val catalog: FoodCatalog,
    private val photos: PhotoLoader,
) {
    /** Whether the Photo button is offered: the build flag, and a model in the app. */
    suspend fun available(): Boolean = DietScanFeature.photoEnabled && classifier.available()

    suspend fun scan(uri: Uri): DishScanResult = photos.load(uri)?.let { scan(it) } ?: DishScanResult.Failed

    suspend fun scan(photo: Bitmap): DishScanResult {
        val guesses = try {
            classifier.top(photo)
        } finally {
            photo.recycle()
        } ?: return DishScanResult.Failed
        return when (val s = DishScan.suggest(guesses, classifier.labelFoods())) {
            DishScan.Suggestions.Unsure -> DishScanResult.Unsure
            // A food id the catalog no longer has is skipped; none left reads as not sure.
            is DishScan.Suggestions.Foods -> catalog.byIds(s.foodIds).takeIf { it.isNotEmpty() }?.let { DishScanResult.Suggested(it) } ?: DishScanResult.Unsure
        }
    }
}
