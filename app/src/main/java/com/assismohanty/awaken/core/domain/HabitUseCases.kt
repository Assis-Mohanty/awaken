package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
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
)

sealed interface SaveHabitResult {
    data class Saved(val habitId: String) : SaveHabitResult
    data object LimitReached : SaveHabitResult
    data object InvalidTitle : SaveHabitResult
    data object InvalidDays : SaveHabitResult
    data object InvalidTarget : SaveHabitResult
    data object DifficultyTooHigh : SaveHabitResult
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
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: HabitDraft): SaveHabitResult {
        val title = draft.title.trim()
        when {
            title.isEmpty() -> return SaveHabitResult.InvalidTitle
            draft.days.isEmpty() -> return SaveHabitResult.InvalidDays
            draft.target < 1 -> return SaveHabitResult.InvalidTarget
            draft.difficulty > QuestRules.maxCustomDifficulty(Verification.SELF.source) -> return SaveHabitResult.DifficultyTooHigh
        }
        val result = transactions.run {
            val existing = draft.id?.let { habitDao.get(it) }
            if (existing == null && !QuestRules.canAddCustom(habitDao.countActive())) return@run SaveHabitResult.LimitReached
            val habit = HabitEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = title,
                stat = draft.stat,
                difficulty = draft.difficulty,
                verification = Verification.SELF,
                daysOfWeekMask = draft.days.toMask(),
                target = draft.target,
                unit = draft.unit,
                active = true,
                createdAt = existing?.createdAt ?: clock.now(),
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
        }
        return result
    }
}

/** Archiving stops future instances; today's instance, if any, stays so the day's record is unchanged. */
class ArchiveHabitUseCase @Inject constructor(private val habitDao: HabitDao) {
    suspend operator fun invoke(habitId: String) {
        habitDao.get(habitId)?.let { habitDao.upsert(it.copy(active = false)) }
    }
}

class ObserveHabitsUseCase @Inject constructor(private val habitDao: HabitDao) {
    operator fun invoke(): Flow<List<HabitEntity>> = habitDao.observeActive()
}

class GetHabitUseCase @Inject constructor(private val habitDao: HabitDao) {
    suspend operator fun invoke(habitId: String): HabitEntity? = habitDao.get(habitId)
}
