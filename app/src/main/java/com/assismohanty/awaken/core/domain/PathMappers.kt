package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathMilestoneEntity
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.engine.path.Milestone
import com.assismohanty.awaken.engine.path.Path
import com.assismohanty.awaken.engine.path.PathSlot
import com.assismohanty.awaken.engine.path.PricedBlueprint
import com.assismohanty.awaken.engine.path.ValidatedBlueprint
import java.time.DayOfWeek
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json


internal fun PathEntity.toPath(): Path = Path(
    id = id,
    goal = goal,
    currentLevel = currentLevel,
    notes = notes,
    primaryStat = primaryStat,
    secondaryStat = secondaryStat,
    activeDays = activeDaysMask.toDays(),
    questsPerDay = questsPerDay,
    weeklyMinutes = weeklyMinutes,
    status = status,
    createdAt = createdAt,
)

internal fun PathMilestoneEntity.toMilestone(): Milestone = Milestone(idx, title, summary, exitCriterion)

/**
 * A stored slot as the engine sees it. `path_slot` keeps the priced fields, not the model's `effort` (ARCH §2), so
 * `effort` is rebuilt from the priced difficulty; nothing after pricing reads it.
 */
internal fun PathSlotEntity.toSlot(): PathSlot = PathSlot(
    id = id,
    pathId = pathId,
    isoWeek = isoWeek,
    blueprintIndex = blueprintIndex,
    assignedDate = assignedGameDate,
    carried = carriedOver,
    priced = PricedBlueprint(
        blueprint = ValidatedBlueprint(
            index = blueprintIndex,
            title = title,
            description = description,
            stat = stat,
            unit = unit,
            target = target,
            effort = pricedDifficulty,
            estMinutes = estMinutes,
            mode = mode,
            tags = json.decodeFromString(ListSerializer(String.serializer()), tagsJson).toSet(),
            rationale = rationale,
        ),
        difficulty = pricedDifficulty,
        verification = pricedVerification,
        gateRank = gateRank,
        minDurationSec = minDurationSec,
    ),
    outcome = outcome,
    feedback = feedback,
)

internal fun PathSlot.toEntity(): PathSlotEntity {
    val blueprint = priced.blueprint
    return PathSlotEntity(
        id = id,
        pathId = pathId,
        isoWeek = isoWeek,
        blueprintIndex = blueprintIndex,
        assignedGameDate = assignedDate,
        carriedOver = carried,
        title = blueprint.title,
        description = blueprint.description,
        stat = blueprint.stat,
        unit = blueprint.unit,
        target = blueprint.target,
        estMinutes = blueprint.estMinutes,
        mode = blueprint.mode,
        pricedDifficulty = priced.difficulty,
        pricedVerification = priced.verification,
        gateRank = priced.gateRank,
        minDurationSec = priced.minDurationSec,
        tagsJson = json.encodeToString(ListSerializer(String.serializer()), blueprint.tags.sorted()),
        rationale = blueprint.rationale,
        outcome = outcome,
        feedback = feedback,
    )
}
