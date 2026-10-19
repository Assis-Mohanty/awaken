package com.assismohanty.awaken.core.work

import android.content.Context
import androidx.work.WorkManager
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Re-arms the background day-start job from the profile's current `dayStartHour` (after Settings or an import). */
@Singleton
class DayStartScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) {
    suspend fun reschedule() = DayRolloverWorker.schedule(WorkManager.getInstance(context), hunterDao, clock)
}
