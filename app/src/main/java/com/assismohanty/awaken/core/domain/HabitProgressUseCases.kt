package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.engine.HabitRules
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What a habit action did (GD §16.1-16.2). */
sealed interface HabitActionResult {
    /** A count habit moved to [progress] and is still open. */
    data class Progressed(val progress: Int) : HabitActionResult

    /** The action completed the quest; the reward sheet shows [outcome]. */
    data class Completed(val outcome: CompletionOutcome) : HabitActionResult

    /** Done, nothing more to show (a skip, a slip, an undo). */
    data object Done : HabitActionResult

    /** The habit or quest is of another type, another day, already closed, or the rule forbids it right now. */
    data object NotAllowed : HabitActionResult

    data object NotFound : HabitActionResult
}

/** One habit's instance for today, with its habit, or null when the id is not a habit quest of the current game-day. */
private data class TodayHabit(val quest: QuestInstanceEntity, val habit: HabitEntity)

private val OPEN = listOf(QuestStatus.ACTIVE, QuestStatus.STARTED)

private val QuestInstanceEntity.isOpenNow: Boolean get() = status == QuestStatus.ACTIVE || status == QuestStatus.STARTED

/** Loads today's habit quest. All the habit actions share these checks, so none can act on a past day. */
private class TodayHabits(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val habitDao: HabitDao,
    private val clock: Clock,
) {
    suspend fun find(questInstanceId: String): TodayHabit? {
        val profile = hunterDao.getProfile() ?: return null
        val quest = questDao.get(questInstanceId)?.takeIf { it.type == QuestType.HABIT } ?: return null
        if (quest.gameDate != clock.gameDate(profile.dayStartHour)) return null
        val habit = quest.habitId?.let { habitDao.get(it) } ?: return null
        return TodayHabit(quest, habit)
    }
}

/**
 * The +1 / −1 of a count habit (GD §16.1). Progress is stored on the day's instance; reaching the target completes the
 * quest through the economy pipeline (SELF, so the SELF cap applies), and −1 on a completed one is an ordinary Undo
 * (10 minutes, same game-day). Partial progress pays nothing.
 */
class AdjustQuantityUseCase @Inject constructor(
    hunterDao: HunterDao,
    private val questDao: QuestDao,
    habitDao: HabitDao,
    private val completeQuest: CompleteQuestUseCase,
    private val undoCompletion: UndoCompletionUseCase,
    private val transactions: TransactionRunner,
    clock: Clock,
) {
    private val today = TodayHabits(hunterDao, questDao, habitDao, clock)

    suspend fun plus(questInstanceId: String): HabitActionResult = transactions.run {
        val (quest, habit) = today.find(questInstanceId) ?: return@run HabitActionResult.NotFound
        if (habit.type != HabitType.QUANTITY || !quest.isOpenNow) return@run HabitActionResult.NotAllowed
        val next = HabitRules.clampProgress(quest.progress + 1, quest.target)
        questDao.updateProgress(quest.id, next)
        if (!HabitRules.quantityComplete(next, quest.target)) return@run HabitActionResult.Progressed(next)
        when (val result = completeQuest.completeHabit(quest.id)) {
            is CompleteQuestResult.Completed -> HabitActionResult.Completed(result.outcome)
            else -> {
                // The completion was refused (e.g. the day closed): keep the count where it was.
                questDao.updateProgress(quest.id, quest.progress)
                HabitActionResult.NotAllowed
            }
        }
    }

    suspend fun minus(questInstanceId: String): HabitActionResult = transactions.run {
        val (quest, habit) = today.find(questInstanceId) ?: return@run HabitActionResult.NotFound
        if (habit.type != HabitType.QUANTITY) return@run HabitActionResult.NotAllowed
        when {
            quest.status == QuestStatus.COMPLETED -> when (undoCompletion(quest.id)) {
                is UndoResult.Undone -> HabitActionResult.Progressed((quest.target - 1).coerceAtLeast(0))
                else -> HabitActionResult.NotAllowed
            }
            quest.isOpenNow && quest.progress > 0 -> {
                questDao.updateProgress(quest.id, quest.progress - 1)
                HabitActionResult.Progressed(quest.progress - 1)
            }
            else -> HabitActionResult.NotAllowed
        }
    }
}

/**
 * Avoid habits (GD §16.1): **Kept it today** is open from the start of the Evening block until the game-day ends and
 * completes the quest; **I slipped** marks it missed at once and can be taken back until the day ends. An instance nobody
 * confirmed is MISSED at rollover and pays nothing, so no XP is ever paid for a day the app did not see.
 */
class AvoidHabitUseCases @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    habitDao: HabitDao,
    private val completeQuest: CompleteQuestUseCase,
    private val uiPrefs: UiPrefsRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    private val today = TodayHabits(hunterDao, questDao, habitDao, clock)

    suspend fun keptToday(questInstanceId: String): HabitActionResult = transactions.run {
        val (quest, habit) = today.find(questInstanceId) ?: return@run HabitActionResult.NotFound
        if (habit.type != HabitType.NEGATIVE || !quest.isOpenNow) return@run HabitActionResult.NotAllowed
        val profile = hunterDao.getProfile() ?: return@run HabitActionResult.NotFound
        val bounds = uiPrefs.prefs.first().blockBounds
        val now = clock.now().atZone(clock.zone()).toLocalTime()
        if (!HabitRules.canConfirmKept(now, profile.dayStartHour, bounds)) return@run HabitActionResult.NotAllowed
        when (val result = completeQuest.completeHabit(quest.id)) {
            is CompleteQuestResult.Completed -> HabitActionResult.Completed(result.outcome)
            else -> HabitActionResult.NotAllowed
        }
    }

    suspend fun slipped(questInstanceId: String): HabitActionResult = transactions.run {
        val (quest, habit) = today.find(questInstanceId) ?: return@run HabitActionResult.NotFound
        if (habit.type != HabitType.NEGATIVE) return@run HabitActionResult.NotAllowed
        if (questDao.moveStatus(quest.id, OPEN, QuestStatus.MISSED) > 0) HabitActionResult.Done else HabitActionResult.NotAllowed
    }

    /** Takes a slip back: only today's, so a missed day that already closed stays missed. */
    suspend fun undoSlip(questInstanceId: String): HabitActionResult = transactions.run {
        val (quest, habit) = today.find(questInstanceId) ?: return@run HabitActionResult.NotFound
        if (habit.type != HabitType.NEGATIVE) return@run HabitActionResult.NotAllowed
        if (questDao.moveStatus(quest.id, listOf(QuestStatus.MISSED), QuestStatus.ACTIVE) > 0) HabitActionResult.Done else HabitActionResult.NotAllowed
    }
}

/** What the options sheet can offer for today's instance of a habit (GD §16.2). */
enum class SkipState {
    /** No open instance today (done, missed, expired, not scheduled, or no hunter). */
    NONE,

    /** Open, and this week's skip is unused. */
    AVAILABLE,

    /** Open, but this week's skip was already used. */
    NO_SKIPS_LEFT,

    /** Today's instance is skipped: it can be taken back. */
    SKIPPED,

    /** A times-per-week habit: every day is already optional, so there is nothing to skip. */
    NOT_NEEDED,
}

/**
 * Skip today (GD §16.2): one skip per fixed-day habit per ISO week. A skipped instance is not a miss, not a directive
 * item and never paid; taking the skip back gives the allowance back. Flexible habits cannot be skipped.
 */
class SkipHabitUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val habitDao: HabitDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    private suspend fun todayInstance(habitId: String): Pair<QuestInstanceEntity, HabitEntity>? {
        val profile = hunterDao.getProfile() ?: return null
        val habit = habitDao.get(habitId) ?: return null
        val quest = questDao.habitInstanceOn(habitId, clock.gameDate(profile.dayStartHour)) ?: return null
        return quest to habit
    }

    suspend fun state(habitId: String): SkipState {
        val (quest, habit) = todayInstance(habitId) ?: return SkipState.NONE
        return when {
            quest.status == QuestStatus.SKIPPED -> SkipState.SKIPPED
            !quest.isOpenNow -> SkipState.NONE
            habit.frequency != HabitFrequency.DAYS_OF_WEEK -> SkipState.NOT_NEEDED
            usedThisWeek(habit.id, quest) < HabitRules.SKIPS_PER_WEEK -> SkipState.AVAILABLE
            else -> SkipState.NO_SKIPS_LEFT
        }
    }

    suspend fun skip(habitId: String): HabitActionResult = transactions.run {
        val (quest, habit) = todayInstance(habitId) ?: return@run HabitActionResult.NotFound
        if (!quest.isOpenNow || !HabitRules.canSkip(habit.frequency, usedThisWeek(habit.id, quest))) return@run HabitActionResult.NotAllowed
        if (questDao.moveStatus(quest.id, OPEN, QuestStatus.SKIPPED) > 0) HabitActionResult.Done else HabitActionResult.NotAllowed
    }

    suspend fun undoSkip(habitId: String): HabitActionResult = transactions.run {
        val (quest, _) = todayInstance(habitId) ?: return@run HabitActionResult.NotFound
        if (questDao.moveStatus(quest.id, listOf(QuestStatus.SKIPPED), QuestStatus.ACTIVE) > 0) HabitActionResult.Done else HabitActionResult.NotAllowed
    }

    private suspend fun usedThisWeek(habitId: String, quest: QuestInstanceEntity): Int =
        questDao.habitSkipsBetween(habitId, HabitRules.weekStart(quest.gameDate), HabitRules.weekEnd(quest.gameDate))
}
