package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** GD §6.7 Screen Discipline + §12 vectors 36–40. */
class ScreenTimeRulesTest {

    private val today = LocalDate.parse("2026-10-25")
    private val insta = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    private fun rule(pkg: String, category: AppCategory, limit: Int?, from: LocalDate = today) = AppRule(pkg, category, limit, from)
    private fun minutes(m: Int): Long = m * 60_000L

    @Test
    fun `vector 36 - screen discipline pays 78 XP at streak 0 and 117 at streak 30, uncapped`() {
        assertThat(XpCalculator.questXp(Difficulty.NORMAL, 0, VerificationSource.USAGE)).isEqualTo(78)
        assertThat(XpCalculator.questXp(Difficulty.NORMAL, 30, VerificationSource.USAGE)).isEqualTo(117)
        assertThat(DailyCaps.capFor(VerificationSource.USAGE)).isNull()
        val context = HunterContext(hunterId = "h", focusStats = listOf(Stat.STR, Stat.INT), todaySelfXp = 10_000)
        assertThat(DailyCaps.earnedToday(VerificationSource.USAGE, context)).isEqualTo(0)
    }

    @Test
    fun `vector 37 - using exactly the limit holds, one minute over breaches`() {
        val rules = ScreenTimeRules.rulesFor(today, listOf(rule(insta, AppCategory.DISTRACTING, 30)))
        assertThat(ScreenTimeRules.evaluate(mapOf(insta to minutes(30) + 59_999), rules).breached).isFalse()
        assertThat(ScreenTimeRules.evaluate(mapOf(insta to minutes(31)), rules).breached).isTrue()
    }

    @Test
    fun `vector 38 - an edit made today applies tomorrow, so today keeps the old limit`() {
        val edit = rule(insta, AppCategory.DISTRACTING, 120, from = ScreenTimeRules.effectiveFromForEdit(today))
        val rules = listOf(rule(insta, AppCategory.DISTRACTING, 30, from = today.minusDays(3)), edit)
        assertThat(ScreenTimeRules.rulesFor(today, rules).getValue(insta).dailyLimitMinutes).isEqualTo(30)
        assertThat(ScreenTimeRules.rulesFor(today.plusDays(1), rules).getValue(insta).dailyLimitMinutes).isEqualTo(120)
    }

    @Test
    fun `vector 39 - productive, neutral and unlimited apps never breach`() {
        val rules = ScreenTimeRules.rulesFor(
            today,
            listOf(
                rule("work.app", AppCategory.PRODUCTIVE, null),
                rule("misc.app", AppCategory.NEUTRAL, null),
                rule(yt, AppCategory.DISTRACTING, null),
            ),
        )
        val state = ScreenTimeRules.evaluate(mapOf("work.app" to minutes(600), "misc.app" to minutes(600), yt to minutes(600)), rules)
        assertThat(state.breached).isFalse()
        assertThat(state.hasStake).isFalse()
    }

    @Test
    fun `vector 40 - the discipline quest is WIL, Normal, device-verified and stable per day`() {
        val quest = ScreenTimeRules.disciplineQuest("h", today, "Screen Discipline", "Hold every limit.")
        assertThat(quest.type).isEqualTo(QuestType.DISCIPLINE)
        assertThat(quest.stat).isEqualTo(Stat.WIL)
        assertThat(quest.difficulty).isEqualTo(Difficulty.NORMAL)
        assertThat(quest.verification).isEqualTo(Verification.DEVICE_USAGE)
        assertThat(quest.questInstanceId).isEqualTo(ScreenTimeRules.disciplineQuest("h", today, "x", "y").questInstanceId)
        assertThat(quest.questInstanceId).isNotEqualTo(ScreenTimeRules.disciplineQuest("h", today.plusDays(1), "x", "y").questInstanceId)
        assertThat(DirectiveGenerator.canReroll(QuestType.DISCIPLINE)).isFalse()
        assertThat(QuestType.DISCIPLINE.isGenerated).isFalse()
    }

    @Test
    fun `defaults - bundled list first, then the platform category`() {
        assertThat(ScreenTimeRules.defaultCategory(insta, AppKind.OTHER)).isEqualTo(AppCategory.DISTRACTING)
        assertThat(ScreenTimeRules.defaultCategory("com.duolingo", AppKind.GAME)).isEqualTo(AppCategory.PRODUCTIVE)
        assertThat(ScreenTimeRules.defaultCategory("some.game", AppKind.GAME)).isEqualTo(AppCategory.DISTRACTING)
        assertThat(ScreenTimeRules.defaultCategory("some.video", AppKind.VIDEO)).isEqualTo(AppCategory.DISTRACTING)
        assertThat(ScreenTimeRules.defaultCategory("some.notes", AppKind.PRODUCTIVITY)).isEqualTo(AppCategory.PRODUCTIVE)
        assertThat(ScreenTimeRules.defaultCategory("some.tool", AppKind.OTHER)).isEqualTo(AppCategory.NEUTRAL)
        assertThat(ScreenTimeRules.defaultLimit(AppCategory.DISTRACTING)).isEqualTo(30)
        assertThat(ScreenTimeRules.defaultLimit(AppCategory.NEUTRAL)).isNull()
    }

    @Test
    fun `limits are 5 to 240 minutes in 5-minute steps`() {
        assertThat(ScreenTimeRules.isValidLimit(5)).isTrue()
        assertThat(ScreenTimeRules.isValidLimit(240)).isTrue()
        assertThat(ScreenTimeRules.isValidLimit(0)).isFalse()
        assertThat(ScreenTimeRules.isValidLimit(245)).isFalse()
        assertThat(ScreenTimeRules.isValidLimit(32)).isFalse()
    }

    @Test
    fun `a rule that starts tomorrow is not in force today`() {
        val rules = ScreenTimeRules.rulesFor(today, listOf(rule(insta, AppCategory.DISTRACTING, 30, from = today.plusDays(1))))
        assertThat(rules).isEmpty()
    }

    @Test
    fun `warnings are due with 5 minutes or fewer left and stop once crossed`() {
        val rules = ScreenTimeRules.rulesFor(today, listOf(rule(insta, AppCategory.DISTRACTING, 30), rule(yt, AppCategory.DISTRACTING, 60)))
        fun warned(instaMin: Int, ytMin: Int) =
            ScreenTimeRules.warningsDue(ScreenTimeRules.evaluate(mapOf(insta to minutes(instaMin), yt to minutes(ytMin)), rules)).map { it.packageName }
        assertThat(warned(24, 10)).isEmpty()
        assertThat(warned(25, 10)).containsExactly(insta)
        assertThat(warned(30, 56)).containsExactly(insta, yt)
        assertThat(warned(31, 10)).isEmpty()
    }

    @Test
    fun `remaining minutes never go negative`() {
        assertThat(AppLimitState(insta, usedMinutes = 50, limitMinutes = 30).remainingMinutes).isEqualTo(0)
    }
}
