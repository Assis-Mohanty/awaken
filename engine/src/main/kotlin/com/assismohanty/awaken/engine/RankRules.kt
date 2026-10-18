package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rank bands and Rank Trial gating (GD §5). Reaching a band only unlocks the trial for the *next* rank; the displayed
 * rank is the highest rank whose trial was passed.
 */
object RankRules {
    const val TRIAL_STAGES = 3
    const val TRIAL_WINDOW_DAYS = 5L
    const val TRIAL_REWARD_GOLD = 500
    val TRIAL_RETRY_COOLDOWN: Duration = Duration.ofHours(48)

    /** The band a level falls in, ignoring trials. */
    fun bandFor(level: Int): Rank = Rank.entries.last { level >= it.minLevel }

    /** Displayed rank = highest passed trial (E if none). Bands never promote by themselves. */
    fun currentRank(passedTrials: Set<Rank>): Rank = passedTrials.maxOrNull() ?: Rank.E

    /**
     * The rank a trial may be started for right now, or null.
     * @param lastFailedAt when the most recent trial for that rank failed (window expired), if any.
     */
    fun trialAvailable(
        level: Int,
        passedTrials: Set<Rank>,
        trialActive: Boolean,
        lastFailedAt: Instant?,
        now: Instant,
    ): Rank? {
        if (trialActive) return null
        val target = currentRank(passedTrials).next ?: return null
        if (bandFor(level) < target) return null
        if (lastFailedAt != null && Duration.between(lastFailedAt, now) < TRIAL_RETRY_COOLDOWN) return null
        return target
    }

    /** Last game-date of a trial started on [startDate] (5-day window, inclusive). */
    fun trialExpiresOn(startDate: LocalDate): LocalDate = startDate.plusDays(TRIAL_WINDOW_DAYS - 1)

    /**
     * When an unpassed trial failed: the start of the game-day after its window. The 48 h retry cooldown runs from
     * here, not from whenever the app next opens (ENGINE_DECISIONS #31).
     */
    fun trialFailedAt(startDate: LocalDate, zone: ZoneId, dayStartHour: Int): Instant =
        GameDay.startOf(trialExpiresOn(startDate).plusDays(1), zone, dayStartHour)

    /**
     * Trial stage stats: the two weakest stats by XP plus one focus stat. Ties break by [Stat] order. If both focus
     * stats are among the weakest two, the third stage uses the third-weakest stat so stages never repeat a stat.
     */
    fun trialStats(statXp: Map<Stat, Long>, focusStats: List<Stat>): List<Stat> {
        val byWeakness = Stat.entries.sortedWith(compareBy<Stat> { statXp[it] ?: 0L }.thenBy { it.ordinal })
        val weakest = byWeakness.take(2)
        val third = focusStats.firstOrNull { it !in weakest } ?: byWeakness[2]
        return weakest + third
    }

    enum class TrialStatus { ACTIVE, PASSED, FAILED }

    /** Status of a trial started on [startDate] given the game-dates on which stages were cleared. */
    fun trialStatus(startDate: LocalDate, today: LocalDate, stageClearDates: List<LocalDate>): TrialStatus {
        val expires = trialExpiresOn(startDate)
        val valid = stageClearDates.filter { it in startDate..expires }
        return when {
            valid.size >= TRIAL_STAGES -> TrialStatus.PASSED
            today > expires -> TrialStatus.FAILED
            else -> TrialStatus.ACTIVE
        }
    }

    /** A completed TRIAL quest: which stage stat it cleared and on which game-day. */
    data class StageCompletion(val stat: Stat, val gameDate: LocalDate)

    /**
     * Cleared stages of a trial started on [startDate], derived from its completed TRIAL quests (ENGINE_DECISIONS #31):
     * only completions inside the window count, at most one per game-day and one per stat, earliest first.
     */
    fun clearedStages(startDate: LocalDate, completions: List<StageCompletion>): Map<Stat, LocalDate> {
        val window = startDate..trialExpiresOn(startDate)
        val cleared = LinkedHashMap<Stat, LocalDate>()
        completions.filter { it.gameDate in window }
            .sortedWith(compareBy<StageCompletion> { it.gameDate }.thenBy { it.stat.ordinal })
            .forEach { completion ->
                if (completion.stat !in cleared && completion.gameDate !in cleared.values && cleared.size < TRIAL_STAGES) {
                    cleared[completion.stat] = completion.gameDate
                }
            }
        return cleared
    }

    /** Stage stats still open, in stage order. */
    fun openStages(stageStats: List<Stat>, cleared: Map<Stat, LocalDate>): List<Stat> = stageStats.filter { it !in cleared }

    /** ENGINE_DECISIONS #32: promotion is permanent, so the completion that passed a trial cannot be undone. */
    fun canUndoStage(trialStatus: TrialStatus): Boolean = trialStatus != TrialStatus.PASSED

    /** One trial stage per game-day ("one Hard quest per day"), any order, inside the window. */
    fun canClearStage(startDate: LocalDate, today: LocalDate, stageClearDates: List<LocalDate>): Boolean =
        today in startDate..trialExpiresOn(startDate) &&
            today !in stageClearDates &&
            stageClearDates.size < TRIAL_STAGES
}
