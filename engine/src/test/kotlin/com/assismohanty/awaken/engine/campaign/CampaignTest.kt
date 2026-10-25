package com.assismohanty.awaken.engine.campaign

import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.AchievementSnapshot
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §6.8 The 45-day Campaign + §12 vectors 41–45. */
class CampaignTest {
    private val start = LocalDate.parse("2026-10-25")
    private val library = TemplateLibrary.parse(requireNotNull(javaClass.classLoader.getResource("quests/templates.json")).readText())
    private val context = HunterContext(hunterId = "h1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `vector 41 - day 1 is the start, day 45 the end, then it is over`() {
        assertThat(CampaignRules.dayIndex(start, start)).isEqualTo(1)
        assertThat(CampaignRules.dayIndex(start, start.plusDays(44))).isEqualTo(45)
        assertThat(CampaignRules.dayIndex(start, start.plusDays(45))).isNull()
        assertThat(CampaignRules.endDate(start)).isEqualTo(LocalDate.parse("2026-12-08"))
        assertThat(CampaignRules.isOver(start, start.plusDays(44))).isFalse()
        assertThat(CampaignRules.isOver(start, start.plusDays(45))).isTrue()
    }

    @Test
    fun `vector 42 - acts change on days 16 and 31`() {
        assertThat(CampaignRules.act(15)).isEqualTo(CampaignAct.FOUNDATION)
        assertThat(CampaignRules.act(16)).isEqualTo(CampaignAct.BUILD)
        assertThat(CampaignRules.act(30)).isEqualTo(CampaignAct.BUILD)
        assertThat(CampaignRules.act(31)).isEqualTo(CampaignAct.ASCEND)
        assertThat(CampaignRules.act(45)).isEqualTo(CampaignAct.ASCEND)
    }

    @Test
    fun `vector 43 - habit tier follows the act but never more than one above the adaptive tier`() {
        assertThat(CampaignRules.habitDifficulty(CampaignAct.FOUNDATION, Difficulty.NORMAL)).isEqualTo(Difficulty.EASY)
        assertThat(CampaignRules.habitDifficulty(CampaignAct.BUILD, Difficulty.NORMAL)).isEqualTo(Difficulty.NORMAL)
        assertThat(CampaignRules.habitDifficulty(CampaignAct.ASCEND, Difficulty.NORMAL)).isEqualTo(Difficulty.HARD)
        assertThat(CampaignRules.habitDifficulty(CampaignAct.ASCEND, Difficulty.EASY)).isEqualTo(Difficulty.NORMAL)
    }

    @Test
    fun `vector 44 - 32 cleared days complete the campaign, 31 do not`() {
        assertThat(CampaignRules.outcome(32)).isEqualTo(CampaignStatus.COMPLETED)
        assertThat(CampaignRules.outcome(31)).isEqualTo(CampaignStatus.ENDED)
        assertThat(CampaignRules.stillReachable(clearedDays = 20, daysLeft = 12)).isTrue()
        assertThat(CampaignRules.stillReachable(clearedDays = 20, daysLeft = 11)).isFalse()
    }

    @Test
    fun `vector 45 - completing a campaign unlocks Reforged once for 1,000 gold and a title`() {
        val reforged = AchievementEvaluator.byId.getValue("reforged")
        assertThat(reforged.gold).isEqualTo(1_000)
        assertThat(reforged.titleId).isEqualTo("reforged")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(campaignsCompleted = 1))).contains("reforged")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(campaignsCompleted = 1, alreadyUnlocked = setOf("reforged")))).doesNotContain("reforged")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(campaignsCompleted = 0))).doesNotContain("reforged")
    }

    @Test
    fun `the scan sums two answers per stat and names the weakest`() {
        val answers = CampaignScan.QUESTIONS.associate { (id, stat) -> id to if (stat == Stat.VIT) 0 else 3 }
        val scores = CampaignScan.scores(answers)
        assertThat(scores.getValue(Stat.VIT)).isEqualTo(0)
        assertThat(scores.getValue(Stat.STR)).isEqualTo(6)
        assertThat(CampaignScan.weakest(scores)).isEqualTo(Stat.VIT)
        assertThat(CampaignScan.archetype(scores)).isEqualTo(Archetype.RESTORER)
        assertThat(CampaignScan.isComplete(answers)).isTrue()
        assertThat(CampaignScan.isComplete(answers - "vit_sleep")).isFalse()
    }

    @Test
    fun `ties go to the first stat in order`() {
        val scores = Stat.entries.associateWith { 2 }
        assertThat(CampaignScan.weakest(scores)).isEqualTo(Stat.STR)
    }

    @Test
    fun `the plan gives the weakest stat two habits, skips the strongest, and is deterministic`() {
        val scores = mapOf(Stat.STR to 1, Stat.INT to 2, Stat.WIL to 3, Stat.VIT to 4, Stat.CHA to 6)
        val plan = CampaignPlanner.plan(library.templates, context, scores, count = 5)
        assertThat(plan).hasSize(5)
        assertThat(plan.count { it.stat == Stat.STR }).isEqualTo(2)
        assertThat(plan.map { it.stat }).doesNotContain(Stat.CHA)
        assertThat(plan.first().days).isEqualTo(DayOfWeek.entries.toSet())
        assertThat(plan.map { it.templateId }).containsNoDuplicates()
        assertThat(CampaignPlanner.plan(library.templates, context, scores, count = 5)).isEqualTo(plan)
        plan.forEach { habit ->
            val template = library.templates.first { it.id == habit.templateId }
            assertThat(template.verification.source).isEqualTo(VerificationSource.SELF)
            CampaignAct.entries.forEach { act -> assertThat(template.targets).containsKey(act.base) }
        }
    }

    @Test
    fun `the plan respects free habit slots`() {
        assertThat(CampaignRules.habitCount(activeHabits = 0, maxActive = QuestRules.MAX_ACTIVE_CUSTOM)).isEqualTo(5)
        assertThat(CampaignRules.habitCount(activeHabits = 4, maxActive = QuestRules.MAX_ACTIVE_CUSTOM)).isEqualTo(4)
        assertThat(CampaignRules.habitCount(activeHabits = 5, maxActive = QuestRules.MAX_ACTIVE_CUSTOM)).isEqualTo(3)
        assertThat(CampaignRules.habitCount(activeHabits = 6, maxActive = QuestRules.MAX_ACTIVE_CUSTOM)).isEqualTo(0)
        val scores = Stat.entries.associateWith { 2 }
        assertThat(CampaignPlanner.plan(library.templates, context, scores, count = 3)).hasSize(3)
        assertThat(CampaignPlanner.plan(library.templates, context, scores, count = 0)).isEmpty()
    }
}
