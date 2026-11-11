package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.PackagedCatalog
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.engine.body.Barcode
import javax.inject.Inject

/** What a scan turned into. [NotFound] carries the canonical 13-digit code so "add it from the label" can fill it in. */
sealed interface BarcodeResult {
    data class Found(val food: Food, val fromPack: Boolean) : BarcodeResult
    data class NotFound(val barcode: String) : BarcodeResult
    data object Invalid : BarcodeResult
}

/** The hunter's own foods first (they may have fixed a wrong pack row), then the optional pack (Diet v2 plan section 6.4). */
class LookUpBarcodeUseCase @Inject constructor(
    private val library: FoodLibrary,
    private val pack: PackagedCatalog,
) {
    suspend operator fun invoke(code: ScannedCode): BarcodeResult {
        val canonical = Barcode.canonical(code.raw, code.upcE) ?: return BarcodeResult.Invalid
        library.customFoodByBarcode(canonical)?.let { return BarcodeResult.Found(it, fromPack = false) }
        pack.lookup(canonical)?.let { return BarcodeResult.Found(it, fromPack = true) }
        return BarcodeResult.NotFound(canonical)
    }
}
