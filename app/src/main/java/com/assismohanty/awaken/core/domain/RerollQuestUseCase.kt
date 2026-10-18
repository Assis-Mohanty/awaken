package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.model.Directive
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import javax.inject.Inject

sealed interface RerollResult {
    data class Rerolled(val replacement: QuestInstanceEntity, val cost: Int) : RerollResult
    /** Only today's open side quests can be rerolled (never the main quest). */
    data object NotRerollable : RerollResult
    data object LimitReached : RerollResult
    data class InsufficientGold(val cost: Int, val gold: Long) : RerollResult
    data object NoAlternative : RerollResult
}

/** Side-quest reroll (GD §6.3): first free, then 40 gold each, max 3 paid per day. One transaction. */
class RerollQuestUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val ledger: LedgerRepository,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(questInstanceId: String): RerollResult {
        val library = templates.library()
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run RerollResult.NotRerollable
            val today = clock.gameDate(profile.dayStartHour)
            val quest = questDao.get(questInstanceId)
            if (quest == null || quest.type != QuestType.SIDE || quest.gameDate != today ||
                (quest.status != QuestStatus.ACTIVE && quest.status != QuestStatus.STARTED)
            ) {
                return@run RerollResult.NotRerollable
            }

            val rerollNumber = questDao.countRerolled(today) + 1
            val cost = DirectiveGenerator.rerollCost(rerollNumber) ?: return@run RerollResult.LimitReached
            val gold = ledger.balances().gold
            if (gold < cost) return@run RerollResult.InsufficientGold(cost, gold)

            val current = questDao.getForDate(today)
                .filter { (it.type == QuestType.MAIN || it.type == QuestType.SIDE) && it.status != QuestStatus.REROLLED }
                .mapNotNull { it.toDraft() }
            val directive = Directive(today, current.firstOrNull { it.type == QuestType.MAIN }, current.filter { it.type == QuestType.SIDE })
            val draft = DirectiveGenerator.rerollSide(
                templates = library.templates,
                context = reader.hunterContext(profile, today),
                current = directive,
                rerollNumber = rerollNumber,
                alsoExclude = questDao.rerolledTemplateIds(today).toSet(),
            ) ?: return@run RerollResult.NoAlternative

            val now = clock.now()
            val replacement = draft.toEntity(now)
            questDao.setStatus(quest.id, QuestStatus.REROLLED, completedAt = null)
            questDao.insertAll(listOf(replacement))
            if (cost > 0) {
                ledger.insertIdempotent(
                    listOf(
                        LedgerEntryEntity(
                            kind = LedgerKind.GOLD,
                            delta = -cost.toLong(),
                            sourceType = SourceType.SHOP,
                            sourceId = "reroll:$today#$rerollNumber",
                            gameDate = today,
                            createdAt = now,
                        ),
                    ),
                )
            }
            RerollResult.Rerolled(replacement, cost)
        }
    }
}
