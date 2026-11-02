package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.usage.EventsRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.PickupRules
import com.assismohanty.awaken.engine.PickupSummary
import javax.inject.Inject

/** Today's phone habit, or why there is none: a missing permission or an unreadable log is never "zero pickups" (GD §16.10). */
sealed interface PickupsState {
    data object NoAccess : PickupsState
    data object Unavailable : PickupsState
    data class Data(val summary: PickupSummary) : PickupsState
}

/** Pickups, first pickup, last use yesterday and the longest stretch. Read-only; display only, no reward (GD §16.10). */
class LoadPickupsUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val usage: UsageGateway,
    private val clock: Clock,
) {
    /** Null when there is no hunter. */
    suspend operator fun invoke(): PickupsState? {
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val start = GameDay.startOf(today, clock.zone(), profile.dayStartHour)
        val todayEvents = when (val read = usage.events(start, clock.now())) {
            EventsRead.NoAccess -> return PickupsState.NoAccess
            EventsRead.Unavailable -> return PickupsState.Unavailable
            is EventsRead.Tracked -> read.events
        }
        val yesterdayStart = GameDay.startOf(today.minusDays(1), clock.zone(), profile.dayStartHour)
        // Yesterday is only for "last use": if it cannot be read, that one line is missing rather than the whole card.
        val yesterdayEvents = (usage.events(yesterdayStart, start) as? EventsRead.Tracked)?.events.orEmpty()
        return PickupsState.Data(PickupRules.summarize(todayEvents, yesterdayEvents))
    }
}
