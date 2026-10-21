package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.CompletableQuest
import com.assismohanty.awaken.engine.LedgerMath
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestReward
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.path.SlotOutcome
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class CompletionOutcome(
    val questInstanceId: String,
    val completionId: String,
    val reward: QuestReward,
    /** The completed quest's difficulty and verifier, for the reward sheet's breakdown labels. */
    val difficulty: Difficulty,
    val verification: VerificationSource,
    val levelBefore: Int,
    val levelAfter: Int,
    /** The rank whose trial this completion just made available, if any. */
    val rankTrialUnlocked: Rank?,
    /** Set when this completion cleared the last Rank Trial stage: the hunter was promoted to this rank (GD §5). */
    val promotedTo: Rank? = null,
    val unlockedAchievements: List<String>,
    /** False for a cleared Focus Gate, which is final (ENGINE_DECISIONS #36). */
    val undoable: Boolean = true,
    /** A Path quest: the reward sheet offers the Too easy / Right / Too hard chips (GD §6.6.5). */
    val pathQuest: Boolean = false,
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

sealed interface CompleteQuestResult {
    data class Completed(val outcome: CompletionOutcome) : CompleteQuestResult
    data object NoHunter : CompleteQuestResult
    data object NotFound : CompleteQuestResult
    data class NotCompletable(val status: QuestStatus) : CompleteQuestResult
    /** The quest belongs to another game-day. */
    data object WrongDay : CompleteQuestResult
    /** HEALTH/TIMER quests complete only through their verifier (GD §6.5). */
    data object VerificationRequired : CompleteQuestResult
    data class TooSoon(val remaining: Duration) : CompleteQuestResult
}

/** The economy pipeline (ARCH §3.1) — the only code path that mints quest XP. One transaction per completion. */
class CompleteQuestUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val completionDao: CompletionDao,
    private val achievements: AchievementUnlocker,
    private val trialDao: TrialDao,
    private val trialReader: TrialReader,
    private val pathDao: PathDao,
    private val ledger: LedgerRepository,
    private val reader: GameStateReader,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Manual completion from the UI: SELF quests only. */
    suspend operator fun invoke(questInstanceId: String): CompleteQuestResult = complete(questInstanceId, verifiedBy = null)

    /** Verified entry point for Focus Gates (TIMER, Phase 8) and Health Connect sync (HEALTH, Phase 11). */
    suspend fun completeVerified(questInstanceId: String, verifiedBy: VerificationSource): CompleteQuestResult =
        complete(questInstanceId, verifiedBy)

    private suspend fun complete(questInstanceId: String, verifiedBy: VerificationSource?): CompleteQuestResult =
        transactions.run {
            val profile = hunterDao.getProfile() ?: return@run CompleteQuestResult.NoHunter
            val quest = questDao.get(questInstanceId) ?: return@run CompleteQuestResult.NotFound
            if (quest.status != QuestStatus.ACTIVE && quest.status != QuestStatus.STARTED) {
                return@run CompleteQuestResult.NotCompletable(quest.status)
            }
            val now = clock.now()
            val today = clock.gameDate(profile.dayStartHour)
            if (quest.gameDate != today) return@run CompleteQuestResult.WrongDay

            val source = quest.verification.source
            // SELF quests complete by hand only; HEALTH/TIMER only through their own verifier.
            val expectedVerifier = if (source == VerificationSource.SELF) null else source
            if (verifiedBy != expectedVerifier) return@run CompleteQuestResult.VerificationRequired
            val startedAt = quest.startedAt
            if (startedAt != null && !QuestRules.canComplete(startedAt, now, quest.minDurationSec)) {
                val elapsed = Duration.between(startedAt, now)
                return@run CompleteQuestResult.TooSoon(Duration.ofSeconds(quest.minDurationSec.toLong()).minus(elapsed))
            }

            val levelBefore = LevelCurve.level(ledger.balances().xpTotal)
            val passedTrials = trialDao.passedRanks().toSet()
            val trialBefore = trialAvailable(levelBefore, passedTrials, now)

            val attempt = completionDao.countFor(quest.id) + 1
            val completionId = LedgerMath.completionId(quest.id, attempt)
            val context = reader.hunterContext(profile, today)
            val reward = XpCalculator.forCompletion(quest.toCompletable(source), context)

            questDao.setStatus(quest.id, QuestStatus.COMPLETED, completedAt = now)
            quest.pathSlotId?.let { pathDao.setOutcome(it, SlotOutcome.COMPLETED) }
            completionDao.insert(
                CompletionEntity(
                    id = completionId,
                    questInstanceId = quest.id,
                    attempt = attempt,
                    gameDate = today,
                    questType = quest.type,
                    stat = quest.stat,
                    verificationSource = source,
                    crit = reward.crit,
                    xp = reward.xp,
                    completedAt = now,
                ),
            )
            ledger.insertIdempotent(reward.toLedgerRows(quest, completionId, source, today, now))
            val promotedTo = if (quest.type == QuestType.TRIAL) clearTrialStage(quest, today, now) else null

            val balances = ledger.balances()
            val levelAfter = LevelCurve.level(balances.xpTotal)
            val unlocked = achievements.unlockNew(profile, today, now) // after the reward is written (GD §10)
            val trialAfter = trialAvailable(levelAfter, passedTrials, now)

            CompleteQuestResult.Completed(
                CompletionOutcome(
                    questInstanceId = quest.id,
                    completionId = completionId,
                    reward = reward,
                    difficulty = quest.difficulty,
                    verification = source,
                    levelBefore = levelBefore,
                    levelAfter = levelAfter,
                    rankTrialUnlocked = trialAfter.takeIf { it != null && it != trialBefore },
                    promotedTo = promotedTo,
                    unlockedAchievements = unlocked,
                    // Timed work is final: a cleared Focus Gate, or a Path quest cleared through its gate (ENGINE_DECISIONS #36, #76).
                    undoable = quest.type != QuestType.GATE && source != VerificationSource.HEALTH &&
                        !(source == VerificationSource.TIMER && quest.pathSlotId != null),
                    pathQuest = quest.pathSlotId != null,
                ),
            )
        }

    /**
     * A cleared stage closes today's other stages (one per day, ENGINE_DECISIONS #31). If it was the last stage, the
     * trial passes: status, +500 gold and promotion, all inside the completion's transaction (invariant 3).
     */
    private suspend fun clearTrialStage(quest: QuestInstanceEntity, today: LocalDate, now: Instant): Rank? {
        questDao.closeOtherTrialStages(today, exceptId = quest.id)
        val trial = trialDao.active() ?: return null
        if (trialReader.progress(trial, today).status != RankRules.TrialStatus.PASSED) return null
        trialDao.upsert(trial.copy(status = RankRules.TrialStatus.PASSED, endedAt = now))
        ledger.insertIdempotent(listOf(trialRewardRow(trial, today, now)))
        return trial.targetRank
    }

    private suspend fun trialAvailable(level: Int, passedTrials: Set<Rank>, now: Instant): Rank? =
        RankRules.trialAvailable(level, passedTrials, trialDao.activeCount() > 0, trialDao.lastFailedAt(), now)

    private fun QuestInstanceEntity.toCompletable(source: VerificationSource) = CompletableQuest(
        questInstanceId = id,
        type = type,
        stat = stat,
        secondaryStat = secondaryStat,
        difficulty = difficulty,
        verification = source,
    )

    private fun QuestReward.toLedgerRows(
        quest: QuestInstanceEntity,
        completionId: String,
        source: VerificationSource,
        gameDate: LocalDate,
        now: Instant,
    ): List<LedgerEntryEntity> {
        val sourceType = if (quest.type == QuestType.GATE) SourceType.GATE else SourceType.QUEST
        val breakdownJson = Json.encodeToString(ListSerializer(BreakdownItem.serializer()), breakdown)
        return LedgerMath.rowsFor(this, sourceType, completionId).map { row ->
            val isXpTotal = row.kind == LedgerKind.XP_TOTAL
            LedgerEntryEntity(
                kind = row.kind,
                stat = row.stat,
                itemId = row.itemId,
                delta = row.delta,
                sourceType = row.sourceType,
                sourceId = row.sourceId,
                gameDate = gameDate,
                xpSource = if (isXpTotal) source.name else "",
                createdAt = now,
                breakdownJson = if (isXpTotal) breakdownJson else null,
            )
        }
    }
}
