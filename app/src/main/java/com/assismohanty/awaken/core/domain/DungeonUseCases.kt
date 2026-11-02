package com.assismohanty.awaken.core.domain

import android.content.Context
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DungeonDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.DungeonEntity
import com.assismohanty.awaken.core.data.db.entity.DungeonStatus
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.DungeonEntry
import com.assismohanty.awaken.engine.DungeonRules
import com.assismohanty.awaken.engine.DungeonVerdict
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Localised words for a Dungeon stage quest; the engine decides its numbers (GD §16.14). */
class DungeonText @Inject constructor(@ApplicationContext private val context: Context) {
    fun stageTitle(stage: Int, total: Int, title: String): String = context.getString(R.string.dungeon_stage_title, stage, total, title)
}

sealed interface EnterDungeonResult {
    data class Entered(val dungeonId: String) : EnterDungeonResult
    data class Refused(val reason: DungeonEntry) : EnterDungeonResult
}

/**
 * Enters a Dungeon (GD §16.14) in one transaction: the run row and the entry fee as a ledger row (`GOLD −fee`,
 * `DUNGEON`, `<id>:entry`). Stage 1 is the next game-day, so its quest comes with that day's directive.
 */
class EnterDungeonUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val dungeonDao: DungeonDao,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(lengthDays: Int, stat: Stat): EnterDungeonResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run EnterDungeonResult.Refused(DungeonEntry.LOCKED)
        val balances = ledger.balances()
        val check = DungeonRules.canEnter(LevelCurve.level(balances.xpTotal), balances.gold, lengthDays, dungeonDao.active() != null)
        if (check != DungeonEntry.OK) return@run EnterDungeonResult.Refused(check)
        val fee = requireNotNull(DungeonRules.fee(lengthDays))
        val today = clock.gameDate(profile.dayStartHour)
        val id = UUID.randomUUID().toString()
        dungeonDao.upsert(
            DungeonEntity(id, lengthDays, stat, fee, enteredGameDate = today, firstStageDate = DungeonRules.firstStageDate(today), status = DungeonStatus.ACTIVE),
        )
        ledger.insertIdempotent(listOf(row(LedgerKind.GOLD, -fee.toLong(), "$id:entry", today)))
        EnterDungeonResult.Entered(id)
    }

    private fun row(kind: LedgerKind, delta: Long, sourceId: String, date: LocalDate) = LedgerEntryEntity(
        kind = kind, delta = delta, sourceType = SourceType.DUNGEON, sourceId = sourceId, gameDate = date, createdAt = clock.now(),
    )
}

/** The stage quest of the active Dungeon for [gameDate], or null when there is none that day (GD §16.14). */
class DungeonStagePlanner @Inject constructor(
    private val dungeonDao: DungeonDao,
    private val templates: TemplateRepository,
    private val reader: GameStateReader,
    private val text: DungeonText,
) {
    suspend fun entityFor(profile: HunterProfileEntity, gameDate: LocalDate, now: java.time.Instant): QuestInstanceEntity? {
        val dungeon = dungeonDao.active() ?: return null
        val stage = DungeonRules.stageOn(dungeon.firstStageDate, dungeon.lengthDays, gameDate) ?: return null
        val context = reader.hunterContext(profile, gameDate)
        val draft = DirectiveGenerator.dungeonStage(templates.library().templates, context, dungeon.stat, dungeon.id, stage, gameDate) ?: return null
        return draft.copy(title = text.stageTitle(stage, dungeon.lengthDays, draft.title)).toEntity(now)
            .copy(dungeonId = dungeon.id, dungeonStage = stage)
    }
}

/**
 * Decides the Dungeon's day when a game-day ends (GD §16.14), before the day's open quests are closed. A completed
 * stage moves on; the last one clears it and pays `GOLD +1.5 × fee` and one fragment (`<id>:clear`, idempotent). A stage not
 * completed, including a day the app never generated, fails it: the fee is gone and nothing else is taken.
 */
class SettleDungeonUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val dungeonDao: DungeonDao,
    private val questDao: QuestDao,
    private val ledger: LedgerRepository,
    private val achievements: AchievementUnlocker,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate): DungeonVerdict = transactions.run {
        val dungeon = dungeonDao.active() ?: return@run DungeonVerdict.PENDING
        val stage = DungeonRules.stageOn(dungeon.firstStageDate, dungeon.lengthDays, date) ?: return@run DungeonVerdict.PENDING
        val done = questDao.getForDate(date).any { it.dungeonId == dungeon.id && it.dungeonStage == stage && it.status == QuestStatus.COMPLETED }
        val verdict = DungeonRules.verdict(stage, dungeon.lengthDays, done)
        val now = clock.now()
        when (verdict) {
            DungeonVerdict.FAILED -> dungeonDao.upsert(dungeon.copy(status = DungeonStatus.FAILED, endedAt = now))
            DungeonVerdict.CLEARED -> {
                dungeonDao.upsert(dungeon.copy(status = DungeonStatus.CLEARED, endedAt = now))
                val reward = requireNotNull(DungeonRules.reward(dungeon.lengthDays)).toLong()
                ledger.insertIdempotent(
                    listOf(
                        LedgerEntryEntity(kind = LedgerKind.GOLD, delta = reward, sourceType = SourceType.DUNGEON, sourceId = "${dungeon.id}:clear", gameDate = date, createdAt = now),
                        LedgerEntryEntity(
                            kind = LedgerKind.FRAGMENT, delta = DungeonRules.FRAGMENTS_ON_CLEAR.toLong(), sourceType = SourceType.DUNGEON,
                            sourceId = "${dungeon.id}:clear", gameDate = date, createdAt = now,
                        ),
                    ),
                )
                hunterDao.getProfile()?.let { achievements.unlockNew(it, date, now) }
            }
            else -> Unit
        }
        verdict
    }
}

/** Walking away fails the Dungeon at once; the fee stays spent and nothing else is taken (GD §16.14). */
class AbandonDungeonUseCase @Inject constructor(
    private val dungeonDao: DungeonDao,
    private val questDao: QuestDao,
    private val hunterDao: HunterDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke() = transactions.run {
        val dungeon = dungeonDao.active() ?: return@run
        dungeonDao.upsert(dungeon.copy(status = DungeonStatus.FAILED, endedAt = clock.now()))
        val today = hunterDao.getProfile()?.let { clock.gameDate(it.dayStartHour) } ?: return@run
        questDao.getForDate(today).filter { it.dungeonId == dungeon.id && it.isOpen }.forEach {
            questDao.setStatus(it.id, QuestStatus.EXPIRED, completedAt = null)
        }
    }
}

enum class StageState { DONE, TODAY, UPCOMING, FAILED }

/** The Dungeon screen's picture of the current (or last) run. */
data class DungeonView(
    val dungeon: DungeonEntity,
    val stages: List<StageState>,
    /** The stage quest of today, if the Dungeon is on a stage today. */
    val todayQuestId: String?,
)

/** What the picker needs: whether Dungeons are unlocked, and the gold on hand. */
data class DungeonOverview(val level: Int, val gold: Long, val current: DungeonView?) {
    val unlocked: Boolean get() = DungeonRules.isUnlocked(level)
}

class ObserveDungeonUseCase @Inject constructor(
    private val dungeonDao: DungeonDao,
    private val questDao: QuestDao,
    private val hunterDao: HunterDao,
    private val ledger: LedgerRepository,
    private val clock: Clock,
) {
    operator fun invoke(ticks: Flow<Any>): Flow<DungeonOverview> =
        combine(dungeonDao.observeCurrent(), ledger.observeBalances(), ticks) { dungeon, balances, _ ->
            val level = LevelCurve.level(balances.xpTotal)
            DungeonOverview(level, balances.gold, dungeon?.let { view(it) })
        }

    private suspend fun view(dungeon: DungeonEntity): DungeonView {
        val today = hunterDao.getProfile()?.let { clock.gameDate(it.dayStartHour) } ?: dungeon.firstStageDate
        val quests = (0 until dungeon.lengthDays).associate { i ->
            val date = DungeonRules.stageDate(dungeon.firstStageDate, i + 1)
            (i + 1) to questDao.getForDate(date).firstOrNull { it.type == QuestType.DUNGEON && it.dungeonId == dungeon.id }
        }
        val stages = (1..dungeon.lengthDays).map { stage ->
            val date = DungeonRules.stageDate(dungeon.firstStageDate, stage)
            val quest = quests[stage]
            when {
                quest?.status == QuestStatus.COMPLETED -> StageState.DONE
                dungeon.status == DungeonStatus.FAILED && !date.isAfter(today) -> StageState.FAILED
                dungeon.status == DungeonStatus.FAILED -> StageState.UPCOMING
                date == today -> StageState.TODAY
                date.isBefore(today) -> StageState.FAILED
                else -> StageState.UPCOMING
            }
        }
        val todayStage = DungeonRules.stageOn(dungeon.firstStageDate, dungeon.lengthDays, today)
        return DungeonView(dungeon, stages, todayQuestId = todayStage?.let { quests[it]?.id })
    }
}
