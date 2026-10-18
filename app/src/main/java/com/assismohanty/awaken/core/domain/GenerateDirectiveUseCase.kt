package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DirectiveGenerator
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Daily Directive for one game-date (GD §6.3): main + sides once per date, plus today's habits. Idempotent: ids are
 * deterministic, so a second call (or a race) inserts nothing new.
 */
class GenerateDirectiveUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val habitDao: HabitDao,
    private val reader: GameStateReader,
    private val templates: TemplateRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Returns the quests for [gameDate] after generation, or null when there is no hunter yet. */
    suspend operator fun invoke(gameDate: LocalDate): List<QuestInstanceEntity>? {
        val library = templates.library()
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run null
            val now = clock.now()
            if (questDao.countGenerated(gameDate) == 0) {
                val context = reader.hunterContext(profile, gameDate)
                val directive = DirectiveGenerator.generate(library.templates, context, gameDate)
                questDao.insertAll(directive.quests.map { it.toEntity(now) })
            }
            questDao.insertAll(
                habitDao.active()
                    .filter { it.isScheduledOn(gameDate) }
                    .map { habit -> habit.toQuest(profile.hunterId, gameDate, now) },
            )
            questDao.getForDate(gameDate)
        }
    }

    private fun HabitEntity.isScheduledOn(date: LocalDate): Boolean =
        daysOfWeekMask and (1 shl (date.dayOfWeek.value - 1)) != 0

    private fun HabitEntity.toQuest(hunterId: String, gameDate: LocalDate, now: Instant) = QuestInstanceEntity(
        id = DirectiveGenerator.instanceId(hunterId, gameDate, "habit:$id"),
        gameDate = gameDate,
        type = QuestType.HABIT,
        templateId = null,
        habitId = id,
        stat = stat,
        secondaryStat = null,
        title = title,
        description = title,
        unit = unit,
        target = target,
        difficulty = difficulty,
        verification = verification,
        minDurationSec = 0,
        status = QuestStatus.ACTIVE,
        createdAt = now,
    )
}
