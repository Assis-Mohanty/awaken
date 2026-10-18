package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.TrialEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.RankRules.StageCompletion
import com.assismohanty.awaken.engine.RankRules.TrialStatus
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** A trial's stages and how far the hunter got (GD §5, ENGINE_DECISIONS #31). */
data class TrialProgress(
    val trial: TrialEntity,
    /** The three stage stats, fixed when the trial started. */
    val stageStats: List<Stat>,
    val cleared: Map<Stat, LocalDate>,
    val status: TrialStatus,
) {
    val openStages: List<Stat> get() = RankRules.openStages(stageStats, cleared)
}

/** Derives trial progress from its TRIAL quests; nothing about stages is stored separately. */
class TrialReader @Inject constructor(private val questDao: QuestDao) {
    suspend fun progress(trial: TrialEntity, today: LocalDate): TrialProgress {
        // Every stage quest is generated on the start day, so that day's TRIAL quests name the stages.
        val stageStats = questDao.getForDate(trial.startedGameDate)
            .filter { it.type == QuestType.TRIAL }
            .map { it.stat }
            .distinct()
            .sortedBy { it.ordinal }
        val completions = questDao.completedTrialStages(trial.startedGameDate, trial.expiresGameDate)
            .map { StageCompletion(it.stat, it.gameDate) }
        val cleared = RankRules.clearedStages(trial.startedGameDate, completions)
        return TrialProgress(trial, stageStats, cleared, RankRules.trialStatus(trial.startedGameDate, today, cleared.values.toList()))
    }
}

sealed interface StartTrialResult {
    data class Started(val trialId: String) : StartTrialResult
    data object NotAvailable : StartTrialResult
    /** Equipment/injury filters left a stage stat with no Hard template; the trial can't be completed, so it won't start. */
    data object NoStages : StartTrialResult
}

/** "Begin trial" (GD §5): creates the ACTIVE trial and today's three stage quests in one transaction. */
class StartTrialUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val trialDao: TrialDao,
    private val ledger: LedgerRepository,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(): StartTrialResult {
        val library = templates.library()
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run StartTrialResult.NotAvailable
            val now = clock.now()
            val today = clock.gameDate(profile.dayStartHour)
            val balances = ledger.balances()
            val target = RankRules.trialAvailable(
                level = LevelCurve.level(balances.xpTotal),
                passedTrials = trialDao.passedRanks().toSet(),
                trialActive = trialDao.activeCount() > 0,
                lastFailedAt = trialDao.lastFailedAt(),
                now = now,
            ) ?: return@run StartTrialResult.NotAvailable
            val stats = RankRules.trialStats(Stat.entries.associateWith { balances.statXp(it) }, profile.focusStats)
            val stages = DirectiveGenerator.trialStages(library.templates, reader.hunterContext(profile, today), stats, today)
            if (stages.size < RankRules.TRIAL_STAGES) return@run StartTrialResult.NoStages

            val trial = TrialEntity(
                id = UUID.randomUUID().toString(),
                targetRank = target,
                startedGameDate = today,
                expiresGameDate = RankRules.trialExpiresOn(today),
                status = TrialStatus.ACTIVE,
                endedAt = null,
            )
            trialDao.upsert(trial)
            questDao.insertAll(stages.map { it.toEntity(now) })
            StartTrialResult.Started(trial.id)
        }
    }
}

/**
 * The rollover's trial step for [today]: an ACTIVE trial past its window fails (timestamped at the start of the next
 * game-day, ENGINE_DECISIONS #31); otherwise, unless a stage was already cleared today, today's open stages are
 * generated. Returns the rank whose trial just failed, if any.
 */
class AdvanceTrialUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val trialDao: TrialDao,
    private val trialReader: TrialReader,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(profile: HunterProfileEntity, today: LocalDate): Rank? {
        val library = templates.library()
        return transactions.run {
            val trial = trialDao.active() ?: return@run null
            val progress = trialReader.progress(trial, today)
            when {
                progress.status == TrialStatus.FAILED -> {
                    val failedAt = RankRules.trialFailedAt(trial.startedGameDate, clock.zone(), profile.dayStartHour)
                    trialDao.upsert(trial.copy(status = TrialStatus.FAILED, endedAt = failedAt))
                    trial.targetRank
                }
                today in progress.cleared.values || progress.openStages.isEmpty() -> null
                else -> {
                    val stages = DirectiveGenerator.trialStages(
                        library.templates, reader.hunterContext(profile, today), progress.openStages, today,
                    )
                    questDao.insertAll(stages.map { it.toEntity(clock.now()) }) // deterministic ids: re-runs are no-ops
                    null
                }
            }
        }
    }
}

/** Ledger row for passing a trial (GD §5: +500 gold), keyed by the trial so it is granted once. */
internal fun trialRewardRow(trial: TrialEntity, gameDate: LocalDate, now: Instant) = LedgerEntryEntity(
    kind = LedgerKind.GOLD,
    delta = RankRules.TRIAL_REWARD_GOLD.toLong(),
    sourceType = SourceType.TRIAL,
    sourceId = trial.id,
    gameDate = gameDate,
    createdAt = now,
)
