package com.assismohanty.awaken.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.achievementNameRes
import com.assismohanty.awaken.core.designsystem.components.unitPluralRes
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayMappingTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    @Test
    fun directiveMapsToMainSidesAndProgress() = runTest {
        graph.seedHunter()
        graph.generateDirective(graph.today())
        // A SELF side quest: the generator may also pick TIMER/HEALTH sides, which can't be completed by hand (GD §6.5).
        val side = graph.db.questDao().getForDate(graph.today()).first { it.type == QuestType.SIDE && it.verification == Verification.SELF }
        graph.completeQuest(side.id)

        val state = graph.observeToday(graph.dayRefresher.ticks).filterNotNull().first().toUiState()

        assertThat(state.loading).isFalse()
        assertThat(state.main?.isMain).isTrue()
        assertThat(state.sides).hasSize(3)
        assertThat(state.sides.single { it.id == side.id }.state).isEqualTo(QuestRowState.DONE)
        assertThat(state.completed).isEqualTo(1)
        assertThat(state.total).isEqualTo(4)
        assertThat(state.cleared).isFalse() // GD §7.1: needs the main quest or 3 items
        assertThat(state.systemLine).isNotNull()
        assertThat(state.header?.name).isEqualTo("Test Hunter")
    }

    @Test
    fun clearingTheMainQuestFlipsTheDirectiveTitle() = runTest {
        graph.seedHunter()
        graph.generateDirective(graph.today())
        val main = graph.db.questDao().getForDate(graph.today()).first { it.type == QuestType.MAIN }
        // GD §7.1: the main quest alone clears the day.
        graph.completeQuest(main.id)

        val state = graph.observeToday(graph.dayRefresher.ticks).filterNotNull().first().toUiState()

        assertThat(state.cleared).isTrue()
    }

    @Test
    fun everyAchievementHasADisplayName() {
        AchievementEvaluator.all.forEach { assertThat(achievementNameRes(it.id)).isNotNull() }
    }

    @Test
    fun everyTemplateUnitHasAPlural() = runTest {
        graph.templates.library().units.forEach { unit -> assertThat(unitPluralRes(unit)).isNotNull() }
    }
}
