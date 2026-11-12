package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.BuildConfig

/**
 * Whether the photo food scanner may be offered. Off in every build until a model passes the go / no-go test (GD 20.6,
 * docs/diet/SCANNER_TEST.md); even when on, the Photo button needs a model in the app (`DishClassifier.available`).
 * The barcode scanner has no flag.
 */
object DietScanFeature {
    val photoEnabled: Boolean get() = BuildConfig.DIET_PHOTO_SCAN_ENABLED
}
