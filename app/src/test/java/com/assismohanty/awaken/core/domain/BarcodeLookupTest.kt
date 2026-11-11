package com.assismohanty.awaken.core.domain

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

/** A scan is looked up in the hunter's own foods first, then the optional pack; a miss says which code to fill in (plan 6.4). */
@RunWith(AndroidJUnit4::class)
class BarcodeLookupTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

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
    fun anUnknownCodeIsNotFoundAndCarriesItsCanonicalForm() = runTest {
        val result = graph.lookUpBarcode(ScannedCode("5901234123457", upcE = false))
        assertThat(result).isEqualTo(BarcodeResult.NotFound("5901234123457"))
    }

    @Test
    fun aCodeWithABadCheckDigitIsInvalid() = runTest {
        assertThat(graph.lookUpBarcode(ScannedCode("5901234123458", upcE = false))).isEqualTo(BarcodeResult.Invalid)
    }

    @Test
    fun withNoPackInTheAppThePackIsSimplyEmpty() = runTest {
        assertThat(graph.packagedCatalog.available).isFalse()
        assertThat(graph.packagedCatalog.lookup("5901234123457")).isNull()
    }
}
