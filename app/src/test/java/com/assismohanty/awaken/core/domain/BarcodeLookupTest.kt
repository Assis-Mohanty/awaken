package com.assismohanty.awaken.core.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** A scan is looked up in the hunter's own foods first, then the packaged-food pack; a miss says which code to fill in (plan 6.4). */
@RunWith(AndroidJUnit4::class)
class BarcodeLookupTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    /** A product in the shipped pack (see tools/packaged-pack/report.md): Farmley raw sunflower seeds, 20 g serving. */
    private val packBarcode = "8906120107181"

    @Test
    fun aSavedCustomFoodIsFoundByItsBarcodeInEitherForm() = runTest {
        graph.foodLibrary.saveCustomFood(
            CustomFoodDraft(name = "Protein bar", barcode = "036000291452", per100 = Nutrients(mapOf(Nutrient.KCAL to 380.0))),
        )

        // The scanner reports a UPC-A as 12 digits; the stored code is the 13-digit form.
        val found = graph.lookUpBarcode(ScannedCode("036000291452", upcE = false))

        assertThat(found).isInstanceOf(BarcodeResult.Found::class.java)
        assertThat((found as BarcodeResult.Found).food.name).isEqualTo("Protein bar")
        assertThat(found.fromPack).isFalse()
    }

    @Test
    fun aProductInThePackIsFoundWithItsLabelServing() = runTest {
        assertThat(graph.packagedCatalog.available).isTrue()

        val found = graph.lookUpBarcode(ScannedCode(packBarcode, upcE = false)) as BarcodeResult.Found

        assertThat(found.fromPack).isTrue()
        assertThat(found.food.name).isEqualTo("Raw Sunflower Seeds (Farmley)")
        assertThat(found.food.per100[Nutrient.KCAL]).isEqualTo(625.0)
        assertThat(found.food.per100[Nutrient.PROTEIN]).isEqualTo(23.1)
        assertThat(found.food.portions.single().grams).isEqualTo(20.0)
    }

    @Test
    fun theHuntersOwnFoodWinsOverThePackForTheSameCode() = runTest {
        graph.foodLibrary.saveCustomFood(
            CustomFoodDraft(name = "My sunflower seeds", barcode = packBarcode, per100 = Nutrients(mapOf(Nutrient.KCAL to 600.0))),
        )

        val found = graph.lookUpBarcode(ScannedCode(packBarcode, upcE = false)) as BarcodeResult.Found

        assertThat(found.fromPack).isFalse()
        assertThat(found.food.name).isEqualTo("My sunflower seeds")
    }

    @Test
    fun anUnknownCodeIsNotFoundAndCarriesItsCanonicalForm() = runTest {
        val result = graph.lookUpBarcode(ScannedCode("5901234123457", upcE = false))
        assertThat(result).isEqualTo(BarcodeResult.NotFound("5901234123457"))
    }

    @Test
    fun aCodeWithABadCheckDigitIsInvalid() = runTest {
        assertThat(graph.lookUpBarcode(ScannedCode("5901234123458", upcE = false))).isEqualTo(BarcodeResult.Invalid)
    }

    @Test
    fun theShippedPackIsSmallAndNotMergedIntoTheFoodCatalog() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val size = assets.open("nutrition/packaged.db").use { it.available() }
        assertThat(size).isLessThan(3 * 1024 * 1024)
        assertThat(assets.list("nutrition")!!.toList()).containsAtLeast("foods.db", "packaged.db")
    }
}
