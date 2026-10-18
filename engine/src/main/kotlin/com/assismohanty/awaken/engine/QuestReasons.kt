package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The "Why this quest?" line on Quest detail (DESIGN_SYSTEM §4.3). */
sealed interface QuestReason {
    data class Main(val stat: Stat) : QuestReason
    data object Penalty : QuestReason
    data object Habit : QuestReason
    data class New(val stat: Stat) : QuestReason
    data class Neglected(val stat: Stat, val days: Long) : QuestReason
    data class Focus(val stat: Stat) : QuestReason
    data object Balance : QuestReason
}

/**
 * Explains a quest with the same signals the generator weighs (GD §6.3, ENGINE_DECISIONS #30). For side quests, highest
 * first: never trained → idle for [DirectiveGenerator.NEGLECT_DAYS]+ game-days → focus stat → balance.
 */
object QuestReasons {
    /** [lastActive] = latest game-date with a completion per stat. Trial stages and gates explain themselves: null. */
    fun reasonFor(
        type: QuestType,
        stat: Stat,
        focusStats: List<Stat>,
        lastActive: Map<Stat, LocalDate>,
        today: LocalDate,
    ): QuestReason? = when (type) {
        QuestType.MAIN -> QuestReason.Main(stat)
        QuestType.PENALTY -> QuestReason.Penalty
        QuestType.HABIT -> QuestReason.Habit
        QuestType.TRIAL, QuestType.GATE -> null
        QuestType.SIDE -> {
            val last = lastActive[stat]
            val idleDays = last?.let { ChronoUnit.DAYS.between(it, today) }
            when {
                idleDays == null -> QuestReason.New(stat)
                idleDays >= DirectiveGenerator.NEGLECT_DAYS -> QuestReason.Neglected(stat, idleDays)
                stat in focusStats -> QuestReason.Focus(stat)
                else -> QuestReason.Balance
            }
        }
    }
}
