package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The daily System line (GD §11): one line keyed by context, deterministic by date. Priority, highest first:
 * comeback (penalty quest open) → trial_active → streak_milestone → cleared_day → neglected_<stat> → generic.
 */
object SystemLines {
    const val COMEBACK = "comeback"
    const val TRIAL_ACTIVE = "trial_active"
    const val STREAK_MILESTONE = "streak_milestone"
    const val CLEARED_DAY = "cleared_day"
    const val GENERIC = "generic"
    const val STREAK_PLACEHOLDER = "{streak}"

    /** The streak achievement thresholds (GD §10); no new numbers. */
    val STREAK_MILESTONES: Set<Int> = setOf(3, 7, 30, 100)

    fun neglectedKey(stat: Stat): String = "neglected_${stat.name.lowercase()}"

    /**
     * The longest-neglected stat that was active before and has had no completion for [DirectiveGenerator.NEGLECT_DAYS]+
     * game-days. Stats never touched don't count, so a brand-new hunter isn't told everything is "fading".
     */
    fun neglectedStat(lastActive: Map<Stat, LocalDate>, today: LocalDate): Stat? =
        lastActive.entries
            .map { (stat, date) -> stat to ChronoUnit.DAYS.between(date, today) }
            .filter { (_, days) -> days >= DirectiveGenerator.NEGLECT_DAYS }
            .sortedWith(compareByDescending<Pair<Stat, Long>> { it.second }.thenBy { it.first.ordinal })
            .firstOrNull()?.first

    fun contextKey(
        penaltyOpen: Boolean,
        trialActive: Boolean,
        streak: Int,
        todayCleared: Boolean,
        neglected: Stat?,
    ): String = when {
        penaltyOpen -> COMEBACK
        trialActive -> TRIAL_ACTIVE
        streak in STREAK_MILESTONES -> STREAK_MILESTONE
        todayCleared -> CLEARED_DAY
        neglected != null -> neglectedKey(neglected)
        else -> GENERIC
    }

    /** Deterministic pick for (hunter, date, context); falls back to generic if a context has no lines. */
    fun pick(lines: Map<String, List<String>>, key: String, hunterId: String, gameDate: LocalDate, streak: Int): String? {
        val (usedKey, options) = lines[key]?.takeIf { it.isNotEmpty() }?.let { key to it }
            ?: lines[GENERIC]?.takeIf { it.isNotEmpty() }?.let { GENERIC to it }
            ?: return null
        val index = Fnv1a.hash64("$hunterId|$gameDate|$usedKey").toULong() % options.size.toULong()
        return options[index.toInt()].replace(STREAK_PLACEHOLDER, streak.toString())
    }
}
