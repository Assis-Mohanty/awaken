package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** GD §16.1–16.2 habit types, flexible weeks, skips and reminders (vectors #85–#95). */
class HabitRulesTest {

    private val daily = HabitFrequency.DAYS_OF_WEEK
    private val weekly = HabitFrequency.TIMES_PER_WEEK

    @Test
    fun `vector 85 - a count habit pays only when the target is reached`() {
        assertThat(HabitRules.quantityComplete(progress = 7, target = 8)).isFalse()
        assertThat(HabitRules.quantityComplete(progress = 8, target = 8)).isTrue()
        // SELF, Normal, streak 0, no crit: 60 XP and 30 gold (§4.1, §9.1), the same as any self-reported quest.
        val xp = XpCalculator.questXp(Difficulty.NORMAL, 0, VerificationSource.SELF)
        assertThat(xp).isEqualTo(60)
        assertThat(XpCalculator.goldFor(xp)).isEqualTo(30)
    }

    @Test
    fun `vector 85b - progress is clamped between zero and the target`() {
        assertThat(HabitRules.clampProgress(-1, 8)).isEqualTo(0)
        assertThat(HabitRules.clampProgress(9, 8)).isEqualTo(8)
    }

    @Test
    fun `vector 86 - a count target of 101 or 1 is rejected, 2 and 100 are fine`() {
        assertThat(HabitRules.validate(HabitType.QUANTITY, daily, null, 101)).isEqualTo(HabitProblem.QUANTITY_TARGET)
        assertThat(HabitRules.validate(HabitType.QUANTITY, daily, null, 1)).isEqualTo(HabitProblem.QUANTITY_TARGET)
        assertThat(HabitRules.validate(HabitType.QUANTITY, daily, null, 2)).isNull()
        assertThat(HabitRules.validate(HabitType.QUANTITY, daily, null, 100)).isNull()
    }

    @Test
    fun `vector 87 - a 30 minute timed habit is Normal and pays 69 XP as one TIMER quest`() {
        assertThat(HabitRules.timedDifficulty(30)).isEqualTo(Difficulty.NORMAL)
        assertThat(HabitRules.verificationFor(HabitType.TIMED)).isEqualTo(Verification.TIMER)
        assertThat(XpCalculator.questXp(Difficulty.NORMAL, 0, VerificationSource.TIMER)).isEqualTo(69)
    }

    @Test
    fun `vector 88 - 14 minutes is rejected and 95 minutes is Extreme for 138 XP`() {
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 14)).isEqualTo(HabitProblem.TIMED_MINUTES)
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 95)).isNull()
        assertThat(HabitRules.timedDifficulty(95)).isEqualTo(Difficulty.EXTREME)
        assertThat(XpCalculator.questXp(Difficulty.EXTREME, 0, VerificationSource.TIMER)).isEqualTo(138)
    }

    @Test
    fun `timed minutes follow the 5 minute steps from 15 to 180`() {
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 15)).isNull()
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 180)).isNull()
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 17)).isEqualTo(HabitProblem.TIMED_MINUTES)
        assertThat(HabitRules.validate(HabitType.TIMED, daily, null, 185)).isEqualTo(HabitProblem.TIMED_MINUTES)
    }

    @Test
    fun `timed difficulty follows the section 8-1 tiers`() {
        assertThat(listOf(15, 24, 25, 49, 50, 89, 90, 180).map(HabitRules::timedDifficulty)).containsExactly(
            Difficulty.EASY, Difficulty.EASY, Difficulty.NORMAL, Difficulty.NORMAL,
            Difficulty.HARD, Difficulty.HARD, Difficulty.EXTREME, Difficulty.EXTREME,
        ).inOrder()
    }

    @Test
    fun `only a timed habit is TIMER verified`() {
        assertThat(HabitType.entries.filter { HabitRules.verificationFor(it) == Verification.TIMER }).containsExactly(HabitType.TIMED)
    }

    @Test
    fun `vector 90 - kept it opens at the start of the evening block and stays open to the end of the game-day`() {
        val kept = { hhmm: String -> HabitRules.canConfirmKept(LocalTime.parse(hhmm), dayStartHour = 4) }
        assertThat(kept("16:59")).isFalse()
        assertThat(kept("17:00")).isTrue()
        assertThat(kept("21:00")).isTrue()
        assertThat(kept("03:30")).isTrue() // still the previous game-day's evening
        assertThat(kept("04:00")).isFalse()
    }

    @Test
    fun `vector 90 - an avoid day nobody confirmed is missed, never paid`() {
        assertThat(HabitRules.closedStatus(daily)).isEqualTo(QuestStatus.MISSED)
    }

    @Test
    fun `an avoid habit must be on fixed weekdays`() {
        assertThat(HabitRules.validate(HabitType.NEGATIVE, weekly, 3, 1)).isEqualTo(HabitProblem.NEGATIVE_NEEDS_DAYS)
        assertThat(HabitRules.validate(HabitType.NEGATIVE, daily, null, 1)).isNull()
    }

    @Test
    fun `a check habit keeps its free-form target`() {
        assertThat(HabitRules.validate(HabitType.CHECK, daily, null, 20)).isNull()
        assertThat(HabitRules.validate(HabitType.CHECK, daily, null, 0)).isNotNull()
    }

    @Test
    fun `vector 92 - a 3 times a week habit stops getting instances once the week is done and expires instead of missing`() {
        // Mon done, Tue done, Wed nothing, Thu done.
        assertThat(HabitRules.flexibleNeedsInstance(timesPerWeek = 3, completionsThisWeek = 0)).isTrue() // Mon
        assertThat(HabitRules.flexibleNeedsInstance(timesPerWeek = 3, completionsThisWeek = 1)).isTrue() // Tue
        assertThat(HabitRules.flexibleNeedsInstance(timesPerWeek = 3, completionsThisWeek = 2)).isTrue() // Wed, left open
        assertThat(HabitRules.closedStatus(weekly)).isEqualTo(QuestStatus.EXPIRED) // Wed is EXPIRED, not MISSED
        assertThat(HabitRules.flexibleNeedsInstance(timesPerWeek = 3, completionsThisWeek = 2)).isTrue() // Thu
        assertThat(HabitRules.flexibleNeedsInstance(timesPerWeek = 3, completionsThisWeek = 3)).isFalse() // Fri-Sun: none
    }

    @Test
    fun `times per week takes 1 to 6`() {
        assertThat(HabitRules.validate(HabitType.CHECK, weekly, 0, 1)).isEqualTo(HabitProblem.TIMES_PER_WEEK)
        assertThat(HabitRules.validate(HabitType.CHECK, weekly, 7, 1)).isEqualTo(HabitProblem.TIMES_PER_WEEK)
        assertThat(HabitRules.validate(HabitType.CHECK, weekly, null, 1)).isEqualTo(HabitProblem.TIMES_PER_WEEK)
        assertThat(HabitRules.validate(HabitType.CHECK, weekly, 1, 1)).isNull()
        assertThat(HabitRules.validate(HabitType.CHECK, weekly, 6, 1)).isNull()
    }

    @Test
    fun `vector 93 - a third habit cannot take a reminder`() {
        assertThat(HabitRules.canSetReminder(otherHabitsWithReminder = 1)).isTrue()
        assertThat(HabitRules.canSetReminder(otherHabitsWithReminder = 2)).isFalse()
    }

    @Test
    fun `reminders are minutes after midnight`() {
        assertThat(HabitRules.isValidReminder(0)).isTrue()
        assertThat(HabitRules.isValidReminder(1439)).isTrue()
        assertThat(HabitRules.isValidReminder(1440)).isFalse()
        assertThat(HabitRules.isValidReminder(-1)).isFalse()
    }

    @Test
    fun `vector 95 - one skip a week, never for a flexible habit`() {
        assertThat(HabitRules.canSkip(daily, skipsThisWeek = 0)).isTrue() // Tue
        assertThat(HabitRules.canSkip(daily, skipsThisWeek = 1)).isFalse() // Thu refused while Tue's skip stands
        assertThat(HabitRules.canSkip(daily, skipsThisWeek = 0)).isTrue() // after undoing Tue's skip
        assertThat(HabitRules.canSkip(weekly, skipsThisWeek = 0)).isFalse()
    }

    @Test
    fun `the limit of active habits is unchanged at 8`() {
        assertThat(HabitRules.MAX_HABITS).isEqualTo(8)
    }

    @Test
    fun `a skipped quest is neither hidden nor a day item`() {
        assertThat(QuestStatus.SKIPPED.isHidden).isFalse()
    }
}
