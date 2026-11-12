package com.assismohanty.awaken.feature.diet

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.data.db.entity.ScanKind
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.engine.body.DishScan.Guess
import com.assismohanty.awaken.testing.FakeLabelReader
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

/** The photo food scanner through the ViewModel, with a fake classifier and real catalog foods (plan 5.1, 8.4). */
@RunWith(AndroidJUnit4::class)
class DietDishPhotoTest {
    private val graph = TestGraph()

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
        FakeLabelReader(), graph.scanDish,
    )

    private suspend fun DietViewModel.until(condition: (DietUiState) -> Boolean): DietUiState = withTimeout(5_000) { uiState.first(condition) }

    private fun photo(): Bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

    /** Two real catalog ids behind two labels, so the foods (and every number) come from the database. */
    private suspend fun twoLabels(): Pair<String, String> {
        val rice = graph.foodCatalog.candidates("rice").first().id
        val dal = graph.foodCatalog.candidates("dal").first { it.id != rice }.id
        graph.dishClassifier.foods = mapOf("rice" to listOf(rice), "dal" to listOf(dal))
        return rice to dal
    }

    @Test
    fun thePhotoButtonStaysHiddenWhileTheFlagIsOff() = runBlocking<Unit> {
        val state = viewModel().until { !it.loading }
        // DIET_PHOTO_SCAN_ENABLED is false in every build, even with a model present.
        assertThat(graph.dishClassifier.available()).isTrue()
        assertThat(state.photoScan).isFalse()
    }

    @Test
    fun aGuessBecomesCatalogFoodsAndPickingOneLogsItAsAPhotoEntryThenOffersTheNextItem() = runBlocking<Unit> {
        val (rice, dal) = twoLabels()
        graph.dishClassifier.guesses = listOf(Guess("dal", 0.7), Guess("rice", 0.2))
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("LUNCH"))
        vm.onEvent(DietEvent.OpenDishPhoto)
        val taken = photo()
        vm.onEvent(DietEvent.DishPhotoTaken(taken))

        val suggested = vm.until { it.dishPhoto is DishPhotoState.Suggested }.dishPhoto as DishPhotoState.Suggested
        assertThat(suggested.foods.map { it.id }).containsExactly(dal, rice).inOrder()
        // The photo is released once looked at and is never kept.
        assertThat(taken.isRecycled).isTrue()

        vm.onEvent(DietEvent.PickDish(1))
        val form = vm.until { it.amount != null }.amount!!
        assertThat(form.food.id).isEqualTo(rice)
        assertThat(form.meal).isEqualTo("LUNCH")
        assertThat(form.source).isEqualTo(EntrySource.PHOTO)
        vm.onEvent(DietEvent.ConfirmAmount)

        val after = vm.until { it.amount == null && (it.dishPhoto as? DishPhotoState.Suggested)?.added == 1 }
        assertThat(after.search).isNotNull()
        val logged = vm.until { it.day?.meals?.get("LUNCH")?.isNotEmpty() == true }.day!!.meals.getValue("LUNCH").single().row
        assertThat(logged.foodId).isEqualTo(rice)
        assertThat(logged.entrySource).isEqualTo(EntrySource.PHOTO)

        vm.onEvent(DietEvent.CloseDishPhoto)
        val closed = vm.until { it.dishPhoto == null }
        assertThat(closed.search).isNull()
        assertThat(graph.db.bodyLogDao().feedbackCount(ScanKind.PHOTO)).isEqualTo(1)
        assertThat(graph.db.bodyLogDao().feedbackHits(ScanKind.PHOTO)).isEqualTo(1)
    }

    @Test
    fun aLowScoreIsUnsureAndNoneOfTheseGoesBackToTheSearch() = runBlocking<Unit> {
        twoLabels()
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenSearch("DINNER"))
        vm.onEvent(DietEvent.OpenDishPhoto)

        graph.dishClassifier.guesses = listOf(Guess("dal", 0.1))
        vm.onEvent(DietEvent.DishPhotoTaken(photo()))
        vm.until { it.dishPhoto == DishPhotoState.Unsure }

        vm.onEvent(DietEvent.DishPhotoRetry)
        graph.dishClassifier.guesses = listOf(Guess("dal", 0.9))
        vm.onEvent(DietEvent.DishPhotoTaken(photo()))
        vm.until { it.dishPhoto is DishPhotoState.Suggested }
        vm.onEvent(DietEvent.NoneOfThese)
        val back = vm.until { it.dishPhoto == null }
        assertThat(back.search?.meal).isEqualTo("DINNER")
        // One unsure, one "none of these": two scans, no hit.
        assertThat(graph.db.bodyLogDao().feedbackCount(ScanKind.PHOTO)).isEqualTo(2)
        assertThat(graph.db.bodyLogDao().feedbackHits(ScanKind.PHOTO)).isEqualTo(0)
    }

    @Test
    fun aModelThatCannotRunOrACameraThatTookNoPhotoFails() = runBlocking<Unit> {
        twoLabels()
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenDishPhoto)
        graph.dishClassifier.guesses = null
        vm.onEvent(DietEvent.DishPhotoTaken(photo()))
        vm.until { it.dishPhoto == DishPhotoState.Failed }
        vm.onEvent(DietEvent.DishPhotoRetry)
        vm.onEvent(DietEvent.DishPhotoTaken(null))
        vm.until { it.dishPhoto == DishPhotoState.Failed }
    }
}
