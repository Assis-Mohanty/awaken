package com.assismohanty.awaken.core.work

import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.domain.HabitReminderScheduling
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the reminders armed for the current profile. One place covers app start, onboarding, a Settings edit and an
 * import: whenever the reminder time or the day start changes (or a hunter first appears), both are re-armed.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val hunterDao: HunterDao,
    private val scheduler: ReminderScheduler,
    private val habitReminders: HabitReminderScheduling,
    private val dietReminders: com.assismohanty.awaken.core.domain.DietReminderScheduling,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        // Habit reminders (GD §16.2) are re-armed on every start, so one lost to an update, an import or a reboot returns.
        scope.launch {
            try {
                habitReminders.rearm()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Scheduling habit reminders failed")
            }
        }
        // Meal reminders and the end of a running fast come back after an update or a reboot the same way.
        scope.launch {
            try {
                dietReminders.rearmMeals()
                dietReminders.rearmFast()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Scheduling diet reminders failed")
            }
        }
        scope.launch {
            hunterDao.observeProfile()
                .map { profile -> profile?.let { it.reminderTime to it.dayStartHour } }
                .distinctUntilChanged()
                .collect {
                    try {
                        scheduler.reschedule()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Scheduling reminders failed")
                    }
                }
        }
    }
}
