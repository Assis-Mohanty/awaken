package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class QuestReasonsTest {

    private val today = LocalDate.of(2026, 10, 18)
    private val focus = listOf(Stat.STR, Stat.INT)

    private fun side(stat: Stat, lastActive: Map<Stat, LocalDate>) =
        QuestReasons.reasonFor(QuestType.SIDE, stat, focus, lastActive, today)

    @Test
    fun `fixed reasons for main, penalty and habit quests`() {
        assertThat(QuestReasons.reasonFor(QuestType.MAIN, Stat.STR, focus, emptyMap(), today)).isEqualTo(QuestReason.Main(Stat.STR))
        assertThat(QuestReasons.reasonFor(QuestType.PENALTY, Stat.VIT, focus, emptyMap(), today)).isEqualTo(QuestReason.Penalty)
        assertThat(QuestReasons.reasonFor(QuestType.HABIT, Stat.CHA, focus, emptyMap(), today)).isEqualTo(QuestReason.Habit)
    }

    @Test
    fun `trial stages and gates have no reason line`() {
        assertThat(QuestReasons.reasonFor(QuestType.TRIAL, Stat.STR, focus, emptyMap(), today)).isNull()
        assertThat(QuestReasons.reasonFor(QuestType.GATE, Stat.INT, focus, emptyMap(), today)).isNull()
    }

    @Test
    fun `a never-trained stat is new, even when it is a focus stat`() {
        assertThat(side(Stat.STR, emptyMap())).isEqualTo(QuestReason.New(Stat.STR))
    }

    @Test
    fun `neglect starts at exactly NEGLECT_DAYS idle game-days`() {
        assertThat(side(Stat.WIL, mapOf(Stat.WIL to today.minusDays(2)))).isEqualTo(QuestReason.Balance)
        assertThat(side(Stat.WIL, mapOf(Stat.WIL to today.minusDays(3)))).isEqualTo(QuestReason.Neglected(Stat.WIL, 3))
        assertThat(side(Stat.INT, mapOf(Stat.INT to today.minusDays(9)))).isEqualTo(QuestReason.Neglected(Stat.INT, 9))
    }

    @Test
    fun `recently trained focus stat is focus, anything else is balance`() {
        assertThat(side(Stat.INT, mapOf(Stat.INT to today))).isEqualTo(QuestReason.Focus(Stat.INT))
        assertThat(side(Stat.VIT, mapOf(Stat.VIT to today.minusDays(1)))).isEqualTo(QuestReason.Balance)
    }
}
