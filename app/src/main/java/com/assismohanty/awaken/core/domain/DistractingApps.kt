package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.ScreenTimeRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * The hunter's Distracting apps: every package whose Screen Time category in force today is Distracting (GD 8.2).
 * Focus Lock's fallback reuses these categories instead of keeping a second list.
 */
@Singleton
class DistractingApps @Inject constructor(
    private val screenTimeDao: ScreenTimeDao,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) {
    fun observe(): Flow<Set<String>> = combine(screenTimeDao.observeRules(), hunterDao.observeProfile()) { rules, profile ->
        val today = clock.gameDate(profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)
        ScreenTimeRules.rulesFor(today, rules.map { it.toRule() }).filterValues { it.category == AppCategory.DISTRACTING }.keys.toSet()
    }.distinctUntilChanged()

    suspend fun current(): Set<String> = observe().first()
}
