package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthReadException
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.core.data.health.PhoneStepsGateway
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthRules
import com.assismohanty.awaken.engine.HealthWindow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

/** One bar in the 7-day trend: the totals for that game-day, after the trust rules. */
data class HealthTrendDay(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val activeKcal: Double,
    /** Longest trusted sleep that ended in the day (last night's for today); null = none recorded. */
    val sleepMinutes: Int? = null,
)

/** Where the step numbers on the Health screen came from. */
enum class StepSource {
    /** An app writes steps to Health Connect. */
    HEALTH_CONNECT,

    /** No app does, so the phone's own counter (Recording API) fills in. Display only. */
    PHONE,

    /** Debug fixture data ("Simulate 9,000 steps"). */
    SIMULATED,

    /** Nothing records steps: the screen says so and shows no number. */
    NONE,
}

/** What the Health screen shows. [today] has already been through [HealthRules.trusted]. */
data class HealthOverview(
    val today: HealthDay,
    /** Oldest first, ending with today. */
    val trend: List<HealthTrendDay>,
    val updatedAt: Instant,
    val canDistance: Boolean,
    val canCalories: Boolean,
    val canHeartRate: Boolean,
    val stepSource: StepSource = StepSource.HEALTH_CONNECT,
    /** No step source yet, and the phone's own counter could be switched on (Recording API supported, not yet allowed). */
    val canUsePhoneSteps: Boolean = false,
    /** The daily step goal for the Today ring (GD §16.11). */
    val stepGoal: Int = com.assismohanty.awaken.engine.StepGoalRules.DEFAULT_GOAL,
)

sealed interface HealthOverviewResult {
    data class Ready(val overview: HealthOverview) : HealthOverviewResult

    /** Verified quests are off: nothing is read. */
    data object Disabled : HealthOverviewResult

    /** Health Connect is missing, needs an update, or a core permission is gone. */
    data object Unavailable : HealthOverviewResult

    data object ReadFailed : HealthOverviewResult
}

/**
 * Reads the last [TREND_DAYS] game-days for the Health screen, or only today for the Today tile (display only: no quest,
 * ledger or setting changes). The step source is always judged over the full week, so a quiet morning isn't "no source".
 */
class LoadHealthOverviewUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val gateway: HealthGateway,
    private val phoneSteps: PhoneStepsGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke(days: Int = TREND_DAYS): HealthOverviewResult {
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
            val windows = (days.coerceIn(1, TREND_DAYS) - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val end = if (back == 0) now else GameDay.endOf(date, zone, profile.dayStartHour)
                date to HealthWindow(GameDay.startOf(date, zone, profile.dayStartHour), end)
            }
            val read = windows.map { (date, window) -> date to HealthRules.trusted(gateway.readDay(window)) }
            val week = HealthWindow(GameDay.startOf(today.minusDays(TREND_DAYS - 1L), zone, profile.dayStartHour), now)
            val (source, steps) = steps(read.map { it.second.steps }, windows.map { it.second }, week)
            val trend = read.mapIndexed { i, (date, day) -> Triple(date, HealthRules.trusted(day.copy(steps = steps[i])), windows[i].second) }
            HealthOverviewResult.Ready(
                HealthOverview(
                    today = trend.last().second,
                    trend = trend.map { (date, day, window) ->
                        HealthTrendDay(date, day.steps, day.distanceMeters, day.activeKcal, HealthRules.longestSleepMinutes(day, window))
                    },
                    updatedAt = now,
                    canDistance = gateway.distancePermission in granted,
                    canCalories = gateway.caloriesPermission in granted,
                    canHeartRate = gateway.heartRatePermission in granted,
                    stepSource = source,
                    canUsePhoneSteps = source == StepSource.NONE && phoneSteps.isSupported() && !phoneSteps.isPermissionGranted(),
                    stepGoal = profile.stepGoal,
                ),
            )
        } catch (e: HealthReadException) {
            Timber.w(e, "Health overview read failed")
            HealthOverviewResult.ReadFailed
        }
    }

    /**
     * Health Connect steps when any app writes them; else the phone's counter if the hunter allowed it; else nothing (all
     * zero, shown as "no step data", never a made-up number).
     */
    private suspend fun steps(fromHealthConnect: List<Long>, windows: List<HealthWindow>, week: HealthWindow): Pair<StepSource, List<Long>> {
        if (gateway.isSimulated) return StepSource.SIMULATED to fromHealthConnect
        if (gateway.hasStepData(week)) return StepSource.HEALTH_CONNECT to fromHealthConnect
        val none = StepSource.NONE to windows.map { 0L }
        if (!phoneSteps.isSupported() || !phoneSteps.isPermissionGranted()) return none
        phoneSteps.subscribe()
        val phone = phoneSteps.dailySteps(windows) ?: return none
        return StepSource.PHONE to phone
    }

    companion object {
        const val TREND_DAYS = 7
    }
}
