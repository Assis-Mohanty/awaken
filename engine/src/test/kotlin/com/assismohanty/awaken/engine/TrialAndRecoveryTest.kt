package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.RankRules.StageCompletion
import com.assismohanty.awaken.engine.RankRules.TrialStatus
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.TemplateLibrary
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TrialAndRecoveryTest {

    private val start = LocalDate.of(2026, 10, 19)
    private val library = TemplateLibrary.parse(requireNotNull(javaClass.classLoader.getResource("quests/templates.json")).readText())
    private val context = HunterContext(hunterId = "h1", focusStats = listOf(Stat.STR, Stat.INT))

    @Test
    fun `trial stages are one hard self-reported trial quest per stat, stable per day`() {
        val stats = listOf(Stat.WIL, Stat.CHA, Stat.STR)
        val first = DirectiveGenerator.trialStages(library.templates, context, stats, start)
        val again = DirectiveGenerator.trialStages(library.templates, context, stats, start)
        val byId = library.templates.associateBy { it.id }

        assertThat(again).isEqualTo(first)
        assertThat(first.map { it.stat }).containsExactlyElementsIn(stats).inOrder()
        first.forEach { stage ->
            assertThat(stage.type).isEqualTo(QuestType.TRIAL)
            assertThat(stage.difficulty).isEqualTo(Difficulty.HARD)
            assertThat(byId.getValue(checkNotNull(stage.templateId)).verification.source).isEqualTo(VerificationSource.SELF)
        }
        assertThat(first.map { it.questInstanceId }.toSet()).hasSize(3)
    }

    @Test
    fun `trial stages ignore recovery mode`() {
        val recovering = context.copy(recoveryMode = true)
        val stages = DirectiveGenerator.trialStages(library.templates, recovering, listOf(Stat.STR), start)
        assertThat(stages.single().stat).isEqualTo(Stat.STR)
    }

    @Test
    fun `cleared stages count once per stat and once per day inside the window`() {
        val cleared = RankRules.clearedStages(
            start,
            listOf(
                StageCompletion(Stat.WIL, start.minusDays(1)), // before the window
                StageCompletion(Stat.WIL, start),
                StageCompletion(Stat.CHA, start), // same day as WIL: doesn't count
                StageCompletion(Stat.WIL, start.plusDays(1)), // WIL already cleared
                StageCompletion(Stat.CHA, start.plusDays(2)),
                StageCompletion(Stat.STR, start.plusDays(5)), // after the 5-day window
            ),
        )
        assertThat(cleared).containsExactly(Stat.WIL, start, Stat.CHA, start.plusDays(2)).inOrder()
        assertThat(RankRules.openStages(listOf(Stat.WIL, Stat.CHA, Stat.STR), cleared)).containsExactly(Stat.STR)
        assertThat(RankRules.trialStatus(start, start.plusDays(5), cleared.values.toList())).isEqualTo(TrialStatus.FAILED)
    }

    @Test
    fun `three stages on three days pass the trial and the passing stage cannot be undone`() {
        val cleared = RankRules.clearedStages(
            start,
            listOf(StageCompletion(Stat.WIL, start), StageCompletion(Stat.CHA, start.plusDays(1)), StageCompletion(Stat.STR, start.plusDays(4))),
        )
        val status = RankRules.trialStatus(start, start.plusDays(4), cleared.values.toList())
        assertThat(status).isEqualTo(TrialStatus.PASSED)
        assertThat(RankRules.canUndoStage(status)).isFalse()
        assertThat(RankRules.canUndoStage(TrialStatus.ACTIVE)).isTrue()
    }

    @Test
    fun `a trial fails at the start of the game-day after its window`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val failedAt = RankRules.trialFailedAt(start, zone, 4)
        assertThat(failedAt).isEqualTo(LocalDateTime.of(2026, 10, 24, 4, 0).atZone(zone).toInstant())
    }

    @Test
    fun `recovery carries over while budget remains`() {
        assertThat(StreakRules.recoveryContinues(previousDayRecovery = true, usedThisMonth = 6)).isTrue()
        assertThat(StreakRules.recoveryContinues(previousDayRecovery = true, usedThisMonth = 7)).isFalse()
        assertThat(StreakRules.recoveryContinues(previousDayRecovery = false, usedThisMonth = 0)).isFalse()
        assertThat(StreakRules.recoveryRemaining(3)).isEqualTo(4)
        assertThat(StreakRules.recoveryRemaining(9)).isEqualTo(0)
    }
}
