package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CampaignDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.CampaignEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.campaign.Archetype
import com.assismohanty.awaken.engine.campaign.CampaignAct
import com.assismohanty.awaken.engine.campaign.CampaignPlanner
import com.assismohanty.awaken.engine.campaign.CampaignRules
import com.assismohanty.awaken.engine.campaign.CampaignScan
import com.assismohanty.awaken.engine.campaign.CampaignStatus
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** One planned campaign habit, priced for the act it starts in. */
data class CampaignHabitPreview(
    val templateId: String,
    val stat: Stat,
    val title: String,
    val unit: String,
    val days: Set<DayOfWeek>,
    val difficulty: Difficulty,
    val target: Int,
)

enum class CampaignProblem {
    /** A campaign is already running. */
    ACTIVE,

    /** Fewer than 3 free habit slots (GD §6.8). */
    NO_ROOM,

    /** No usable template for the planned stats (equipment / injury filters). */
    NO_HABITS,
}

/** What the scan says and what the campaign would contain, before the oath is sealed. */
data class CampaignPreview(
    val scores: Map<Stat, Int>,
    val weakest: Stat,
    val archetype: Archetype,
    val habits: List<CampaignHabitPreview>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val problem: CampaignProblem?,
)

internal fun Map<Stat, Int>.toScoresText(): String = Stat.entries.joinToString(",") { "${it.name}:${this[it] ?: 0}" }

internal fun String.toScores(): Map<Stat, Int> = split(",").mapNotNull { part ->
    val (name, value) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
    Stat.entries.firstOrNull { it.name == name }?.let { it to (value.toIntOrNull() ?: 0) }
}.toMap()

/** Plans a campaign from scan answers without saving anything (GD §6.8). */
class PreviewCampaignUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val campaignDao: CampaignDao,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(answers: Map<String, Int>): CampaignPreview? {
        val profile = hunterDao.getProfile() ?: return null
        return preview(profile, answers)
    }

    internal suspend fun preview(profile: HunterProfileEntity, answers: Map<String, Int>): CampaignPreview {
        val today = clock.gameDate(profile.dayStartHour)
        val scores = CampaignScan.scores(answers)
        val count = CampaignRules.habitCount(habitDao.countActive(), QuestRules.MAX_ACTIVE_CUSTOM)
        val context = reader.hunterContext(profile, today)
        val library = templates.library().templates
        val byId = library.associateBy { it.id }
        val habits = CampaignPlanner.plan(library, context, scores, count).map { plan ->
            val difficulty = CampaignRules.habitDifficulty(CampaignAct.FOUNDATION, context.tier(plan.stat))
            CampaignHabitPreview(
                templateId = plan.templateId,
                stat = plan.stat,
                title = plan.title,
                unit = plan.unit,
                days = plan.days,
                difficulty = difficulty,
                target = CampaignPlanner.target(byId.getValue(plan.templateId), difficulty),
            )
        }
        val problem = when {
            campaignDao.active() != null -> CampaignProblem.ACTIVE
            count == 0 -> CampaignProblem.NO_ROOM
            habits.size < CampaignRules.MIN_HABITS -> CampaignProblem.NO_HABITS
            else -> null
        }
        return CampaignPreview(
            scores = scores,
            weakest = CampaignScan.weakest(scores),
            archetype = CampaignScan.archetype(scores),
            habits = habits,
            startDate = today,
            endDate = CampaignRules.endDate(today),
            problem = problem,
        )
    }
}

sealed interface StartCampaignResult {
    data class Started(val campaignId: String) : StartCampaignResult
    data class Refused(val problem: CampaignProblem) : StartCampaignResult
    data object IncompleteScan : StartCampaignResult
}

/**
 * Sealing the oath (GD §6.8): the campaign and its habits are written in one transaction; day 1 is today, and today's
 * habits join the directive straight away.
 */
class StartCampaignUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val campaignDao: CampaignDao,
    private val preview: PreviewCampaignUseCase,
    private val generateDirective: GenerateDirectiveUseCase,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(answers: Map<String, Int>): StartCampaignResult {
        if (!CampaignScan.isComplete(answers)) return StartCampaignResult.IncompleteScan
        val profile = hunterDao.getProfile() ?: return StartCampaignResult.Refused(CampaignProblem.NO_HABITS)
        val result = transactions.run {
            val plan = preview.preview(profile, answers)
            plan.problem?.let { return@run StartCampaignResult.Refused(it) }
            val now = clock.now()
            val id = UUID.randomUUID().toString()
            campaignDao.upsert(
                CampaignEntity(
                    id = id,
                    startDate = plan.startDate,
                    focusStat = plan.weakest,
                    archetype = plan.archetype,
                    scores = plan.scores.toScoresText(),
                    oathSealedAt = now,
                    status = CampaignStatus.ACTIVE,
                    currentAct = CampaignAct.FOUNDATION,
                ),
            )
            plan.habits.forEach { habit ->
                habitDao.upsert(
                    HabitEntity(
                        id = UUID.randomUUID().toString(),
                        title = habit.title,
                        stat = habit.stat,
                        difficulty = habit.difficulty,
                        verification = Verification.SELF,
                        daysOfWeekMask = habit.days.toMask(),
                        target = habit.target,
                        unit = habit.unit,
                        active = true,
                        createdAt = now,
                        campaignId = id,
                        templateId = habit.templateId,
                    ),
                )
            }
            StartCampaignResult.Started(id)
        }
        if (result is StartCampaignResult.Started) generateDirective(clock.gameDate(profile.dayStartHour))
        return result
    }
}

/** Abandoning stops the campaign and archives its habits; nothing earned is touched (GD §6.8, pillar 3). */
class AbandonCampaignUseCase @Inject constructor(
    private val campaignDao: CampaignDao,
    private val habitDao: HabitDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke() = transactions.run {
        val campaign = campaignDao.active() ?: return@run
        campaignDao.upsert(campaign.copy(status = CampaignStatus.ABANDONED, endedAt = clock.now()))
        campaignDao.habits(campaign.id).forEach { habitDao.upsert(it.copy(active = false)) }
    }
}

sealed interface CampaignAdvance {
    data class ActChanged(val act: CampaignAct) : CampaignAdvance
    data class Finished(val status: CampaignStatus, val clearedDays: Int) : CampaignAdvance
}

/**
 * Runs in the rollover after the finished days are finalized and before today's directive (GD §6.8): re-prices the
 * habits when a new act begins, and once day 45 is behind us records the outcome, archives the habits and lets the
 * achievement pipeline pay Reforged (ENGINE_DECISIONS #93). Idempotent: a second run on the same day changes nothing.
 */
class AdvanceCampaignUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val campaignDao: CampaignDao,
    private val dayRecordDao: DayRecordDao,
    private val habitDao: HabitDao,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val achievements: AchievementUnlocker,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(today: LocalDate): CampaignAdvance? {
        val library = templates.library().templates.associateBy { it.id }
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run null
            val campaign = campaignDao.active() ?: return@run null
            val end = CampaignRules.endDate(campaign.startDate)
            val cleared = dayRecordDao.countCleared(campaign.startDate, minOf(end, today.minusDays(1)))
            if (CampaignRules.isOver(campaign.startDate, today)) {
                val status = CampaignRules.outcome(cleared)
                campaignDao.upsert(campaign.copy(status = status, clearedDays = cleared, endedAt = clock.now()))
                campaignDao.habits(campaign.id).forEach { habitDao.upsert(it.copy(active = false)) }
                achievements.unlockNew(profile, today, clock.now())
                return@run CampaignAdvance.Finished(status, cleared)
            }
            val dayIndex = CampaignRules.dayIndex(campaign.startDate, today) ?: return@run null
            val act = CampaignRules.act(dayIndex)
            if (act == campaign.currentAct) {
                if (cleared != campaign.clearedDays) campaignDao.upsert(campaign.copy(clearedDays = cleared))
                return@run null
            }
            val context = reader.hunterContext(profile, today)
            campaignDao.habits(campaign.id).filter { it.active }.forEach { habit ->
                val template = habit.templateId?.let(library::get) ?: return@forEach
                val difficulty = CampaignRules.habitDifficulty(act, context.tier(habit.stat))
                habitDao.upsert(habit.copy(difficulty = difficulty, target = CampaignPlanner.target(template, difficulty)))
            }
            campaignDao.upsert(campaign.copy(currentAct = act, clearedDays = cleared))
            CampaignAdvance.ActChanged(act)
        }
    }
}

/** The campaign screen and Today card (DS §4.10). */
data class CampaignModel(
    val campaign: CampaignEntity,
    val today: LocalDate,
    val endDate: LocalDate,
    /** Null before day 1 or after day 45. */
    val dayIndex: Int?,
    val act: CampaignAct,
    val clearedDates: Set<LocalDate>,
    val habits: List<HabitEntity>,
) {
    val clearedDays: Int get() = clearedDates.size
    val daysLeft: Int get() = dayIndex?.let { CampaignRules.LENGTH_DAYS - it + 1 } ?: 0
    val reachable: Boolean get() = CampaignRules.stillReachable(clearedDays, daysLeft)
    val scores: Map<Stat, Int> get() = campaign.scores.toScores()
}

class ObserveCampaignUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val campaignDao: CampaignDao,
    private val dayRecordDao: DayRecordDao,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<CampaignModel?> =
        combine(hunterDao.observeProfile(), campaignDao.observeCurrent(), dayChanges) { profile, campaign, _ -> profile to campaign }
            .flatMapLatest { (profile, campaign) ->
                if (profile == null || campaign == null) return@flatMapLatest flowOf(null)
                val today = clock.gameDate(profile.dayStartHour)
                val end = CampaignRules.endDate(campaign.startDate)
                combine(dayRecordDao.observeClearedDates(campaign.startDate, end), campaignDao.observeHabits(campaign.id)) { cleared, habits ->
                    val dayIndex = if (campaign.status == CampaignStatus.ACTIVE) CampaignRules.dayIndex(campaign.startDate, today) else null
                    CampaignModel(
                        campaign = campaign,
                        today = today,
                        endDate = end,
                        dayIndex = dayIndex,
                        act = dayIndex?.let(CampaignRules::act) ?: campaign.currentAct,
                        clearedDates = cleared.toSet(),
                        habits = habits,
                    )
                }
            }
}
