package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.SlotOutcome
import java.time.LocalDate
import javax.inject.Inject

/**
 * Chooses which Path slots become quests on a game-day (GD §6.6.6) and records the moves the cap forces. Callers run it
 * inside their own transaction. Quest ids are name-based on `(slotId, gameDate)`, so asking twice never duplicates.
 */
class PathQuestPlanner @Inject constructor(
    private val pathDao: PathDao,
    private val questDao: QuestDao,
) {
    /**
     * Drafts for the slots of [gameDate] that have no quest yet, at most as many as the day's cap of
     * [PathScheduler.MAX_PATH_QUESTS_PER_DAY] still allows (quests already shown, even completed ones, use it up).
     * Slots beyond the cap move to their Path's next active day this week, or become unscheduled (ENGINE_DECISIONS #67).
     */
    suspend fun draftsFor(gameDate: LocalDate): List<QuestDraft> {
        val slots = pathDao.assignedOn(gameDate).map { it.toSlot() }
        if (slots.isEmpty()) return emptyList()
        val shown = questDao.getForDate(gameDate).filter { it.pathSlotId != null && !it.status.isHidden }
        val shownSlotIds = shown.mapNotNull { it.pathSlotId }.toSet()
        val room = (PathScheduler.MAX_PATH_QUESTS_PER_DAY - shown.size).coerceAtLeast(0)

        val paths = pathDao.all().map { it.toPath() }
        val pending = slots.filter { it.outcome == SlotOutcome.PENDING && it.id !in shownSlotIds }
        val plan = PathScheduler.planDay(paths, pending, gameDate)
        val offered = plan.today.take(room)
        val overflow = plan.today.drop(room).map { slot ->
            val path = paths.first { it.id == slot.pathId }
            slot.copy(assignedDate = PathScheduler.nextActiveDay(path, gameDate))
        } + plan.moved
        overflow.forEach { pathDao.upsertSlot(it.toEntity()) }
        return offered.map { PathScheduler.questDraft(it, gameDate) }
    }
}
