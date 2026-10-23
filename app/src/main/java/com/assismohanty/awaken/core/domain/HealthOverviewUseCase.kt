package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthReadException
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthRules
import com.assismohanty.awaken.engine.HealthWindow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

/** One bar in the 7-day trend: the totals for that game-day, after the trust rules. */
data class HealthTrendDay(val date: LocalDate, val steps: Long, val distanceMeters: Double, val activeKcal: Double)

/** What the Health screen shows. [today] has already been through [HealthRules.trusted]. */
data class HealthOverview(
    val today: HealthDay,
    /** Oldest first, ending with today. */
    val trend: List<HealthTrendDay>,
    val updatedAt: Instant,
    val canDistance: Boolean,
    val canCalories: Boolean,
    val canHeartRate: Boolean,
)

sealed interface HealthOverviewResult {
    data class Ready(val overview: HealthOverview) : HealthOverviewResult

    /** Verified quests are off: nothing is read. */
    data object Disabled : HealthOverviewResult

    /** Health Connect is missing, needs an update, or a core permission is gone. */
    data object Unavailable : HealthOverviewResult

    data object ReadFailed : HealthOverviewResult
}

/** Reads the last [TREND_DAYS] game-days for the Health screen (display only: no quest, ledger or setting changes). */
class LoadHealthOverviewUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val gateway: HealthGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke(): HealthOverviewResult {
        val profile = hunterDao.getProfile()
        if (profile == null || !profile.healthEnabled) return HealthOverviewResult.Disabled
        val granted = gateway.grantedPermissions()
        if (gateway.status() != HealthStatus.AVAILABLE || !granted.containsAll(gateway.requiredPermissions)) {
            return HealthOverviewResult.Unavailable
        }
        val zone = clock.zone()
        val now = clock.now()
        val today = clock.gameDate(profile.dayStartHour)
        return try {
            val trend = (TREND_DAYS - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val end = if (back == 0) now else GameDay.endOf(date, zone, profile.dayStartHour)
                val day = HealthRules.trusted(gateway.readDay(HealthWindow(GameDay.startOf(date, zone, profile.dayStartHour), end)))
                date to day
            }
            val todayDay = trend.last().second
            HealthOverviewResult.Ready(
                HealthOverview(
                    today = todayDay,
                    trend = trend.map { (date, day) -> HealthTrendDay(date, day.steps, day.distanceMeters, day.activeKcal) },
                    updatedAt = now,
                    canDistance = gateway.distancePermission in granted,
                    canCalories = gateway.caloriesPermission in granted,
                    canHeartRate = gateway.heartRatePermission in granted,
                ),
            )
        } catch (e: HealthReadException) {
            Timber.w(e, "Health overview read failed")
            HealthOverviewResult.ReadFailed
        }
    }

    companion object {
        const val TREND_DAYS = 7
    }
}
