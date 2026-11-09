package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.core.data.db.entity.AppRuleEntity
import com.assismohanty.awaken.core.data.db.entity.BedtimeRuleEntity
import com.assismohanty.awaken.core.data.db.entity.FocusGoalEntity
import com.assismohanty.awaken.core.data.db.entity.GymSettingsEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.OnboardingDraftStore
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.FocusGoalRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.TrainingDays
import com.assismohanty.awaken.engine.body.Gear
import com.assismohanty.awaken.engine.body.PlateMath
import com.assismohanty.awaken.engine.body.PlateUnit
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.onboarding.ImproveGoal
import com.assismohanty.awaken.engine.onboarding.InjuryRegion
import com.assismohanty.awaken.engine.onboarding.MassUnit
import com.assismohanty.awaken.engine.onboarding.OnboardingAnswers
import com.assismohanty.awaken.engine.onboarding.OnboardingGrants
import com.assismohanty.awaken.engine.onboarding.OnboardingPlan
import com.assismohanty.awaken.engine.onboarding.OnboardingPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

sealed interface OnboardingResult {
    data object Done : OnboardingResult
    data object InvalidName : OnboardingResult
    data object InvalidHunterId : OnboardingResult
}

/**
 * The profile the plan describes, exactly as Start will save it. The preview builds the same profile in memory, so the
 * first plan it shows is the one the first rollover generates.
 */
internal fun onboardingProfile(
    answers: OnboardingAnswers,
    plan: OnboardingPlan,
    today: LocalDate,
    now: Instant,
): HunterProfileEntity {
    val defaults = HunterProfileEntity(
        hunterId = answers.hunterId, name = "", createdAt = now, firstGameDate = today, focusStat1 = Stat.STR, focusStat2 = Stat.INT,
        dayStartHour = GameDay.DEFAULT_DAY_START_HOUR, onboardingDone = true,
    )
    return defaults.copy(
        name = answers.name.trim(),
        focusStat1 = plan.focusStats[0],
        focusStat2 = plan.focusStats[1],
        reminderTime = LocalTime.ofSecondOfDay(plan.reminderMinute * SECONDS_PER_MINUTE).toString(),
        ownedEquipment = plan.ownedEquipment,
        injuryMode = plan.injuryMode,
        // Tags only mean something while Injury Mode is on; otherwise the profile keeps its usual default.
        injuryTags = if (plan.injuryMode) plan.injuryTags else defaults.injuryTags,
        healthEnabled = plan.healthEnabled,
        screenTimeFrom = if (plan.discipline != null) ScreenTimeRules.effectiveFromForEdit(today) else null,
        stepGoal = plan.stepGoal,
        birthYear = plan.birthYear,
        heightCm = plan.heightCm,
        weightKg = plan.weightKg,
        dietGoalHint = plan.dietGoalHint,
        trainingDays = TrainingDays.mask(plan.trainingDays),
        trainingReminder = plan.trainingReminder,
    )
}

private const val SECONDS_PER_MINUTE = 60L

/** One quest the first plan will hold, as the plan screen lists it. */
data class OnboardingQuestLine(val title: String, val stat: Stat, val difficulty: Difficulty)

data class OnboardingPlanPreview(val plan: OnboardingPlan, val quests: List<OnboardingQuestLine>)

/**
 * Works out the plan and the quests it would produce, without writing anything (docs/onboarding/PLAN.md §3.3). The quests
 * come from the same generator and the same context code as the real first plan, for a hunter who does not exist yet.
 */
class PreviewOnboardingPlanUseCase @Inject constructor(
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(answers: OnboardingAnswers, grants: OnboardingGrants): OnboardingPlanPreview {
        val today = clock.gameDate(GameDay.DEFAULT_DAY_START_HOUR)
        val plan = OnboardingPlanner.plan(answers, grants, today)
        val profile = onboardingProfile(answers, plan, today, clock.now())
        val library = templates.library()
        // The real tiers are saved from the plan, so they are what the generator reads on day one.
        val context = reader.hunterContext(profile, today).copy(tiers = plan.tiers)
        val directive = DirectiveGenerator.generate(library.templates, context, today)
        return OnboardingPlanPreview(plan, directive.quests.map { OnboardingQuestLine(it.title, it.stat, it.difficulty) })
    }
}

/**
 * Start (docs/onboarding/PLAN.md §3.3): saves the profile and everything the plan set, in ONE transaction, then runs the
 * first rollover so day one has its plan. If anything fails the transaction rolls back, nothing exists, and the person
 * can try again with their answers intact. No ledger rows are written and nothing is rewarded for answering.
 *
 * Callers run this non-cancellable: the profile row is what closes the onboarding screen, and the work after it must still
 * finish.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val focusDao: FocusDao,
    private val screenTimeDao: ScreenTimeDao,
    private val gymDao: GymDao,
    private val usage: UsageGateway,
    private val rollover: DayRolloverUseCase,
    private val transactions: TransactionRunner,
    private val drafts: OnboardingDraftStore,
    private val uiPrefs: UiPrefsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        answers: OnboardingAnswers,
        grants: OnboardingGrants,
        /** The notification prompt was answered here, so the one after a first quest must not repeat it. */
        notificationsAsked: Boolean = false,
    ): OnboardingResult {
        if (!Awakening.isValidName(answers.name)) return OnboardingResult.InvalidName
        if (answers.hunterId.isBlank()) return OnboardingResult.InvalidHunterId
        val today = clock.gameDate(GameDay.DEFAULT_DAY_START_HOUR)
        val plan = OnboardingPlanner.plan(answers, grants, today)
        val profile = onboardingProfile(answers, plan, today, clock.now())
        // Read before the transaction: it is the phone, not the database.
        val launcherApps = if (plan.discipline != null) launcherApps() else emptyList()

        transactions.run {
            hunterDao.upsertProfile(profile)
            hunterDao.upsertTiers(Stat.entries.map { StatTierEntity(it, plan.tiers[it] ?: Difficulty.NORMAL) })
            if (plan.focusGoalMinutes != FocusGoalRules.DEFAULT_GOAL) {
                focusDao.upsertGoal(FocusGoalEntity(effectiveFrom = today, minutes = plan.focusGoalMinutes))
            }
            plan.phoneDown?.let {
                screenTimeDao.upsertBedtime(
                    BedtimeRuleEntity(BedtimeRules.effectiveFromForEnable(today), enabled = true, bedtimeMin = it.bedtimeMinute, wakeMin = it.wakeMinute),
                )
            }
            plan.discipline?.let { discipline ->
                val from = ScreenTimeRules.effectiveFromForEdit(today)
                screenTimeDao.upsertRules(
                    launcherApps.map { app ->
                        val category = ScreenTimeRules.defaultCategory(app.packageName, app.kind)
                        val limit = if (ScreenTimeRules.defaultLimit(category) != null) discipline.limitMinutes else null
                        AppRuleEntity(app.packageName, from, category, limit)
                    },
                )
            }
            // Always saved now: the fitness level, equipment and injuries are editable in Gym (GD §18.1), so they must exist there.
            gymDao.upsertGymSettings(gymSettings(plan, answers))
        }

        // The profile exists now and the app is already on its way to Today; a failure here only delays the first plan, and
        // the next foreground rollover generates it.
        try {
            rollover()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "The first rollover after onboarding failed")
        }
        drafts.clear()
        if (notificationsAsked) uiPrefs.setNotificationsPrompted()
        return OnboardingResult.Done
    }

    private suspend fun launcherApps(): List<InstalledApp> = try {
        usage.launcherApps()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Without the app list there are no rules to seed; the person can set them in Screen time.
        Timber.w(e, "Reading launcher apps failed")
        emptyList()
    }

    private fun gymSettings(plan: OnboardingPlan, answers: OnboardingAnswers): GymSettingsEntity {
        val kg = PlateMath.defaults(PlateUnit.KG)
        val lb = PlateMath.defaults(PlateUnit.LB)
        return GymSettingsEntity(
            barKg = kg.barWeight, platesKg = kg.plates.joinToString(","), barLb = lb.barWeight, platesLb = lb.plates.joinToString(","),
            unit = (if (answers.weightUnit == MassUnit.LB) PlateUnit.LB else PlateUnit.KG).name,
            repMin = plan.repRange.min, repMax = plan.repRange.max,
            fitnessLevel = answers.fitness?.name,
            goal = trainingGoal(answers),
            equipment = answers.equipment?.let { choice -> Gear.fromOnboarding(choice).joinToString(",") { it.key } },
            injuries = answers.injuries.filter { it != InjuryRegion.NO_INJURIES }.joinToString(",") { it.name },
        )
    }

    /** Strength if the hunter wants it, else general fitness; the plans have no goal to infer from the other answers. */
    private fun trainingGoal(answers: OnboardingAnswers): String? = when {
        ImproveGoal.STRENGTH in answers.improve -> "strength"
        ImproveGoal.FITNESS in answers.improve -> "general"
        else -> null
    }
}
