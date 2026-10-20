package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Stat
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/*
 * Wire format of the Path Oracle (the JSON Schemas in /contracts; GD §6.6.2-6.6.3). These DTOs only describe the JSON; the
 * app sends requests built by [PathWire] and runs every response through [PathValidator], never through these classes
 * straight into Room (CLAUDE.md invariant 5).
 */

/** The Path as the model sees it: no XP, level, gold or streak (GD §6.6.2). */
@Serializable
data class PathDto(
    val goal: String,
    val currentLevel: String,
    val notes: String,
    val primaryStat: Stat,
    val secondaryStat: Stat? = null,
    /** `MON`..`SUN`, Monday first. */
    val activeDays: List<String>,
    val questsPerDay: Int,
    val weeklyMinutes: Int,
)

/** What the prompt must respect: owned equipment ids and Injury Mode tags (ARCH §3.8 prompt rules). */
@Serializable
data class ConstraintsDto(val equipment: List<String>, val injuryTags: List<String>)

@Serializable
data class MilestoneDto(val title: String, val summary: String, val exitCriterion: String)

/** One of last week's Path quests (GD §6.6.2 "last week's per-quest outcomes"). */
@Serializable
data class LastWeekQuestDto(val title: String, val outcome: String, val feedback: String? = null)

@Serializable
data class PathPlanRequest(val requestId: String, val path: PathDto, val constraints: ConstraintsDto)

@Serializable
data class PathWeekRequest(
    val requestId: String,
    val path: PathDto,
    val milestone: MilestoneDto,
    val progressionSignal: ProgressionSignal,
    /** n = activeDays × questsPerDay (V9). */
    val maxBlueprints: Int,
    val lastWeek: List<LastWeekQuestDto>,
    val constraints: ConstraintsDto,
)

/** A blueprint as the model returns it (GD §6.6.3): enums stay strings so an unknown value reaches V1, not a crash. */
@Serializable
data class Blueprint(
    val title: String,
    val description: String,
    val stat: String,
    val unit: String,
    val target: Int,
    val effort: String,
    val estMinutes: Int,
    val mode: String,
    val tags: List<String> = emptyList(),
    val rationale: String = "",
)

@Serializable
data class PathPlanResponse(val promptVersion: String, val milestones: List<MilestoneDto>)

@Serializable
data class PathWeekResponse(
    val promptVersion: String,
    val blueprints: List<Blueprint>,
    val suggestAdvance: Boolean = false,
)

/** Builds requests from engine models, trimming user text to the GD §6.6.1 limits (CLAUDE.md invariant 9). */
object PathWire {
    const val LAST_WEEK_MAX = 14

    fun pathDto(path: Path): PathDto = PathDto(
        goal = path.goal.trim().take(PathRules.GOAL_LENGTH.last),
        currentLevel = path.currentLevel.trim().take(PathRules.LEVEL_LENGTH.last),
        notes = path.notes.trim().take(PathRules.NOTES_LENGTH.last),
        primaryStat = path.primaryStat,
        secondaryStat = path.secondaryStat,
        activeDays = path.activeDays.sorted().map(::dayCode),
        questsPerDay = path.questsPerDay,
        weeklyMinutes = path.weeklyMinutes,
    )

    fun constraints(ownedEquipment: Set<String>, injuryTags: Set<String>): ConstraintsDto =
        ConstraintsDto(ownedEquipment.sorted(), injuryTags.sorted())

    fun planRequest(requestId: String, path: Path, constraints: ConstraintsDto): PathPlanRequest =
        PathPlanRequest(requestId, pathDto(path), constraints)

    fun weekRequest(
        requestId: String,
        path: Path,
        milestone: Milestone,
        signal: ProgressionSignal,
        lastWeek: List<WeekOutcome>,
        constraints: ConstraintsDto,
    ): PathWeekRequest = PathWeekRequest(
        requestId = requestId,
        path = pathDto(path),
        milestone = MilestoneDto(milestone.title, milestone.summary, milestone.exitCriterion),
        progressionSignal = signal,
        maxBlueprints = path.weeklyQuestLimit,
        lastWeek = lastWeek.take(LAST_WEEK_MAX).map {
            LastWeekQuestDto(PathText.clean(it.title, PathText.TITLE_MAX), it.outcome.name, it.feedback?.name)
        },
        constraints = constraints,
    )

    fun dayCode(day: DayOfWeek): String = day.name.take(3)
}
