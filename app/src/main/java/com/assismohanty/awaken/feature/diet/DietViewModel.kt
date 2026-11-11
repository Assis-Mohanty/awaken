package com.assismohanty.awaken.feature.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.engine.body.HeightUnit
import com.assismohanty.awaken.engine.body.WeightUnit
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.db.entity.MealPlanEntryEntity
import com.assismohanty.awaken.core.data.db.entity.RecipeEntity
import com.assismohanty.awaken.core.domain.BodyLog
import com.assismohanty.awaken.core.data.food.OpenFoodFactsClient
import com.assismohanty.awaken.core.domain.BarcodeResult
import com.assismohanty.awaken.core.domain.FoodOrigin
import com.assismohanty.awaken.core.domain.OnlineStatus
import com.assismohanty.awaken.core.domain.HealthOverviewResult
import com.assismohanty.awaken.core.domain.LookUpBarcodeUseCase
import com.assismohanty.awaken.core.scan.ScannedCode
import com.assismohanty.awaken.core.domain.IngredientDraft
import com.assismohanty.awaken.core.domain.LoadHealthOverviewUseCase
import kotlin.math.roundToInt
import com.assismohanty.awaken.core.domain.RecipeBook
import com.assismohanty.awaken.core.domain.RecipeDraft
import com.assismohanty.awaken.core.domain.RecipeSaveResult
import com.assismohanty.awaken.engine.body.Recipe
import com.assismohanty.awaken.engine.body.RecipeIngredient
import com.assismohanty.awaken.engine.body.RecipeMath
import com.assismohanty.awaken.core.domain.DietReminderScheduling
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
import com.assismohanty.awaken.engine.body.GoalPlan
import com.assismohanty.awaken.engine.body.GoalSettings
import com.assismohanty.awaken.engine.body.Hydration
import com.assismohanty.awaken.engine.body.LossPace
import com.assismohanty.awaken.engine.body.MacroPreset
import com.assismohanty.awaken.engine.body.MacroSplit
import com.assismohanty.awaken.core.data.db.entity.MeasureKind
import com.assismohanty.awaken.core.data.db.entity.WeightLogEntity
import com.assismohanty.awaken.core.domain.TrendOffer
import com.assismohanty.awaken.core.data.db.entity.FastingSessionEntity
import com.assismohanty.awaken.engine.body.DietReport
import com.assismohanty.awaken.engine.body.FastState
import com.assismohanty.awaken.engine.body.FastingRules
import com.assismohanty.awaken.engine.body.NutrientCoverage
import com.assismohanty.awaken.engine.body.WeekReport
import com.assismohanty.awaken.engine.body.WeighIn
import com.assismohanty.awaken.engine.body.WeightTrend
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    /** 0 means the preset decides. */
    val proteinPerKg: Double = 0.0,
    /** 1 about you, 2 goal and pace, 3 your numbers. */
    val step: Int = 1,
    val lossPace: LossPace = LossPace.STEADY,
    val preset: MacroPreset = MacroPreset.BALANCED,
    val proteinPct: Int = 0,
    val carbsPct: Int = 0,
    val fatPct: Int = 0,
    /** Engine plan for the form as it stands, once it is complete. */
    val preview: GoalPlan? = null,
    val editing: Boolean = false,
) {
    /** Step 1 is complete: the answers the maintenance number needs. */
    fun aboutYouDone(today: LocalDate): Boolean {
        val age = NutritionMath.ageOn(birthYear, today)
        return sex != null && activity != null && age in NutritionMath.AGE_RANGE && heightCm in NutritionMath.HEIGHT_CM_RANGE && weightKg in NutritionMath.WEIGHT_KG_RANGE
    }

    /** A custom split only counts when the three percentages add to 100. */
    val customSplit: MacroSplit? get() = MacroSplit.of(proteinPct, carbsPct, fatPct)

    fun toSetup(today: LocalDate): DietSetup? {
        val age = NutritionMath.ageOn(birthYear, today)
        if (goal == null || sex == null || activity == null) return null
        if (age !in NutritionMath.AGE_RANGE || heightCm !in NutritionMath.HEIGHT_CM_RANGE || weightKg !in NutritionMath.WEIGHT_KG_RANGE) return null
        if (preset == MacroPreset.CUSTOM && customSplit == null) return null
        val settings = GoalSettings(lossPace = lossPace, preset = preset, proteinPerKg = proteinPerKg.takeIf { it > 0.0 }, split = customSplit.takeIf { preset == MacroPreset.CUSTOM })
        return DietSetup(sex, birthYear, heightCm, weightKg, activity, goal, settings)
    }
}

/** What a blank search shows: what was logged lately, or the starred foods. */
enum class SearchTab { RECENT, FAVOURITES, MEALS, RECIPES }

/** What a food search is for: the diary, an ingredient of the recipe being built, or the meal plan. */
enum class SearchMode { DIARY, RECIPE, PLAN }

/** Where a barcode scan stands. [Looking] while a code is being looked up, so a second read of it is ignored. */
sealed interface ScanState {
    data object Scanning : ScanState
    data object Looking : ScanState
    data object Invalid : ScanState
    data class Found(val food: Food, val origin: FoodOrigin) : ScanState
    data class NotFound(val barcode: String, val online: OnlineStatus) : ScanState
}

/** A recipe being built. Numbers come from `:engine` [RecipeMath]; nothing is computed here. */
data class RecipeForm(
    val id: String? = null,
    val name: String = "",
    val servings: Double = 1.0,
    /** 0 means not set: the raw total is used. */
    val cookedGrams: Double = 0.0,
    val ingredients: List<IngredientDraft> = emptyList(),
    val error: RecipeSaveResult? = null,
) {
    private val recipe: Recipe?
        get() = if (ingredients.isEmpty() || servings <= 0.0) null else Recipe(ingredients.map { RecipeIngredient(it.per100, it.grams) }, servings, cookedGrams.takeIf { it > 0.0 })

    /** One serving's nutrients, or null until there is an ingredient and a serving count. */
    val perServing: Nutrients? get() = recipe?.let(RecipeMath::perServing)

    /** The same dish for [newServings]: every ingredient and the cooked weight scale, one serving stays the same. */
    fun scaledTo(newServings: Double): RecipeForm {
        val current = recipe ?: return this
        if (newServings <= 0.0) return this
        val scaled = current.scaled(newServings / servings)
        return copy(servings = newServings, cookedGrams = scaled.cookedGrams ?: 0.0, ingredients = ingredients.zip(scaled.ingredients) { draft, ingredient -> draft.copy(grams = ingredient.grams) })
    }
}

/** A small input sheet over the diary. */
sealed interface Prompt {
    data object NewMeal : Prompt
    data class SaveMeal(val slotId: String) : Prompt
    data object Note : Prompt
    data class QuickAdd(val slotId: String) : Prompt
    data class RenameMeal(val slotId: String, val current: String) : Prompt
    data object Water : Prompt
}

data class FoodSearch(
    /** A meal slot id. */
    val meal: String,
    val mode: SearchMode = SearchMode.DIARY,
    val query: String = "",
    val tab: SearchTab = SearchTab.RECENT,
    val results: List<Food> = emptyList(),
    val showCustomForm: Boolean = false,
    /** Why the last custom food was refused. */
    val customError: CustomFoodResult.Reason? = null,
    /** A scanned code the form starts with ("add it from the label"). */
    val prefillBarcode: String? = null,
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
    val mode: SearchMode = SearchMode.DIARY,
    val source: EntrySource = EntrySource.SEARCH,
    /** Set when editing an existing diary row. */
    val editingRowId: String? = null,
    /** Set when changing a planned food. */
    val editingPlanId: String? = null,
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
    val panel: Panel? = null,
    val report: WeekReport? = null,
    val recipeForm: RecipeForm? = null,
    /** The day the meal plan shows. */
    val planDate: LocalDate? = null,
    val activeKcal: Int? = null,
    val scan: ScanState? = null,
)

/** The panels that open over the diary. */
enum class Panel { WEIGHT, NUTRIENTS, REPORT, FASTING, WATER, RECIPES, PLAN }

/** A fast as the sheet shows it: the stored session and where it stands now. */
data class FastView(val session: FastingSessionEntity? = null, val state: FastState = FastState.Idle)

/** What the trackers around the diary show for the viewed day. */
data class Trackers(
    val waterMl: Int = 0,
    val weights: List<WeightLogEntity> = emptyList(),
    val offer: TrendOffer? = null,
    val trendKg: Double? = null,
    val fast: FastView = FastView(),
    val planned: List<MealPlanEntryEntity> = emptyList(),
)

/** What the library flows hand the screen state together. */
private data class Library(val slots: List<MealSlot>, val saved: List<SavedMealSummary>, val units: Pair<WeightUnit, HeightUnit>, val recipes: List<RecipeEntity>)

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
    val offer: TrendOffer? = null,
    val waterMl: Int = 0,
    val weights: List<WeightLogEntity> = emptyList(),
    val panel: Panel? = null,
    val report: WeekReport? = null,
    val trendKg: Double? = null,
    val fast: FastView = FastView(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val recipes: List<RecipeEntity> = emptyList(),
    val recipeForm: RecipeForm? = null,
    val planDate: LocalDate? = null,
    val planned: List<MealPlanEntryEntity> = emptyList(),
    val today: LocalDate? = null,
    /** Today's active calories from Health Connect, or null when it is off or has none. Display only. */
    val activeKcal: Int? = null,
    val scan: ScanState? = null,
    /** Share of the day's calories from foods with a value, per nutrient (null when nothing is logged). */
    val coverage: Map<Nutrient, Double?> = emptyMap(),
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
    data object SetupNext : DietEvent
    data object SetupBack : DietEvent

    data class AddWater(val ml: Int) : DietEvent
    data object AcceptTrend : DietEvent
    data object DismissTrend : DietEvent
    data class OpenPanel(val panel: Panel) : DietEvent
    data object ClosePanel : DietEvent
    data class StartFast(val targetMinutes: Int) : DietEvent
    data object EndFast : DietEvent
    data class SetWaterTarget(val ml: Int?) : DietEvent
    data object UndoWater : DietEvent

    data object NewRecipe : DietEvent
    data class EditRecipe(val id: String) : DietEvent
    data class RecipeChanged(val change: (RecipeForm) -> RecipeForm) : DietEvent
    data object AddIngredient : DietEvent
    data class RemoveIngredient(val index: Int) : DietEvent
    data class ScaleRecipe(val servings: Double) : DietEvent
    data object SaveRecipe : DietEvent
    data class DeleteRecipe(val id: String) : DietEvent
    data object CloseRecipe : DietEvent

    data class PlanDay(val date: LocalDate) : DietEvent
    data object AddToPlan : DietEvent

    data object OpenScanner : DietEvent
    data object CloseScanner : DietEvent
    data class BarcodeRead(val code: ScannedCode) : DietEvent
    data object ScanAgain : DietEvent
    data object ScanAccept : DietEvent
    data object ScanCreate : DietEvent
    data object ScanAllowOnline : DietEvent
    data object ScanDeclineOnline : DietEvent
    data class EditPlanned(val entry: MealPlanEntryEntity) : DietEvent
    data class LogPlanned(val id: String, val date: LocalDate) : DietEvent
    data class RemovePlanned(val id: String) : DietEvent
    data class LogWeight(val kg: Double) : DietEvent
    data class DeleteWeight(val id: String) : DietEvent
    data class LogMeasure(val kind: MeasureKind, val cm: Double) : DietEvent

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
    data class RenameMeal(val slotId: String, val name: String) : DietEvent

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
    private val uiPrefs: UiPrefsRepository,
    private val reminders: DietReminderScheduling,
    private val recipeBook: RecipeBook,
    private val loadHealth: LoadHealthOverviewUseCase,
    private val lookUpBarcode: LookUpBarcodeUseCase,
) : ViewModel() {

    private val flow = MutableStateFlow(DietFlow())

    val uiState: StateFlow<DietUiState> = combine(
        diet.observePlan(),
        combine(foodLibrary.observeMealSlots(), foodLibrary.observeSavedMeals(), uiPrefs.prefs.map { it.weightUnit to it.heightUnit }.distinctUntilChanged(), recipeBook.observeRecipes()) { slots, saved, unit, recipes -> Library(slots, saved, unit, recipes) },
        flow.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
            date?.let { d -> combine(diet.observeDay(d), bodyLog.observeNote(d)) { day, note -> day to note?.text } } ?: flowOf(null)
        },
        flow.map { it.date to it.planDate }.distinctUntilChanged().flatMapLatest { (date, planDate) ->
            if (date == null) {
                flowOf(Trackers())
            } else {
                combine(
                    bodyLog.observeWaterMl(date), bodyLog.observeWeights(date.minusDays(WEIGHT_DAYS)), diet.observePlan(), fastView(),
                    planDate?.let(bodyLog::observePlan) ?: flowOf(emptyList()),
                ) { water, weights, _, fast, planned ->
                    val today = flow.value.today ?: date
                    Trackers(
                        water, weights.sortedByDescending { it.loggedAt }, bodyLog.trendOffer(today),
                        WeightTrend.trend(weights.map { WeighIn(it.gameDate, it.weightKg) }, today), fast, planned,
                    )
                }
            }
        },
        flow,
    ) { plan, library, dayAndNote, trackers, f ->
        val (slots, saved, units, recipes) = library
        DietUiState(
            loading = f.date == null,
            date = f.date ?: LocalDate.MIN,
            isToday = f.date == f.today,
            plan = plan,
            day = dayAndNote?.first,
            slots = slots,
            savedMeals = saved,
            weightUnit = units.first,
            heightUnit = units.second,
            recipes = recipes,
            recipeForm = f.recipeForm,
            planDate = f.planDate,
            planned = trackers.planned,
            today = f.today,
            activeKcal = f.activeKcal,
            scan = f.scan,
            note = dayAndNote?.second,
            prompt = f.prompt,
            offer = trackers.offer,
            waterMl = trackers.waterMl,
            weights = trackers.weights,
            panel = f.panel,
            report = f.report,
            trendKg = trackers.trendKg,
            fast = trackers.fast,
            coverage = dayAndNote?.first?.let { coverageOf(it) }.orEmpty(),
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
        viewModelScope.launch {
            val overview = (loadHealth() as? HealthOverviewResult.Ready)?.overview
            val kcal = overview?.takeIf { it.canCalories }?.today?.activeKcal?.roundToInt()
            flow.update { it.copy(activeKcal = kcal) }
        }
    }

    fun onEvent(event: DietEvent) {
        when (event) {
            DietEvent.PreviousDay -> flow.update { f -> f.copy(date = f.date?.minusDays(1)) }
            DietEvent.NextDay -> flow.update { f -> f.copy(date = f.date?.plusDays(1)?.takeIf { d -> f.today == null || !d.isAfter(f.today) } ?: f.date) }
            DietEvent.Today -> flow.update { f -> f.copy(date = f.today) }

            DietEvent.EditProfile -> {
                val setup = uiState.value.plan?.setup ?: return
                val s = setup.settings
                val form = SetupForm(
                    setup.goal, setup.sex, setup.birthYear, setup.heightCm, setup.weightKg, setup.activity,
                    proteinPerKg = s.proteinPerKg ?: 0.0, lossPace = s.lossPace, preset = s.preset, proteinPct = s.split?.proteinPct ?: 0, carbsPct = s.split?.carbsPct ?: 0, fatPct = s.split?.fatPct ?: 0,
                    editing = true,
                )
                flow.update { it.copy(setup = withPreview(form), targets = false) }
            }
            DietEvent.CancelProfile -> flow.update { it.copy(setup = null) }
            DietEvent.SetupNext -> flow.update { f -> f.setup?.let { form -> f.copy(setup = withPreview(form.copy(step = (form.step + 1).coerceAtMost(LAST_STEP)))) } ?: f }
            DietEvent.SetupBack -> flow.update { f -> f.setup?.let { form -> f.copy(setup = withPreview(form.copy(step = (form.step - 1).coerceAtLeast(1)))) } ?: f }

            is DietEvent.AddWater -> viewModelScope.launch {
                bodyLog.addWater(flow.value.today ?: diet.today(), event.ml)
                flow.update { it.copy(prompt = null) }
            }
            DietEvent.AcceptTrend -> viewModelScope.launch { uiState.value.offer?.let { bodyLog.acceptTrend(it) } }
            DietEvent.DismissTrend -> viewModelScope.launch { bodyLog.dismissTrend(flow.value.today ?: diet.today()) }
            is DietEvent.OpenPanel -> {
                flow.update { f -> f.copy(panel = event.panel, planDate = if (event.panel == Panel.PLAN) f.planDate ?: f.today else f.planDate) }
                if (event.panel == Panel.REPORT) viewModelScope.launch {
                    val end = flow.value.today ?: diet.today()
                    val report = DietReport.week(diet.week(end))
                    flow.update { f -> f.copy(report = report) }
                }
            }
            DietEvent.ClosePanel -> flow.update { it.copy(panel = null) }
            is DietEvent.StartFast -> viewModelScope.launch {
                bodyLog.startFast(event.targetMinutes)
                reminders.rearmFast()
            }
            DietEvent.EndFast -> viewModelScope.launch {
                bodyLog.endFast()
                reminders.rearmFast()
            }
            is DietEvent.SetWaterTarget -> viewModelScope.launch {
                bodyLog.setWaterTarget(event.ml)
                flow.update { it.copy(panel = null) }
            }
            DietEvent.UndoWater -> viewModelScope.launch { bodyLog.lastWaterId(flow.value.today ?: diet.today())?.let { bodyLog.removeWater(it) } }
            is DietEvent.LogWeight -> viewModelScope.launch { bodyLog.logWeight(flow.value.today ?: diet.today(), event.kg) }
            is DietEvent.DeleteWeight -> viewModelScope.launch { bodyLog.deleteWeight(event.id) }
            is DietEvent.LogMeasure -> viewModelScope.launch { bodyLog.logMeasure(flow.value.today ?: diet.today(), event.kind, event.cm) }
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
                flow.update { it.copy(amount = AmountForm(event.food, meal, portions, favourite = favourite, mode = flow.value.search?.mode ?: SearchMode.DIARY)) }
            }
            DietEvent.OpenCustomFood -> flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = true)) }
            DietEvent.CloseCustomFood -> flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = false)) }
            is DietEvent.CreateCustomFood -> viewModelScope.launch {
                when (val result = foodLibrary.saveCustomFood(event.draft)) {
                    is CustomFoodResult.Invalid -> flow.update { f -> f.copy(search = f.search?.copy(customError = result.reason)) }
                    is CustomFoodResult.Saved -> {
                        val meal = flow.value.search?.meal ?: FALLBACK_MEAL
                        flow.update { f -> f.copy(search = f.search?.copy(showCustomForm = false, customError = null), amount = AmountForm(result.food, meal, result.food.portions, mode = flow.value.search?.mode ?: SearchMode.DIARY)) }
                    }
                }
            }

            is DietEvent.OpenPrompt -> flow.update { it.copy(prompt = event.prompt) }
            DietEvent.ClosePrompt -> flow.update { it.copy(prompt = null) }
            is DietEvent.AddMeal -> viewModelScope.launch {
                foodLibrary.addMeal(event.name)
                flow.update { it.copy(prompt = null) }
            }
            is DietEvent.RenameMeal -> viewModelScope.launch {
                foodLibrary.renameMeal(event.slotId, event.name)
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
                if (form.mode == SearchMode.RECIPE) {
                    val ingredient = IngredientDraft(form.food.id, form.food.name, form.totalGrams, form.food.per100)
                    flow.update { f -> f.copy(recipeForm = f.recipeForm?.copy(ingredients = f.recipeForm.ingredients + ingredient, error = null), amount = null, search = null) }
                    return@launch
                }
                if (form.mode == SearchMode.PLAN) {
                    val date = flow.value.planDate ?: return@launch
                    val planId = form.editingPlanId
                    if (planId != null) {
                        bodyLog.updatePlanned(planId, form.meal, form.totalGrams, form.portionLabel)
                    } else {
                        bodyLog.plan(flow.value.today ?: diet.today(), date, form.meal, form.food, form.totalGrams, form.portionLabel)
                    }
                    flow.update { it.copy(amount = null, search = null) }
                    return@launch
                }
                if (editing != null) {
                    diet.updateLog(editing, form.totalGrams, form.meal, form.portionLabel)
                } else {
                    diet.log(flow.value.date ?: diet.today(), form.meal, form.food, form.totalGrams, form.portionLabel, form.source)
                }
                flow.update { it.copy(amount = null, search = null) }
            }
            DietEvent.OpenScanner -> flow.update { it.copy(scan = ScanState.Scanning) }
            DietEvent.CloseScanner -> flow.update { it.copy(scan = null) }
            DietEvent.ScanAgain -> flow.update { it.copy(scan = ScanState.Scanning) }
            is DietEvent.BarcodeRead -> {
                // Frames keep arriving while a code is looked up: only the first read of a scan counts.
                var taken = false
                flow.update { f -> if (f.scan == ScanState.Scanning) { taken = true; f.copy(scan = ScanState.Looking) } else f }
                if (taken) viewModelScope.launch {
                    val next = when (val result = lookUpBarcode(event.code)) {
                        is BarcodeResult.Found -> ScanState.Found(result.food, result.origin)
                        is BarcodeResult.NotFound -> ScanState.NotFound(result.barcode, result.online)
                        BarcodeResult.Invalid -> ScanState.Invalid
                    }
                    flow.update { f -> if (f.scan == ScanState.Looking) f.copy(scan = next) else f }
                }
            }
            DietEvent.ScanAllowOnline -> viewModelScope.launch {
                val missed = flow.value.scan as? ScanState.NotFound ?: return@launch
                uiPrefs.setOnlineFoodLookup(true)
                flow.update { it.copy(scan = ScanState.Looking) }
                val next = when (val result = lookUpBarcode.online(missed.barcode)) {
                    is BarcodeResult.Found -> ScanState.Found(result.food, result.origin)
                    is BarcodeResult.NotFound -> ScanState.NotFound(result.barcode, result.online)
                    BarcodeResult.Invalid -> ScanState.Invalid
                }
                flow.update { f -> if (f.scan == ScanState.Looking) f.copy(scan = next) else f }
            }
            DietEvent.ScanDeclineOnline -> viewModelScope.launch {
                uiPrefs.setOnlineFoodLookup(false)
                flow.update { f -> (f.scan as? ScanState.NotFound)?.let { f.copy(scan = it.copy(online = OnlineStatus.DECLINED)) } ?: f }
            }
            DietEvent.ScanAccept -> viewModelScope.launch {
                val scanned = flow.value.scan as? ScanState.Found ?: return@launch
                // A product found online is kept in My foods once accepted, so the next scan finds it with no connection.
                val found = if (scanned.origin == FoodOrigin.ONLINE) savedCopy(scanned) ?: return@launch else scanned
                val search = flow.value.search
                val portions = found.food.portions.ifEmpty { diet.portionsOf(found.food.id) }
                val favourite = found.food.id in foodLibrary.observeFavouriteIds().first()
                val form = AmountForm(
                    food = found.food, meal = search?.meal ?: FALLBACK_MEAL, portions = portions,
                    // The label serving is preselected when there is one, else 100 g.
                    portion = portions.firstOrNull(), favourite = favourite, mode = search?.mode ?: SearchMode.DIARY, source = EntrySource.BARCODE,
                )
                flow.update { it.copy(scan = null, amount = form) }
            }
            is DietEvent.ScanCreate -> flow.update { f ->
                val code = (f.scan as? ScanState.NotFound)?.barcode
                f.copy(scan = null, search = (f.search ?: FoodSearch(FALLBACK_MEAL)).copy(showCustomForm = true, prefillBarcode = code))
            }
            DietEvent.NewRecipe -> flow.update { it.copy(recipeForm = RecipeForm()) }
            is DietEvent.EditRecipe -> viewModelScope.launch {
                val detail = recipeBook.detail(event.id) ?: return@launch
                val form = RecipeForm(detail.id, detail.name, detail.recipe.servings, detail.recipe.cookedGrams ?: 0.0, detail.ingredients)
                flow.update { it.copy(recipeForm = form) }
            }
            is DietEvent.RecipeChanged -> flow.update { f -> f.copy(recipeForm = f.recipeForm?.let(event.change)) }
            DietEvent.AddIngredient -> {
                flow.update { it.copy(search = FoodSearch(FALLBACK_MEAL, SearchMode.RECIPE)) }
                refreshSearch()
            }
            is DietEvent.RemoveIngredient -> flow.update { f -> f.copy(recipeForm = f.recipeForm?.let { r -> r.copy(ingredients = r.ingredients.filterIndexed { i, _ -> i != event.index }) }) }
            is DietEvent.ScaleRecipe -> flow.update { f -> f.copy(recipeForm = f.recipeForm?.scaledTo(event.servings)) }
            DietEvent.SaveRecipe -> viewModelScope.launch {
                val form = flow.value.recipeForm ?: return@launch
                val result = recipeBook.save(RecipeDraft(form.id, form.name, form.servings, form.cookedGrams.takeIf { it > 0.0 }, null, form.ingredients))
                flow.update { f -> f.copy(recipeForm = if (result is RecipeSaveResult.Saved) null else f.recipeForm?.copy(error = result)) }
            }
            is DietEvent.DeleteRecipe -> viewModelScope.launch {
                recipeBook.delete(event.id)
                flow.update { it.copy(recipeForm = null) }
            }
            DietEvent.CloseRecipe -> flow.update { it.copy(recipeForm = null) }

            is DietEvent.PlanDay -> flow.update { f ->
                val today = f.today ?: event.date
                f.copy(planDate = event.date.coerceIn(today, today.plusDays(BodyLog.MAX_PLAN_DAYS)))
            }
            DietEvent.AddToPlan -> {
                flow.update { it.copy(search = FoodSearch("BREAKFAST", SearchMode.PLAN)) }
                refreshSearch()
            }
            is DietEvent.EditPlanned -> viewModelScope.launch {
                val entry = event.entry
                if (entry.logged) return@launch
                val per100 = FoodCatalog.nutrientsFromJson(entry.nutrientsJson)
                val portions = diet.portionsOf(entry.foodId)
                val portion = portions.firstOrNull { entry.portionLabel?.endsWith(it.label) == true }
                val form = AmountForm(
                    food = Food(entry.foodId, entry.name, FoodSource.CUSTOM, per100, portions),
                    meal = entry.meal,
                    portions = portions,
                    portion = portion,
                    count = portion?.let { entry.grams / it.grams } ?: 1.0,
                    grams = entry.grams,
                    mode = SearchMode.PLAN,
                    editingPlanId = entry.id,
                )
                flow.update { it.copy(amount = form) }
            }
            is DietEvent.LogPlanned -> viewModelScope.launch { bodyLog.logPlanned(event.id, event.date) }
            is DietEvent.RemovePlanned -> viewModelScope.launch { bodyLog.removePlanned(event.id) }

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

    /** The running fast, re-read every minute so the elapsed time moves; idle when none runs. */
    private fun fastView(): Flow<FastView> = bodyLog.observeActiveFast().flatMapLatest { session ->
        if (session == null) {
            flowOf(FastView())
        } else {
            flow {
                while (true) {
                    emit(FastView(session, FastingRules.state(session.startedAt, session.targetMinutes, bodyLog.now())))
                    delay(FAST_TICK_MS)
                }
            }
        }
    }

    private fun coverageOf(day: DiaryDay): Map<Nutrient, Double?> {
        val rows = day.meals.values.flatten().map { it.amounts }
        return Nutrient.entries.associateWith { NutrientCoverage.share(rows, it) }
    }

    private fun withPreview(form: SetupForm): SetupForm =
        form.copy(preview = form.toSetup(today())?.let { diet.preview(it, today()) })

    private suspend fun savedCopy(found: ScanState.Found): ScanState.Found? {
        val draft = CustomFoodDraft(
            name = found.food.name, barcode = found.food.id.removePrefix(OpenFoodFactsClient.ID_PREFIX), per100 = found.food.per100,
            portions = found.food.portions, servingGrams = found.food.portions.firstOrNull()?.grams,
        )
        return (foodLibrary.saveCustomFood(draft) as? CustomFoodResult.Saved)?.let { found.copy(food = it.food) }
    }

    private fun refreshSearch() {
        viewModelScope.launch {
            val search = flow.value.search ?: return@launch
            val results = when {
                search.query.isNotBlank() -> foodSearch.search(search.query, today())
                search.tab == SearchTab.FAVOURITES -> foodSearch.favourites()
                search.tab == SearchTab.RECIPES -> recipeBook.allFoods()
                else -> foodSearch.recent()
            }
            flow.update { f -> if (f.search?.query == search.query && f.search.tab == search.tab) f.copy(search = f.search.copy(results = results)) else f }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FALLBACK_MEAL = "SNACK"
        const val LAST_STEP = 3
        const val FAST_TICK_MS = 60_000L
        const val WEIGHT_DAYS = 90L

        /** A quick add keeps its numbers per 100 g and logs exactly 100 g, so the row shows what was typed. */
        const val QUICK_GRAMS = 100.0
        const val QUICK_NAME = "Quick add"
    }
}
