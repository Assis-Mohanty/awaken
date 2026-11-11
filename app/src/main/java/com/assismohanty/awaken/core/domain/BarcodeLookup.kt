package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.PackagedCatalog
import com.assismohanty.awaken.core.data.food.OnlineFoodSource
import com.assismohanty.awaken.core.data.food.OnlineLookup
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.engine.body.Barcode
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Where a found product came from. */
enum class FoodOrigin { OWN, PACK, ONLINE }

/** Why an online answer is missing. [NEEDS_CONSENT] means the hunter has not been asked yet. */
enum class OnlineStatus { NEEDS_CONSENT, DECLINED, NOT_LISTED, UNUSABLE, UNREACHABLE }

/** What a scan turned into. [NotFound] carries the canonical 13-digit code so "add it from the label" can fill it in. */
sealed interface BarcodeResult {
    data class Found(val food: Food, val origin: FoodOrigin) : BarcodeResult
    data class NotFound(val barcode: String, val online: OnlineStatus) : BarcodeResult
    data object Invalid : BarcodeResult
}

/**
 * The hunter's own foods first (they may have fixed a wrong row), then the pack on the phone, then, only if they have
 * switched it on, the online list (Diet v2 plan section 6.4; ENGINE_DECISIONS #204). The first two never use the network.
 */
class LookUpBarcodeUseCase @Inject constructor(
    private val library: FoodLibrary,
    private val pack: PackagedCatalog,
    private val onlineSource: OnlineFoodSource,
    private val prefs: UiPrefsRepository,
) {
    suspend operator fun invoke(code: ScannedCode): BarcodeResult {
        val canonical = Barcode.canonical(code.raw, code.upcE) ?: return BarcodeResult.Invalid
        library.customFoodByBarcode(canonical)?.let { return BarcodeResult.Found(it, FoodOrigin.OWN) }
        pack.lookup(canonical)?.let { return BarcodeResult.Found(it, FoodOrigin.PACK) }
        return when (prefs.prefs.first().onlineFoodLookup) {
            null -> BarcodeResult.NotFound(canonical, OnlineStatus.NEEDS_CONSENT)
            false -> BarcodeResult.NotFound(canonical, OnlineStatus.DECLINED)
            true -> online(canonical)
        }
    }

    /** The online step on its own, once the hunter has agreed. Only the 13-digit [barcode] leaves the phone. */
    suspend fun online(barcode: String): BarcodeResult = when (val found = onlineSource.lookup(barcode)) {
        is OnlineLookup.Found -> BarcodeResult.Found(found.food, FoodOrigin.ONLINE)
        OnlineLookup.NotListed -> BarcodeResult.NotFound(barcode, OnlineStatus.NOT_LISTED)
        OnlineLookup.Unusable -> BarcodeResult.NotFound(barcode, OnlineStatus.UNUSABLE)
        OnlineLookup.Unreachable -> BarcodeResult.NotFound(barcode, OnlineStatus.UNREACHABLE)
    }
}
