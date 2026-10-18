package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** Creates the hunter profile and baseline tiers. Used by onboarding (Phase 6) and the debug seeder. */
class CreateHunterUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        name: String,
        focusStats: List<Stat>,
        baselineTiers: Map<Stat, Difficulty> = emptyMap(),
        dayStartHour: Int = GameDay.DEFAULT_DAY_START_HOUR,
        reminderTime: LocalTime? = null,
    ): HunterProfileEntity {
        require(focusStats.size == 2 && focusStats[0] != focusStats[1]) { "exactly two distinct focus stats required" }
        val profile = HunterProfileEntity(
            hunterId = UUID.randomUUID().toString(),
            name = name,
            createdAt = clock.now(),
            firstGameDate = clock.gameDate(dayStartHour),
            focusStat1 = focusStats[0],
            focusStat2 = focusStats[1],
            dayStartHour = dayStartHour,
            onboardingDone = true,
            reminderTime = reminderTime?.toString(),
        )
        transactions.run {
            hunterDao.upsertProfile(profile)
            hunterDao.upsertTiers(Stat.entries.map { StatTierEntity(it, baselineTiers[it] ?: Difficulty.NORMAL) })
        }
        return profile
    }
}
