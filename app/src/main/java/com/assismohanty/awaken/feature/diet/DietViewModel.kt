package com.assismohanty.awaken.feature.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.domain.BodyLog
import com.assismohanty.awaken.core.domain.CustomFoodDraft
import com.assismohanty.awaken.core.domain.CustomFoodResult
import com.assismohanty.awaken.core.domain.MealSlot
import com.assismohanty.awaken.core.domain.SavedMealSummary
import java.util.UUID
import com.assismohanty.awaken.core.domain.DiaryDay
import com.assismohanty.awaken.core.domain.DietPlan
import com.assismohanty.awaken.core.domain.DietSetup
import com.assismohanty.awaken.core.domain.DietTracker
import com.assismohanty.awaken.core.domain.FoodLibrary
import com.assismohanty.awaken.core.domain.FoodSearchUseCase
import kotlinx.coroutines.flow.first
import com.assismohanty.awaken.engine.body.ActivityLevel
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.NutritionMath
import com.assismohanty.awaken.engine.body.NutritionPlan
import com.assismohanty.awaken.engine.body.Nutrients
import com.assismohanty.awaken.engine.body.Sex
import com.assismohanty.awaken.engine.body.WeightGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The setup form. Fields start empty so the numbers are always the hunter's own. */
data class SetupForm(
    val goal: WeightGoal? = null,
    val sex: Sex? = null,
    val birthYear: Int = 0,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val activity: ActivityLevel? = null,
    /** Engine plan for the form as it stands, once it is complete. */
    val preview: NutritionPlan? = null,
    val editing: Boolean = false,
) {
    fun toSetup(today: LocalDate): DietSetup? {
        val age = NutritionMath.ageOn(birthYear, today)
        if (goal == null || sex == null || activity == null) return null
        if (age !in NutritionMath.AGE_RANGE || heightCm !in NutritionMath.HEIGHT_CM_RANGE || weightKg !in NutritionMath.WEIGHT_KG_RANGE) return null
        return DietSetup(sex, birthYear, heightCm, weightKg, activity, goal)
    }
}

/** What a blank search shows: what was logged lately, or the starred foods. */
enum class SearchTab { RECENT, FAVOURITES, MEALS }

/** A small input sheet over the diary. */
sealed interface Prompt {
    data object NewMeal : Prompt
    data class SaveMeal(val slotId: String) : Prompt
    data object Note : Prompt
    data class QuickAdd(val slotId: String) : Prompt
}

data class FoodSearch(
    /** A meal slot id. */
    val meal: String,
    val query: String = "",
    val tab: SearchTab = SearchTab.RECENT,
    val results: List<Food> = emptyList(),
    val showCustomForm: Boolean = false,
    /** Why the last custom food was refused. */
    val customError: CustomFoodResult.Reason? = null,
)

/** How much of a food: grams, or a count of one of its household portions. */
data class AmountForm(
    val food: Food,
    val meal: String,
    val portions: List<Portion>,
    val portion: Portion? = null,
    val count: Double = 1.0,
    val grams: Double = DEFAULT_GRAMS,
    val favourite: Boolean = false,
    /** Set when editing an existing diary row. */
    val editingRowId: String? = null,
) {
    val totalGrams: Double get() = portion?.let { it.grams * count } ?: grams
    val amounts: Nutrients get() = food.per100.forGrams(totalGrams)
    val portionLabel: String? get() = portion?.let { p -> if (count == 1.0) p.label else "${formatCount(count)} × ${p.label}" }
    /** The food's calorie spread scaled to this amount, when it has one. */
    val kcalRange: ClosedFloatingPointRange<Double>? get() = food.kcalRange?.let { it.start * totalGrams / 100..it.endInclusive * totalGrams / 100 }
    val valid: Boolean get() = totalGrams in DietTracker.MIN_GRAMS..DietTracker.MAX_GRAMS

    companion object {
        const val DEFAULT_GRAMS = 100.0

        private fun formatCount(count: Double) = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString()
    }
}

data class DietFlow(
    val date: LocalDate? = null,
    val today: LocalDate? = null,
    val setup: SetupForm? = null,
    val search: FoodSearch? = null,
    val amount: AmountForm? = null,
    val targets: Boolean = false,
    val prompt: Prompt? = null,
)

data class DietUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.MIN,
    val isToday: Boolean = true,
    val plan: DietPlan? = null,
    val day: DiaryDay? = null,
    val slots: List<MealSlot> = emptyList(),
    val savedMeals: List<SavedMealSummary> = emptyList(),
    val note: String? = null,
    val prompt: Prompt? = null,
    val setup: SetupForm? = null,
    val search: FoodSearch? = null,
    val amount: AmountForm? = null,
    val targets: Boolean = false,
)

sealed interface DietEvent {
    data object PreviousDay : DietEvent
    data object NextDay : DietEvent
    data object Today : DietEvent

    data object EditProfile : DietEvent
    data object CancelProfile : DietEvent
    /** Applied to the form as it is now, so quick successive edits never overwrite each other. */
    data class SetupChanged(val change: (SetupForm) -> SetupForm) : DietEvent
    data object SaveProfile : DietEvent

    data class OpenSearch(val meal: String) : DietEvent
    data object CloseSearch : DietEvent
    data class Query(val text: String) : DietEvent
    data class PickTab(val tab: SearchTab) : DietEvent
    data class PickFood(val food: Food) : DietEvent
    data object OpenCustomFood : DietEvent
    data object CloseCustomFood : DietEvent
    data class CreateCustomFood(val draft: CustomFoodDraft) : DietEvent

    data class OpenPrompt(val prompt: Prompt) : DietEvent
    data object ClosePrompt : DietEvent
    data class AddMeal(val name: String) : DietEvent
    data class DeleteMeal(val id: String) : DietEvent
    data class SaveMeal(val slotId: String, val name: String) : DietEvent
    data class SaveNote(val text: String) : DietEvent
    data class QuickAdd(val slotId: String, val name: String, val nutrients: Nutrients) : DietEvent
    data class AddSavedMeal(val id: String) : DietEvent
    data class DeleteSavedMeal(val id: String) : DietEvent

    /** Copies a meal (or, with a null id, the whole viewed day) to today. */
    data class CopyToToday(val slotId: String?) : DietEvent

    data class EditEntry(val row: FoodLogEntity) : DietEvent
    data class AmountChanged(val change: (AmountForm) -> AmountForm) : DietEvent
    data object CloseAmount : DietEvent
    data object ToggleFavourite : DietEvent
    data object ConfirmAmount : DietEvent
    data object DeleteEntry : DietEvent

    data object OpenTargets : DietEvent
    data object CloseTargets : DietEvent
    data class SaveTargets(val overrides: Map<Nutrient, Double>) : DietEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietViewModel @Inject constructor(
    private val diet: DietTracker,
    private val foodSearch: FoodSearchUseCase,
    private val foodLibrary: FoodLibrary,
    private val bodyLog: BodyLog,
) : ViewModel() {

    private val flow = MutableStateFlow(DietFlow())

    val uiState: StateFlow<DietUiState> = combine(
        diet.observePlan(),
        combine(foodLibrary.observeMealSlots(), foodLibrary.observeSavedMeals()) { slots, saved -> slots to saved },
        flow.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
            date?.let { d -> combine(diet.observeDay(d), bodyLog.observeNote(d)) { day, note -> day to note?.text } } ?: flowOf(null)
        },
        flow,
    ) { plan, library, dayAndNote, f ->
        val (slots, saved) = library
        DietUiState(
            loading = f.date == null,
            date = f.date ?: LocalDate.MIN,
            isToday = f.date == f.today,
            plan = plan,
            day = dayAndNote?.first,
            slots = slots,
            savedMeals = saved,
            note = dayAndNote?.second,
            prompt = f.prompt,
            setup = f.setup ?: if (plan == null) SetupForm() else null,
            search = f.search,
            amount = f.amount,
            targets = f.targets,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DietUiState())

    init {
        viewModelScope.launch {
            val today = diet.today()
            flow.update { it.copy(date = today, today = today) }
        }
    }

    fun onEvent(event: DietEvent) {
        when (event) {
            DietEvent.PreviousDay -> flow.update { f -> f.copy(date = f.date?.minusDays(1)) }
            DietEvent.NextDay -> flow.update { f -> f.copy(date = f.date?.plusDays(1)?.takeIf { d -> f.today == null || !d.isAfter(f.today) } ?: f.date) }
            DietEvent.Today -> flow.update { f -> f.copy(date = f.today) }

            DietEvent.EditProfile -> {
                val setup = uiState.value.plan?.setup ?: return
                val form = SetupForm(setup.goal, setup.sex, setup.birthYear, setup.heightCm, setup.weightKg, setup.activity, editing = true)
                flow.update { it.copy(setup = withPreview(form), targets = false) }
            }
            DietEvent.CancelProfile -> flow.update { it.copy(setup = null) }
            is DietEvent.SetupChanged -> flow.update { f -> f.copy(setup = withPreview(event.change(f.setup ?: SetupForm()))) }
            DietEvent.SaveProfile -> viewModelScope.launch {
                val setup = flow.value.setup?.toSetup(today()) ?: return@launch
                diet.saveSetup(setup)
                flow.update { it.copy(setup = null) }
            }

            is DietEvent.OpenSearch -> {
                flow.update { it.copy(search = FoodSearch(event.meal)) }
                refreshSearch()
            }
            DietEvent.CloseSearch -> flow.update { it.copy(search = null) }
            is DietEvent.Query -> {
                flow.update { f -> f.copy(search = f.search?.copy(query = event.text)) }
                refreshSearch()
            }
            is DietEvent.PickTab -> {
                flow.update { f -> f.copy(search = f.search?.copy(tab = event.tab)) }
                refreshSearch()
            }
            is DietEvent.PickFood -> viewModelScope.launch {
                val meal = flow.value.search?.meal ?: FALLBACK_MEAL
                val portions = event.food.portions.ifEmpty { diet.portionsOf(event.food.id) }
                val favourite = event.food.id in foodLibrary.observeFavouriteIds().first()
                flow.update { it.copy(amount = AmountForm(event.food, meal, portions, favourite = favourite)) }
            }
            DietEvent.OpenCustomFood -> flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = true)) }
            DietEvent.CloseCustomFood -> flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = false)) }
            is DietEvent.CreateCustomFood -> viewModelScope.launch {
                when (val result = foodLibrary.saveCustomFood(event.draft)) {
                    is CustomFoodResult.Invalid -> flow.update { f -> f.copy(search = f.search?.copy(customError = result.reason)) }
                    is CustomFoodResult.Saved -> {
                        val meal = flow.value.search?.meal ?: FALLBACK_MEAL
                        flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = false, customError = null), amount = AmountForm(result.food, meal, result.food.portions)) }
                    }
                }
            }

            is DietEvent.OpenPrompt -> flow.update { it.copy(prompt = event.prompt) }
            DietEvent.ClosePrompt -> flow.update { it.copy(prompt = null) }
            is DietEvent.AddMeal -> viewModelScope.launch {
                foodLibrary.addMeal(event.name)
                flow.update { it.copy(prompt = null) }
            }
            is DietEvent.DeleteMeal -> viewModelScope.launch { foodLibrary.deleteMeal(event.id) }
            is DietEvent.SaveMeal -> viewModelScope.launch {
                val date = flow.value.date ?: return@launch
                foodLibrary.saveMeal(event.name, diet.entriesOn(date, event.slotId))
                flow.update { it.copy(prompt = null) }
            }
            is DietEvent.SaveNote -> viewModelScope.launch {
                flow.value.date?.let { bodyLog.setNote(it, event.text) }
                flow.update { it.copy(prompt = null) }
            }
            is DietEvent.QuickAdd -> viewModelScope.launch {
                val food = Food("quick:${UUID.randomUUID()}", event.name.trim().ifEmpty { QUICK_NAME }, FoodSource.CUSTOM, event.nutrients)
                diet.log(flow.value.date ?: diet.today(), event.slotId, food, QUICK_GRAMS, null, EntrySource.QUICK)
                flow.update { it.copy(prompt = null, search = null) }
            }
            is DietEvent.AddSavedMeal -> viewModelScope.launch {
                val meal = flow.value.search?.meal ?: FALLBACK_MEAL
                foodLibrary.addSavedMealToDiary(event.id, flow.value.date ?: diet.today(), meal)
                flow.update { it.copy(search = null) }
            }
            is DietEvent.DeleteSavedMeal -> viewModelScope.launch { foodLibrary.deleteSavedMeal(event.id) }
            is DietEvent.CopyToToday -> viewModelScope.launch {
                val from = flow.value.date ?: return@launch
                foodLibrary.copyEntries(from, event.slotId, diet.today(), event.slotId)
            }

            is DietEvent.EditEntry -> viewModelScope.launch {
                val row = event.row
                val portions = diet.portionsOf(row.foodId)
                val portion = portions.firstOrNull { row.portionLabel?.endsWith(it.label) == true }
                val form = AmountForm(
                    food = diet.foodOf(row),
                    meal = row.meal,
                    portions = portions,
                    portion = portion,
                    count = portion?.let { row.grams / it.grams } ?: 1.0,
                    grams = row.grams,
                    editingRowId = row.id,
                    favourite = row.foodId in foodLibrary.observeFavouriteIds().first(),
                )
                flow.update { it.copy(amount = form) }
            }
            is DietEvent.AmountChanged -> flow.update { f -> f.copy(amount = f.amount?.let(event.change)) }
            DietEvent.ToggleFavourite -> viewModelScope.launch {
                val form = flow.value.amount ?: return@launch
                val now = foodLibrary.toggleFavourite(form.food.id)
                flow.update { f -> f.copy(amount = f.amount?.copy(favourite = now)) }
            }
            DietEvent.CloseAmount -> flow.update { it.copy(amount = null) }
            DietEvent.ConfirmAmount -> viewModelScope.launch {
                val form = flow.value.amount ?: return@launch
                if (!form.valid) return@launch
                val editing = form.editingRowId
                if (editing != null) {
                    diet.updateLog(editing, form.totalGrams, form.meal, form.portionLabel)
                } else {
                    diet.log(flow.value.date ?: diet.today(), form.meal, form.food, form.totalGrams, form.portionLabel)
                }
                flow.update { it.copy(amount = null, search = null) }
            }
            DietEvent.DeleteEntry -> viewModelScope.launch {
                flow.value.amount?.editingRowId?.let { diet.deleteLog(it) }
                flow.update { it.copy(amount = null) }
            }

            DietEvent.OpenTargets -> flow.update { it.copy(targets = true) }
            DietEvent.CloseTargets -> flow.update { it.copy(targets = false) }
            is DietEvent.SaveTargets -> viewModelScope.launch {
                diet.saveOverrides(event.overrides)
                flow.update { it.copy(targets = false) }
            }
        }
    }

    private fun today(): LocalDate = flow.value.today ?: LocalDate.MIN

    private fun withPreview(form: SetupForm): SetupForm =
        form.copy(preview = form.toSetup(today())?.let { diet.preview(it, today()) })

    private fun refreshSearch() {
        viewModelScope.launch {
            val search = flow.value.search ?: return@launch
            val results = when {
                search.query.isNotBlank() -> foodSearch.search(search.query, today())
                search.tab == SearchTab.FAVOURITES -> foodSearch.favourites()
                else -> foodSearch.recent()
            }
            flow.update { f -> if (f.search?.query == search.query && f.search.tab == search.tab) f.copy(search = f.search.copy(results = results)) else f }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FALLBACK_MEAL = "SNACK"

        /** A quick add keeps its numbers per 100 g and logs exactly 100 g, so the row shows what was typed. */
        const val QUICK_GRAMS = 100.0
        const val QUICK_NAME = "Quick add"
    }
}
