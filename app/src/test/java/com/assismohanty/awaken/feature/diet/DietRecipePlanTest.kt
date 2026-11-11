package com.assismohanty.awaken.feature.diet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import com.assismohanty.awaken.testing.FakePhoneStepsGateway
import com.assismohanty.awaken.testing.NoDietReminders
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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

/** Recipes and the meal plan through the Diet ViewModel: build, scale and save a recipe; plan a food and log it (GD 20.4). */
@RunWith(AndroidJUnit4::class)
class DietRecipePlanTest {
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

    private fun viewModel() = DietViewModel(graph.diet, graph.foodSearch, graph.foodLibrary, graph.bodyLog, graph.uiPrefs, NoDietReminders, graph.recipeBook, LoadHealthOverviewUseCase(graph.db.hunterDao(), graph.health, FakePhoneStepsGateway(), graph.clock))

    private suspend fun DietViewModel.until(condition: (DietUiState) -> Boolean): DietUiState = withTimeout(TIMEOUT_MS) { uiState.first(condition) }

    @Test
    fun aRecipeIsBuiltFromAnIngredientScaledAndSaved() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        val paneer = graph.diet.search("paneer").first { it.name == "Cheese, paneer" }

        vm.onEvent(DietEvent.NewRecipe)
        vm.onEvent(DietEvent.RecipeChanged { it.copy(name = "Paneer dish", servings = 2.0) })
        vm.onEvent(DietEvent.AddIngredient)
        vm.until { it.search?.mode == SearchMode.RECIPE }
        vm.onEvent(DietEvent.PickFood(paneer))
        vm.until { it.amount != null }
        vm.onEvent(DietEvent.ConfirmAmount)
        val built = vm.until { it.recipeForm?.ingredients?.size == 1 }.recipeForm!!
        assertThat(built.ingredients.single().grams).isEqualTo(100.0)
        assertThat(vm.uiState.value.search).isNull()

        val before = built.perServing!!
        vm.onEvent(DietEvent.ScaleRecipe(4.0))
        val scaled = vm.until { it.recipeForm?.servings == 4.0 }.recipeForm!!
        assertThat(scaled.ingredients.single().grams).isWithin(1e-9).of(200.0)
        assertThat(scaled.perServing!!.values.keys).isEqualTo(before.values.keys)
        assertThat(scaled.perServing!!.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.KCAL))
            .isWithin(1e-9).of(before.values.getValue(com.assismohanty.awaken.engine.body.Nutrient.KCAL))

        vm.onEvent(DietEvent.SaveRecipe)
        val saved = vm.until { it.recipes.isNotEmpty() && it.recipeForm == null }
        assertThat(saved.recipes.single().name).isEqualTo("Paneer dish")
        assertThat(graph.recipeBook.detail(saved.recipes.single().id)?.recipe?.servings).isEqualTo(4.0)
    }

    @Test
    fun aRecipeWithoutAnIngredientIsRefusedWithAReason() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.NewRecipe)
        vm.onEvent(DietEvent.RecipeChanged { it.copy(name = "Empty") })
        vm.onEvent(DietEvent.SaveRecipe)

        val state = vm.until { it.recipeForm?.error != null }
        assertThat(state.recipeForm?.error).isEqualTo(com.assismohanty.awaken.core.domain.RecipeSaveResult.NeedsIngredients)
        assertThat(state.recipes).isEmpty()
    }

    @Test
    fun aPlannedFoodIsLoggedIntoTheDiaryAndMarkedDone() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        val idli = graph.diet.search("idli").first()

        vm.onEvent(DietEvent.OpenPanel(Panel.PLAN))
        vm.until { it.planDate == today }
        vm.onEvent(DietEvent.AddToPlan)
        vm.until { it.search?.mode == SearchMode.PLAN }
        vm.onEvent(DietEvent.PickFood(idli))
        vm.until { it.amount != null }
        vm.onEvent(DietEvent.ConfirmAmount)
        val planned = vm.until { it.planned.size == 1 }.planned.single()
        assertThat(planned.logged).isFalse()
        assertThat(graph.diet.observeDay(today).first().meals).isEmpty()

        vm.onEvent(DietEvent.LogPlanned(planned.id, today))
        vm.until { it.planned.singleOrNull()?.logged == true }
        assertThat(graph.diet.observeDay(today).first().meals.values.flatten()).hasSize(1)
    }

    @Test
    fun aPlannedAmountCanBeChangedUntilItIsLogged() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        val idli = graph.diet.search("idli").first()
        vm.onEvent(DietEvent.OpenPanel(Panel.PLAN))
        vm.until { it.planDate == today }
        vm.onEvent(DietEvent.AddToPlan)
        vm.until { it.search?.mode == SearchMode.PLAN }
        vm.onEvent(DietEvent.PickFood(idli))
        vm.until { it.amount != null }
        vm.onEvent(DietEvent.ConfirmAmount)
        val planned = vm.until { it.planned.size == 1 }.planned.single()

        vm.onEvent(DietEvent.EditPlanned(planned))
        assertThat(vm.until { it.amount?.editingPlanId == planned.id }.amount?.grams).isEqualTo(planned.grams)
        vm.onEvent(DietEvent.AmountChanged { it.copy(portion = null, grams = 250.0) })
        vm.onEvent(DietEvent.ConfirmAmount)
        val changed = vm.until { it.planned.singleOrNull()?.grams == 250.0 }.planned.single()
        assertThat(changed.id).isEqualTo(planned.id)

        vm.onEvent(DietEvent.LogPlanned(planned.id, today))
        vm.until { it.planned.singleOrNull()?.logged == true }
        assertThat(graph.bodyLog.updatePlanned(planned.id, "LUNCH", 10.0, null)).isFalse()
        assertThat(vm.uiState.value.planned.single().grams).isEqualTo(250.0)
    }

    @Test
    fun thePlanStopsFourteenDaysAhead() = runBlocking<Unit> {
        val vm = viewModel()
        vm.until { !it.loading }
        vm.onEvent(DietEvent.OpenPanel(Panel.PLAN))
        vm.until { it.planDate == today }

        vm.onEvent(DietEvent.PlanDay(today.plusDays(40)))
        assertThat(vm.until { it.planDate != today }.planDate).isEqualTo(today.plusDays(14))
        vm.onEvent(DietEvent.PlanDay(today.minusDays(3)))
        assertThat(vm.until { it.planDate == today }.planDate).isEqualTo(today)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
