package com.assismohanty.awaken.testing

import android.graphics.Bitmap
import com.assismohanty.awaken.core.scan.DishClassifier
import com.assismohanty.awaken.engine.body.DishScan

/** A classifier that returns the guesses the test gives it (no model runs) and remembers the photos it was shown. */
class FakeDishClassifier(
    var guesses: List<DishScan.Guess>? = emptyList(),
    var foods: Map<String, List<String>> = emptyMap(),
    var hasModel: Boolean = true,
) : DishClassifier {
    val photos = mutableListOf<Bitmap>()

    override suspend fun available(): Boolean = hasModel

    override suspend fun labelFoods(): Map<String, List<String>> = foods

    override suspend fun top(photo: Bitmap, n: Int): List<DishScan.Guess>? {
        photos += photo
        return guesses?.take(n)
    }
}
