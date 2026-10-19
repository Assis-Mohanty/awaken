package com.assismohanty.awaken.core.work

import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
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
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
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
