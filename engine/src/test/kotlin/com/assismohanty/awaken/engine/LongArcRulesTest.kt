package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestTemplate
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §16.14-16.15 dungeons and programs (vectors #127-#134). */
class LongArcRulesTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun template(id: String, stat: Stat, vararg difficulties: Difficulty) = QuestTemplate(
        id = id, stat = stat, title = "Do {n} $id", description = "Do {target}", unit = "reps",
        targets = difficulties.associateWith { 10 }, verification = Verification.SELF,
    )

    private val library = listOf(
        template("a", Stat.STR, Difficulty.EASY, Difficulty.HARD),
        template("b", Stat.STR, Difficulty.HARD),
        template("c", Stat.STR, Difficulty.NORMAL),
        template("d", Stat.INT, Difficulty.HARD),
        template("e", Stat.STR, Difficulty.HARD, Difficulty.NORMAL),
    )
    private val context = HunterContext(hunterId = "h1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `the catalog has the three originally named programs and their lengths`() {
        assertThat(ProgramRules.CATALOG.map { it.key to it.days }).containsExactly("steady_30" to 30, "groundwork_66" to 66, "ironclad_75" to 75).inOrder()
        assertThat(ProgramRules.CATALOG.map { it.rules.size }).containsExactly(3, 4, 4).inOrder()
        assertThat(ProgramRules.spec("ironclad_75")!!.needsPhoneDownNight).isTrue()
        assertThat(ProgramRules.spec("steady_30")!!.needsPhoneDownNight).isFalse()
        assertThat(ProgramRules.spec("nope")).isNull()
        // No diet, weight, fasting or substance rule anywhere.
        assertThat(ProgramRules.CATALOG.flatMap { it.rules }.map { it.stat }.toSet()).doesNotContain(Stat.CHA)
    }

    @Test
    fun `health rules fall back to plain ones when Health is off`() {
        val walk = ProgramRules.spec("steady_30")!!.rules.first()
        assertThat(ProgramRules.effective(walk, healthOn = true).verification).isEqualTo(Verification.HEALTH_STEPS)
        val plain = ProgramRules.effective(walk, healthOn = false)
        assertThat(plain.verification).isEqualTo(Verification.SELF)
        assertThat(plain.target to plain.unit).isEqualTo(30 to "min")
        val read = ProgramRules.spec("steady_30")!!.rules[1]
        assertThat(ProgramRules.effective(read, healthOn = false)).isEqualTo(read)
        val workout = ProgramRules.spec("ironclad_75")!!.rules.first()
        assertThat(ProgramRules.effective(workout, healthOn = false).verification).isEqualTo(Verification.SELF)
    }

    @Test
    fun `a Health habit finds its rule again, so a day without Health can fall back`() {
        ProgramRules.CATALOG.forEach { spec ->
            val health = spec.rules.filter { it.fallback != null }
            assertThat(health.map { it.verification to it.target }).containsNoDuplicates()
            health.forEach { assertThat(ProgramRules.healthRuleFor(spec, it.verification, it.target)).isEqualTo(it) }
        }
        val ironclad = ProgramRules.spec("ironclad_75")!!
        assertThat(ProgramRules.healthRuleFor(ironclad, Verification.HEALTH_STEPS, 10_000)?.key).isEqualTo(ProgramRuleKey.STEPS_10K)
        // A rule already made plain (Health was off at start), or one with no Health form, is left alone.
        assertThat(ProgramRules.healthRuleFor(ironclad, Verification.SELF, 10_000)).isNull()
        assertThat(ProgramRules.healthRuleFor(ironclad, Verification.TIMER, 25)).isNull()
    }

    @Test
    fun `vector 131 - restart makes the answer day day 1, continue goes on with the day marked`() {
        val spec = ProgramRules.spec("steady_30")!!
        // Started Monday; day 5 (Friday) is broken, and it is answered on Saturday.
        val friday = monday.plusDays(4)
        assertThat(ProgramRules.dayNumber(monday, friday)).isEqualTo(5)
        assertThat(ProgramRules.dayVerdict(allRulesDone = false, restOrRecovery = false)).isEqualTo(ProgramDay.BROKEN)
        val saturday = monday.plusDays(5)
        assertThat(ProgramRules.dayNumber(saturday, saturday)).isEqualTo(1) // restart
        assertThat(ProgramRules.dayNumber(monday, saturday)).isEqualTo(6) // continue
        assertThat(ProgramRules.lastDate(monday, spec)).isEqualTo(monday.plusDays(29))
        assertThat(ProgramRules.isLastDay(monday, spec, monday.plusDays(29))).isTrue()
        assertThat(ProgramRules.isLastDay(monday, spec, monday.plusDays(28))).isFalse()
    }

    @Test
    fun `vector 132 - no broken day completes it, any broken day only finishes it`() {
        assertThat(ProgramRules.endStatus(brokenDays = 0)).isEqualTo(ProgramStatus.COMPLETED)
        assertThat(ProgramRules.endStatus(brokenDays = 1)).isEqualTo(ProgramStatus.FINISHED)
    }

    @Test
    fun `vector 133 - an unanswered break is a continue`() {
        assertThat(ProgramRules.DEFAULT_BREAK).isEqualTo(ProgramBreakChoice.CONTINUE)
    }

    @Test
    fun `vector 134 - a rest day is not broken and the counter still advances`() {
        assertThat(ProgramRules.dayVerdict(allRulesDone = false, restOrRecovery = true)).isEqualTo(ProgramDay.REST)
        assertThat(ProgramRules.dayVerdict(allRulesDone = true, restOrRecovery = true)).isEqualTo(ProgramDay.KEPT)
        assertThat(ProgramRules.dayNumber(monday, monday.plusDays(3))).isEqualTo(4)
    }

    @Test
    fun `a program starts only with the slots, no campaign and no other program`() {
        val spec = ProgramRules.spec("groundwork_66")!!
        assertThat(ProgramRules.canStart(spec, freeHabitSlots = 4, programActive = false)).isEqualTo(ProgramStart.OK)
        assertThat(ProgramRules.canStart(spec, 3, false)).isEqualTo(ProgramStart.NOT_ENOUGH_SLOTS)
        assertThat(ProgramRules.canStart(spec, 8, programActive = true)).isEqualTo(ProgramStart.PROGRAM_ACTIVE)
    }

}
