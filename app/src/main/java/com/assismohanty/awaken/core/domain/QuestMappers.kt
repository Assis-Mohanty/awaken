package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.engine.DayRecord
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import java.time.Instant
import java.time.LocalDate

internal fun QuestDraft.toEntity(createdAt: Instant): QuestInstanceEntity = QuestInstanceEntity(
    id = questInstanceId,
    gameDate = gameDate,
    type = type,
    templateId = templateId,
    habitId = null,
    stat = stat,
    secondaryStat = secondaryStat,
    title = title,
    description = description,
    unit = unit,
    target = target,
    difficulty = difficulty,
    verification = verification,
    minDurationSec = minDurationSec,
    status = QuestStatus.ACTIVE,
    createdAt = createdAt,
    pathSlotId = pathSlotId,
)

/** Generated quests only (they always have a template). */
internal fun QuestInstanceEntity.toDraft(): QuestDraft? = templateId?.let { templateId ->
    QuestDraft(
        questInstanceId = id,
        gameDate = gameDate,
        type = type,
        templateId = templateId,
        stat = stat,
        secondaryStat = secondaryStat,
        title = title,
        description = description,
        unit = unit,
        target = target,
        difficulty = difficulty,
        verification = verification,
        minDurationSec = minDurationSec,
    )
}

/** GD §7.1 inputs for one day, from its quests. Every completed quest counts as an item (any type). */
internal fun List<QuestInstanceEntity>.toDayRecord(
    gameDate: LocalDate,
    restDay: Boolean = false,
    recovery: Boolean = false,
): DayRecord = DayRecord(
    gameDate = gameDate,
    mainCompleted = any { it.type == QuestType.MAIN && it.status == QuestStatus.COMPLETED },
    itemsCompleted = count { it.status == QuestStatus.COMPLETED },
    restDay = restDay,
    recovery = recovery,
)
