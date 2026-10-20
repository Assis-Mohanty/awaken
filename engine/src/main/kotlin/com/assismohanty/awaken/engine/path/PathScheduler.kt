package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

/**
 * Places priced blueprints on game-days and decides what each day shows (GD §6.6.6). Pure: the app stores what this
 * returns; ids are name-based UUIDs, so applying the same week twice yields the same slots (vector 32).
 */
object PathScheduler {
    const val MAX_PATH_QUESTS_PER_DAY = 3

    /** ISO week key, e.g. `2026-W43` (ARCH §2 `path_week.iso_week`). */
    fun isoWeek(date: LocalDate): String =
        String.format(Locale.ROOT, "%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** Slot id = name-based UUID of the scheduling key `(pathId, isoWeek, blueprintIndex)`, unique per ARCH §2. */
    fun slotId(pathId: String, isoWeek: String, blueprintIndex: Int): String =
        UUID.nameUUIDFromBytes("path|$pathId|$isoWeek|$blueprintIndex".encodeToByteArray()).toString()

    /**
     * Round-robin, in blueprint order, over the Path's active days of [today]'s ISO week from [today] onward (a plan
     * that arrives mid-week only fills the days left). Day k of d gets blueprints k, k+d, … up to `questsPerDay` each;
     * blueprints that don't fit stay unscheduled (`assignedDate = null`) as reroll replacements (ENGINE_DECISIONS #65).
     */
    fun assign(priced: List<PricedBlueprint>, path: Path, today: LocalDate): List<PathSlot> {
        val week = isoWeek(today)
        val days = activeDaysFrom(path, today)
        val capacity = days.size * path.questsPerDay
        return priced.mapIndexed { position, blueprint ->
            PathSlot(
                id = slotId(path.id, week, blueprint.blueprint.index),
                pathId = path.id,
                isoWeek = week,
                blueprintIndex = blueprint.blueprint.index,
                assignedDate = if (position < capacity) days[position % days.size] else null,
                carried = false,
                priced = blueprint,
            )
        }
    }

    /** The Path's next active day strictly after [date] in the same ISO week, or null (the week boundary ends it). */
    fun nextActiveDay(path: Path, date: LocalDate): LocalDate? = activeDaysFrom(path, date.plusDays(1))
        .firstOrNull { isoWeek(it) == isoWeek(date) }

    /** What [gameDate] shows: at most 3 Path quests, plus the overflow slots moved to their Path's next active day. */
    data class DayPlan(val today: List<PathSlot>, val moved: List<PathSlot>)

    /**
     * Today's Path slots across all ACTIVE Paths, capped at [MAX_PATH_QUESTS_PER_DAY] in priority order (Path creation
     * time, then blueprint order). Overflow moves to that Path's next active day; with none left this week it becomes
     * unscheduled (ENGINE_DECISIONS #67). Slots of paused or archived Paths are not offered.
     */
    fun planDay(paths: List<Path>, slots: List<PathSlot>, gameDate: LocalDate): DayPlan {
        val active = paths.filter { it.status == PathStatus.ACTIVE }.associateBy { it.id }
        val order = active.values.sortedWith(compareBy<Path>({ it.createdAt }, { it.id })).withIndex()
            .associate { (rank, path) -> path.id to rank }
        val due = slots
            .filter { it.assignedDate == gameDate && it.outcome == SlotOutcome.PENDING && it.pathId in active }
            .sortedWith(compareBy<PathSlot>({ order.getValue(it.pathId) }, { it.blueprintIndex }))
        val moved = due.drop(MAX_PATH_QUESTS_PER_DAY).map { slot ->
            slot.copy(assignedDate = nextActiveDay(active.getValue(slot.pathId), gameDate))
        }
        return DayPlan(due.take(MAX_PATH_QUESTS_PER_DAY), moved)
    }

    /**
     * An offered slot left unfinished when [endedDay] closed (GD §6.6.6; ENGINE_DECISIONS #64): re-offered once on the
     * Path's next active day in the same ISO week; an unfinished re-offer becomes SKIPPED; a first offering with no day
     * left this week becomes EXPIRED. Both count as not completed in the progression signal.
     */
    fun rollover(slot: PathSlot, path: Path, endedDay: LocalDate): PathSlot {
        if (slot.outcome != SlotOutcome.PENDING || slot.assignedDate != endedDay) return slot
        if (slot.carried) return slot.copy(outcome = SlotOutcome.SKIPPED)
        val next = nextActiveDay(path, endedDay) ?: return slot.copy(outcome = SlotOutcome.EXPIRED)
        return slot.copy(assignedDate = next, carried = true)
    }

    /**
     * Reroll of a Path quest (GD §6.6.6): the Path's next unscheduled blueprint of that week, assigned to [gameDate].
     * Null means none is left and the reroll falls back to a template side quest. The caller marks the rerolled slot
     * REROLLED in the same transaction.
     */
    fun rerollPick(slots: List<PathSlot>, rerolled: PathSlot, gameDate: LocalDate): PathSlot? = slots
        .filter {
            it.pathId == rerolled.pathId && it.isoWeek == rerolled.isoWeek &&
                it.assignedDate == null && it.outcome == SlotOutcome.PENDING
        }
        .minByOrNull { it.blueprintIndex }
        ?.copy(assignedDate = gameDate)

    /**
     * The quest a slot becomes on [gameDate] (ARCH §2: priced fields copied, never recomputed). The instance id is per
     * slot and day, so a carried-over re-offer is a new quest instance. Path quests carry no secondary stat
     * (ENGINE_DECISIONS #69).
     */
    fun questDraft(slot: PathSlot, gameDate: LocalDate): QuestDraft {
        val blueprint = slot.priced.blueprint
        return QuestDraft(
            questInstanceId = UUID.nameUUIDFromBytes("${slot.id}|$gameDate".encodeToByteArray()).toString(),
            gameDate = gameDate,
            type = QuestType.PATH,
            templateId = null,
            stat = blueprint.stat,
            secondaryStat = null,
            title = blueprint.title,
            description = blueprint.description,
            unit = blueprint.unit.templateUnit,
            target = blueprint.target,
            difficulty = slot.priced.difficulty,
            verification = slot.priced.verification,
            minDurationSec = slot.priced.minDurationSec,
            pathSlotId = slot.id,
        )
    }

    private fun activeDaysFrom(path: Path, from: LocalDate): List<LocalDate> {
        val monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L until 7L).map { monday.plusDays(it) }.filter { it >= from && it.dayOfWeek in path.activeDays }
    }
}
