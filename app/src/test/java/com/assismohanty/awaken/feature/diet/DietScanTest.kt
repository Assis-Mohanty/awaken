package com.assismohanty.awaken.feature.diet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.domain.CustomFoodDraft
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.testing.FakePhoneStepsGateway
import com.assismohanty.awaken.testing.NoDietReminders
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The barcode scan through the Diet ViewModel: found, not found then added from the label, and a misread (plan section 6.4). */
@RunWith(AndroidJUnit4::class)
class DietScanTest {
    private val graph = TestGraph()
    private val today = LocalDate.of(2026, 10, 18)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        graph.close()
    }

    private fun viewModel() = DietViewModel(
        graph.diet, graph.foodSearch, graph.foodLibrary, graph.bodyLog, graph.uiPrefs, NoDietReminders, graph.recipeBook,
        LoadHealthOverviewUseCase(graph.db.hunterDao(), graph.health, FakePhoneStepsGateway(), graph.clock), graph.lookUpBarcode,
    )

    private suspend fun DietViewModel.until(condition: (DietUiState) -> Boolean): DietUiState = withTimeout(TIMEOUT_MS) { uiState.first(condition) }

    @Test
    fun aKnownCodeIsFoundAddedWithItsServingAndLoggedAsAScan() = runBlocking<Unit> {
        graph.foodLibrary.saveCustomFood(
            CustomFoodDraft(
                name = "Protein bar", barcode = "036000291452", per100 = Nutrients(mapOf(Nutrient.KCAL to 380.0, Nutrient.PROTEIN to 30.0)),
                portions = listOf(Portion("1 bar", 60.0)), servingGrams = 60.0,
            ),
        )
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("SNACK"))
        vm.onEvent(DietEvent.OpenScanner)
        vm.until { it.scan == ScanState.Scanning }

        vm.onEvent(DietEvent.BarcodeRead(ScannedCode("036000291452", upcE = false)))
        val found = vm.until { it.scan is ScanState.Found }.scan as ScanState.Found
        assertThat(found.food.name).isEqualTo("Protein bar")
        assertThat(found.fromPack).isFalse()

        vm.onEvent(DietEvent.ScanAccept)
        val amount = vm.until { it.amount != null && it.scan == null }.amount!!
        assertThat(amount.meal).isEqualTo("SNACK")
        assertThat(amount.portion?.label).isEqualTo("1 bar")
        assertThat(amount.source).isEqualTo(EntrySource.BARCODE)

        vm.onEvent(DietEvent.ConfirmAmount)
        vm.until { it.amount == null }
        val row = graph.diet.observeDay(today).first().meals.getValue("SNACK").single().row
        assertThat(row.entrySource).isEqualTo(EntrySource.BARCODE)
        assertThat(row.grams).isEqualTo(60.0)
    }

    @Test
    fun anUnknownCodeLeadsToTheLabelFormWithTheCodeFilledInAndIsFoundNextTime() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("LUNCH"))
        vm.onEvent(DietEvent.OpenScanner)
        vm.onEvent(DietEvent.BarcodeRead(ScannedCode("5901234123457", upcE = false)))
        assertThat((vm.until { it.scan is ScanState.NotFound }.scan as ScanState.NotFound).barcode).isEqualTo("5901234123457")

        vm.onEvent(DietEvent.ScanCreate)
        val form = vm.until { it.scan == null && it.search?.showCustomForm == true }.search!!
        assertThat(form.prefillBarcode).isEqualTo("5901234123457")

        vm.onEvent(DietEvent.CreateCustomFood(CustomFoodDraft(name = "Digestives", barcode = form.prefillBarcode, per100 = Nutrients(mapOf(Nutrient.KCAL to 480.0)))))
        vm.until { it.amount != null }

        vm.onEvent(DietEvent.OpenScanner)
        vm.onEvent(DietEvent.BarcodeRead(ScannedCode("5901234123457", upcE = false)))
        assertThat((vm.until { it.scan is ScanState.Found }.scan as ScanState.Found).food.name).isEqualTo("Digestives")
    }

    @Test
    fun aMisreadSaysSoAndScanAgainGoesBackToScanning() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenScanner)
        vm.onEvent(DietEvent.BarcodeRead(ScannedCode("5901234123458", upcE = false)))
        vm.until { it.scan == ScanState.Invalid }

        vm.onEvent(DietEvent.ScanAgain)
        vm.until { it.scan == ScanState.Scanning }
        vm.onEvent(DietEvent.CloseScanner)
        assertThat(vm.until { it.scan == null }.scan).isNull()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
