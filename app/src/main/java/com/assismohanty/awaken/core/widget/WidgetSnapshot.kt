package com.assismohanty.awaken.core.widget

import com.assismohanty.awaken.core.domain.TodayModel
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank

/**
 * Everything the Today widget shows (ARCH §3.6), as plain data: the widget draws from this and nothing else, and
 * [WidgetUpdater] re-renders it only when the snapshot changes.
 *
 * @param completed / @param total the same count Today shows ("3/5"): main, sides and habits.
 * @param nextQuest title of the next thing to do (penalty first, then main, sides, habits, trial stages); null when
 *   everything is done.
 */
data class WidgetSnapshot(
    val hasHunter: Boolean = false,
    val rank: Rank = Rank.E,
    val level: Int = 1,
    val streak: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val nextQuest: String? = null,
    val cleared: Boolean = false,
)

private val NEXT_ORDER = listOf(QuestType.PENALTY, QuestType.MAIN, QuestType.SIDE, QuestType.HABIT, QuestType.TRIAL)

/** Null means no hunter yet: the widget shows its "open AWAKEN" state. */
fun TodayModel?.toWidgetSnapshot(): WidgetSnapshot {
    if (this == null) return WidgetSnapshot()
    val counted = quests.filter { it.type == QuestType.MAIN || it.type == QuestType.SIDE || it.type == QuestType.HABIT }
    val open = quests.filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.STARTED }
    val next = NEXT_ORDER.firstNotNullOfOrNull { type -> open.firstOrNull { it.type == type } }
    return WidgetSnapshot(
        hasHunter = true,
        rank = hunter.rank,
        level = hunter.level,
        streak = hunter.streak,
        completed = counted.count { it.status == QuestStatus.COMPLETED },
        total = counted.size,
        nextQuest = next?.title,
        cleared = hunter.todayCleared,
    )
}
