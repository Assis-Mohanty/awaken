package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.engine.model.TimeOfDay
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.HabitProblem
import com.assismohanty.awaken.engine.HabitRules
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** User input for a habit. [days] are the scheduled days of the week. */
data class HabitDraft(
    val id: String?,
    val title: String,
    val stat: Stat,
    val difficulty: Difficulty,
    val days: Set<DayOfWeek>,
    val target: Int,
    val unit: String,
    /** GD §14.5: the block this habit shows in on Today; null = Anytime. */
    val timeOfDay: TimeOfDay? = null,
    /** GD §16.1. A Timed habit's [target] is its minutes and its [difficulty] is ignored (it comes from the length). */
    val type: HabitType = HabitType.CHECK,
    val frequency: HabitFrequency = HabitFrequency.DAYS_OF_WEEK,
    /** 1..6 with [HabitFrequency.TIMES_PER_WEEK]. */
    val timesPerWeek: Int? = null,
    /** GD §16.2: minutes after local midnight; null = no reminder. */
    val reminderMinutes: Int? = null,
)

sealed interface SaveHabitResult {
    data class Saved(val habitId: String) : SaveHabitResult
    data object CampaignHabit : SaveHabitResult
    data object LimitReached : SaveHabitResult
    data object InvalidTitle : SaveHabitResult
    data object InvalidDays : SaveHabitResult
    data object InvalidTarget : SaveHabitResult
    data object DifficultyTooHigh : SaveHabitResult

    /** The type, frequency or target breaks a GD §16.1 rule. */
    data class InvalidHabit(val problem: HabitProblem) : SaveHabitResult

    /** Two other habits already have a reminder (GD §16.2). */
    data object ReminderLimit : SaveHabitResult
}

/** Bit (dayOfWeek.value − 1) per scheduled day; Monday = bit 0 (matches GenerateDirectiveUseCase). */
fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

/**
 * Custom habits (GD §4.3, §6.1): max 8 active; self-reported habits max Normal. Habits are SELF-verified in this build —
 * TIMER habits arrive with Focus Gates (Phase 8) and HEALTH habits with Health Connect (Phase 11).
 */
class SaveHabitUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val questDao: QuestDao,
    private val generateDirective: GenerateDirectiveUseCase,
    private val reminders: HabitReminderScheduling,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: HabitDraft): SaveHabitResult {
        val title = draft.title.trim()
        val verification = HabitRules.verificationFor(draft.type)
        // Avoid habits are one yes/no a day; a Timed habit's difficulty comes from its length (GD §16.1).
        val target = if (draft.type == HabitType.NEGATIVE) 1 else draft.target
        val difficulty = if (draft.type == HabitType.TIMED) HabitRules.timedDifficulty(target) else draft.difficulty
        val unit = when (draft.type) {
            HabitType.TIMED -> TIMED_UNIT
            HabitType.NEGATIVE -> ""
            else -> draft.unit
        }
        val flexible = draft.frequency == HabitFrequency.TIMES_PER_WEEK
        when {
            title.isEmpty() -> return SaveHabitResult.InvalidTitle
            !flexible && draft.days.isEmpty() -> return SaveHabitResult.InvalidDays
            target < 1 -> return SaveHabitResult.InvalidTarget
            difficulty > QuestRules.maxCustomDifficulty(verification.source) -> return SaveHabitResult.DifficultyTooHigh
        }
        HabitRules.validate(draft.type, draft.frequency, draft.timesPerWeek, target)?.let { return SaveHabitResult.InvalidHabit(it) }
        if (draft.reminderMinutes != null && !HabitRules.isValidReminder(draft.reminderMinutes)) return SaveHabitResult.InvalidTarget
        val result = transactions.run {
            val existing = draft.id?.let { habitDao.get(it) }
            // Campaign habits are planned and priced by the engine; they can be archived, not edited (ENGINE_DECISIONS #92).
            if (existing?.campaignId != null) return@run SaveHabitResult.CampaignHabit
            if (existing == null && !QuestRules.canAddCustom(habitDao.countActive())) return@run SaveHabitResult.LimitReached
            val id = existing?.id ?: UUID.randomUUID().toString()
            if (draft.reminderMinutes != null && !HabitRules.canSetReminder(habitDao.countRemindersExcept(id))) {
                return@run SaveHabitResult.ReminderLimit
            }
            val habit = HabitEntity(
                id = id,
                title = title,
                stat = draft.stat,
                difficulty = difficulty,
                verification = verification,
                // A flexible habit is due any day: all seven bits, so nothing else needs to special-case it.
                daysOfWeekMask = if (flexible) ALL_DAYS_MASK else draft.days.toMask(),
                target = target,
                unit = unit,
                active = true,
                createdAt = existing?.createdAt ?: clock.now(),
                timeOfDay = draft.timeOfDay,
                type = draft.type,
                frequency = draft.frequency,
                timesPerWeek = if (flexible) draft.timesPerWeek else null,
                reminderTime = draft.reminderMinutes,
            )
            habitDao.upsert(habit)
            hunterDao.getProfile()?.let { profile ->
                questDao.updateOpenHabitInstance(
                    habit.id, clock.gameDate(profile.dayStartHour), habit.title, habit.stat, habit.difficulty, habit.target, habit.unit,
                )
            }
            SaveHabitResult.Saved(habit.id)
        }
        // A new habit scheduled today joins today's directive straight away (deterministic id, so no duplicates).
        if (result is SaveHabitResult.Saved) {
            hunterDao.getProfile()?.let { generateDirective(clock.gameDate(it.dayStartHour)) }
            reminders.rearm()
        }
        return result
    }

    private companion object {
        const val TIMED_UNIT = "min"
        const val ALL_DAYS_MASK = 0b1111111
    }
}

/** Arms or cancels the per-habit reminders (GD §16.2). The WorkManager implementation lives in `core/work`. */
interface HabitReminderScheduling {
    /** Re-reads every habit with a reminder and arms exactly those; cancels the rest. */
    suspend fun rearm()
}

/** What an archive or delete took away, so Undo can put back exactly that. */
data class HabitRemoval(val habit: HabitEntity, val removedInstances: List<QuestInstanceEntity>, val deleted: Boolean)

sealed interface RemoveHabitResult {
    data class Removed(val removal: HabitRemoval) : RemoveHabitResult
    data object NotFound : RemoveHabitResult

    /** Campaign habits end with their campaign (GD §6.8). */
    data object CampaignHabit : RemoveHabitResult
}

/**
 * Archive or delete a custom habit, in one transaction. Both remove its instances from today on that were never started
 * or finished (so it leaves Today now; a day is cleared by completions, so this can't help clear one, GD §7.1). Archive
 * keeps the row inactive and restorable from Settings; delete removes the row. Past instances, completions and every
 * ledger row stay, so XP, stats and history don't change (invariant 1).
 */
class RemoveHabitUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val questDao: QuestDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun archive(habitId: String): RemoveHabitResult = remove(habitId, delete = false)

    suspend fun delete(habitId: String): RemoveHabitResult = remove(habitId, delete = true)

    private suspend fun remove(habitId: String, delete: Boolean): RemoveHabitResult {
        val profile = hunterDao.getProfile() ?: return RemoveHabitResult.NotFound
        val today = clock.gameDate(profile.dayStartHour)
        return transactions.run {
            val habit = habitDao.get(habitId) ?: return@run RemoveHabitResult.NotFound
            if (habit.campaignId != null) return@run RemoveHabitResult.CampaignHabit
            val open = questDao.openHabitInstancesFrom(habitId, today)
            questDao.deleteAll(open.map { it.id })
            if (delete) habitDao.delete(habitId) else habitDao.upsert(habit.copy(active = false))
            RemoveHabitResult.Removed(HabitRemoval(habit, open, delete))
        }
    }
}

sealed interface RestoreHabitResult {
    data object Restored : RestoreHabitResult
    data object LimitReached : RestoreHabitResult
    data object NotFound : RestoreHabitResult
}

/** Undo of an archive or delete, and restoring an archived habit from Settings. */
class RestoreHabitUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val questDao: QuestDao,
    private val generateDirective: GenerateDirectiveUseCase,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Puts back the habit row and the instances [removal] took, as they were (seconds ago, so within the limit). */
    suspend fun undo(removal: HabitRemoval) {
        transactions.run {
            habitDao.upsert(removal.habit)
            questDao.insertAll(removal.removedInstances)
        }
    }

    /** An archived habit becomes active again (max 8, GD §6.1) and joins today's directive if scheduled today. */
    suspend operator fun invoke(habitId: String): RestoreHabitResult {
        val result = transactions.run {
            val habit = habitDao.get(habitId) ?: return@run RestoreHabitResult.NotFound
            if (habit.active) return@run RestoreHabitResult.Restored
            if (!QuestRules.canAddCustom(habitDao.countActive())) return@run RestoreHabitResult.LimitReached
            habitDao.upsert(habit.copy(active = true))
            RestoreHabitResult.Restored
        }
        if (result == RestoreHabitResult.Restored) hunterDao.getProfile()?.let { generateDirective(clock.gameDate(it.dayStartHour)) }
        return result
    }
}

class ObserveHabitsUseCase @Inject constructor(private val habitDao: HabitDao) {
    operator fun invoke(): Flow<List<HabitEntity>> = habitDao.observeActive()

    fun archived(): Flow<List<HabitEntity>> = habitDao.observeArchived()
}

class GetHabitUseCase @Inject constructor(private val habitDao: HabitDao) {
    suspend operator fun invoke(habitId: String): HabitEntity? = habitDao.get(habitId)
}
