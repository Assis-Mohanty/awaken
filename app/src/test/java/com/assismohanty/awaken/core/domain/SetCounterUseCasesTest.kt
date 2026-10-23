package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.SetCounter
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The set counter records progress only: it never completes a quest and never touches the ledger (ENGINE_DECISIONS #84). */
@RunWith(AndroidJUnit4::class)
class SetCounterUseCasesTest {
    private val graph = TestGraph()
    private val addSet = AddSetUseCase(graph.db.questDao(), graph.transactions)
    private val undoSet = UndoSetUseCase(graph.db.questDao(), graph.transactions)

    @After
    fun tearDown() = graph.close()

    private suspend fun setsOf(id: String) = SetCounter.parse(checkNotNull(graph.db.questDao().get(id)).setReps)

    @Test
    fun loggingSetsStoresThemInOrder() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 25)

        assertThat(addSet(quest.id, 10)).isEqualTo(SetCounterResult.Updated(listOf(10)))
        assertThat(addSet(quest.id, 8)).isEqualTo(SetCounterResult.Updated(listOf(10, 8)))

        assertThat(setsOf(quest.id)).containsExactly(10, 8).inOrder()
    }

    @Test
    fun reachingTheTargetDoesNotCompleteTheQuestOrPayAnything() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 25)
        val xpBefore = graph.ledger.balances().xpTotal

        addSet(quest.id, 25)

        assertThat(SetCounter.isTargetReached(setsOf(quest.id), 25)).isTrue()
        assertThat(graph.db.questDao().get(quest.id)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(graph.ledger.balances().xpTotal).isEqualTo(xpBefore)
    }

    @Test
    fun theHunterStillCompletesByHandAndGetsTheSelfReward() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 25)
        addSet(quest.id, 25)

        val result = graph.completeQuest(quest.id) as CompleteQuestResult.Completed

        assertThat(result.outcome.verification.name).isEqualTo("SELF")
        assertThat(graph.db.questDao().get(quest.id)?.status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun undoRemovesOnlyTheLastSet() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 25)
        addSet(quest.id, 10)
        addSet(quest.id, 8)

        assertThat(undoSet(quest.id)).isEqualTo(SetCounterResult.Updated(listOf(10)))
        assertThat(undoSet(quest.id)).isEqualTo(SetCounterResult.Updated(emptyList()))
        assertThat(undoSet(quest.id)).isEqualTo(SetCounterResult.Updated(emptyList()))
    }

    @Test
    fun verifiedQuestsHaveNoCounter() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 7_000)

        assertThat(addSet(quest.id, 10)).isEqualTo(SetCounterResult.NotAvailable)
        assertThat(setsOf(quest.id)).isEmpty()
    }

    @Test
    fun aFinishedQuestIsLocked() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 10)
        addSet(quest.id, 10)
        graph.completeQuest(quest.id)

        assertThat(addSet(quest.id, 5)).isEqualTo(SetCounterResult.NotAvailable)
        assertThat(undoSet(quest.id)).isEqualTo(SetCounterResult.NotAvailable)
        assertThat(setsOf(quest.id)).containsExactly(10)
    }

    @Test
    fun anUnknownQuestIsNotAvailable() = runTest {
        assertThat(addSet("nope", 5)).isEqualTo(SetCounterResult.NotAvailable)
    }

    @Test
    fun theCounterStopsAtTheSetLimit() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("pushups", target = 1_000)
        repeat(SetCounter.MAX_SETS + 5) { addSet(quest.id, 1) }

        assertThat(setsOf(quest.id)).hasSize(SetCounter.MAX_SETS)
    }
}
