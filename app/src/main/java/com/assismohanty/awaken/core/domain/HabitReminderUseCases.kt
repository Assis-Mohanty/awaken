package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import javax.inject.Inject

/** What a habit reminder says (GD §16.2): which habit, by its own title. */
data class HabitReminderContent(val habitId: String, val title: String)

/**
 * Decides, at fire time, whether a habit's reminder is still worth posting: only while today's instance is open. A
 * habit that is already done, skipped, missed or expired, or that no longer has a reminder, stays quiet (GD §16.2).
 */
class BuildHabitReminderUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val questDao: QuestDao,
    private val runRollover: RunRolloverUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(habitId: String): HabitReminderContent? {
        // Today's instance exists even if the app was never opened today (the same rule as the directive reminder).
        runRollover()
        val profile = hunterDao.getProfile() ?: return null
        val habit = habitDao.get(habitId)?.takeIf { it.active && it.reminderTime != null } ?: return null
        val instance = questDao.habitInstanceOn(habitId, clock.gameDate(profile.dayStartHour)) ?: return null
        return if (instance.isOpen) HabitReminderContent(habit.id, habit.title) else null
    }
}
