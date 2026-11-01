package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.usage.InstalledApp
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.AppRule
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.LocalDate
import javax.inject.Inject

/** One launcher app on the Screen Time list. [next*] are set when tomorrow's rule differs from today's. */
data class AppUsageRow(
    val packageName: String,
    val label: String,
    val usedMinutes: Int,
    val category: AppCategory,
    val limitMinutes: Int?,
    val nextCategory: AppCategory? = null,
    val nextLimitMinutes: Int? = null,
    val hasPendingChange: Boolean = false,
) {
    val breached: Boolean get() = limitMinutes != null && category == AppCategory.DISTRACTING && usedMinutes > limitMinutes
}

enum class DisciplineStatus {
    /** Screen Discipline is off. */
    OFF,

    /** On, but it starts on a later game-day. */
    STARTS_LATER,

    /** On today, but usage can't be read (no access, or the system reports nothing): no limit is checked, nothing is paid. */
    NOT_TRACKED,

    /** On today, but nothing to defend: no distracting app has a limit. */
    NO_LIMITS,
    ON_TRACK,
    FORFEITED,
}

data class ScreenTimeDay(val gameDate: LocalDate, val totalMinutes: Int, val distractingMinutes: Int)

data class ScreenTimeOverview(
    val gameDate: LocalDate,
    val hasAccess: Boolean,
    /** Today's usage was really read. False shows "not tracked" instead of minutes. */
    val tracked: Boolean,
    val status: DisciplineStatus,
    val startsOn: LocalDate?,
    val xpAtStake: Int,
    val totalMinutes: Int,
    val minutesByCategory: Map<AppCategory, Int>,
    val apps: List<AppUsageRow>,
    /** The last 7 game-days, oldest first, today included. */
    val trend: List<ScreenTimeDay>,
)

/**
 * Everything the Screen Time screen shows (DESIGN_SYSTEM §4.9). Read-only: forfeits happen only in
 * [DisciplineSyncUseCase], so opening the screen never changes game state. Apps without a rule yet show their default
 * category and get it seeded for tomorrow.
 */
class LoadScreenTimeOverviewUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val reader: ScreenTimeReader,
    private val seedRules: SeedAppRulesUseCase,
    private val gameState: GameStateReader,
    private val usage: UsageGateway,
    private val clock: Clock,
) {
    /** Null when there is no hunter. */
    suspend operator fun invoke(): ScreenTimeOverview? {
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val streak = gameState.hunterContext(profile, today).streak
        val xp = XpCalculator.questXp(ScreenTimeRules.DISCIPLINE_DIFFICULTY, streak, VerificationSource.USAGE, crit = false)
        if (!usage.hasAccess()) {
            return ScreenTimeOverview(today, false, false, statusWithoutUsage(profile.screenTimeFrom, today), profile.screenTimeFrom, xp, 0, emptyMap(), emptyList(), emptyList())
        }
        seedRules()
        val installed = usage.launcherApps()
        val todayRules = reader.rulesInForce(today)
        val tomorrowRules = reader.rulesInForce(today.plusDays(1))
        val read = reader.usageOn(profile, today)
        val tracked = read is UsageRead.Tracked
        val used = read.trackedMillis.orEmpty()
        val rows = installed.map { app ->
            val rule = todayRules[app.packageName]
            val category = rule?.category ?: ScreenTimeRules.defaultCategory(app.packageName, app.kind)
            val limit = rule?.dailyLimitMinutes
            val next = tomorrowRules[app.packageName]
            val pending = next != null && (next.category != category || next.dailyLimitMinutes != limit)
            AppUsageRow(
                packageName = app.packageName,
                label = app.label,
                usedMinutes = ((used[app.packageName] ?: 0L) / MILLIS_PER_MINUTE).toInt(),
                category = category,
                limitMinutes = limit,
                nextCategory = next?.category.takeIf { pending },
                nextLimitMinutes = next?.dailyLimitMinutes.takeIf { pending },
                hasPendingChange = pending,
            )
        }.sortedWith(compareByDescending<AppUsageRow> { it.usedMinutes }.thenBy { it.label.lowercase() })
        val byCategory = AppCategory.entries.associateWith { cat -> rows.filter { it.category == cat }.sumOf { it.usedMinutes } }
        val state = ScreenTimeRules.evaluate(used, todayRules)
        val quest = reader.disciplineQuest(today)
        val status = when {
            profile.screenTimeFrom == null -> DisciplineStatus.OFF
            today.isBefore(profile.screenTimeFrom) -> DisciplineStatus.STARTS_LATER
            quest?.status == QuestStatus.EXPIRED -> DisciplineStatus.FORFEITED
            !tracked -> DisciplineStatus.NOT_TRACKED
            state.breached -> DisciplineStatus.FORFEITED
            !state.hasStake -> DisciplineStatus.NO_LIMITS
            else -> DisciplineStatus.ON_TRACK
        }
        return ScreenTimeOverview(
            gameDate = today,
            hasAccess = true,
            tracked = tracked,
            status = status,
            startsOn = profile.screenTimeFrom,
            xpAtStake = xp,
            totalMinutes = rows.sumOf { it.usedMinutes },
            minutesByCategory = byCategory,
            apps = rows,
            trend = trend(profile, today, installed.associate { it.packageName to it }, todayRules),
        )
    }

    private fun statusWithoutUsage(from: LocalDate?, today: LocalDate): DisciplineStatus = when {
        from == null -> DisciplineStatus.OFF
        today.isBefore(from) -> DisciplineStatus.STARTS_LATER
        else -> DisciplineStatus.NOT_TRACKED
    }

    private suspend fun trend(
        profile: HunterProfileEntity,
        today: LocalDate,
        installed: Map<String, InstalledApp>,
        todayRules: Map<String, AppRule>,
    ): List<ScreenTimeDay> = (TREND_DAYS - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val dayRules = reader.rulesInForce(date)
        val used = reader.usageOn(profile, date).trackedMillis.orEmpty().filterKeys { it in installed }
        val distracting = used.filterKeys { pkg ->
            val category = dayRules[pkg]?.category ?: todayRules[pkg]?.category
                ?: installed[pkg]?.let { ScreenTimeRules.defaultCategory(it.packageName, it.kind) }
            category == AppCategory.DISTRACTING
        }
        ScreenTimeDay(date, (used.values.sum() / MILLIS_PER_MINUTE).toInt(), (distracting.values.sum() / MILLIS_PER_MINUTE).toInt())
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val TREND_DAYS = 7
    }
}
