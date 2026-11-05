package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Archive and delete a habit (phone feedback 2026-11-01): future instances go, the ledger and history never change. */
@RunWith(AndroidJUnit4::class)
class HabitRemovalTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00

    @After
    fun tearDown() = graph.close()

    private suspend fun habit(title: String = "Read 10 pages"): String {
        val result = graph.saveHabit(HabitDraft(null, title, Stat.INT, Difficulty.EASY, DayOfWeek.entries.toSet(), 10, "pages"))
        return (result as SaveHabitResult.Saved).habitId
    }

    private suspend fun instances(habitId: String) = graph.db.questDao().getForDate(graph.today()).filter { it.habitId == habitId }

    /** A habit completed yesterday (ledger rows) with today's instance still open. */
    private suspend fun completedYesterdayOpenToday(): Pair<String, String> {
        graph.seedHunter()
        graph.rollover()
        val id = habit()
        val yesterday = instances(id).single()
        assertThat(graph.completeQuest(yesterday.id)).isInstanceOf(CompleteQuestResult.Completed::class.java)
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        assertThat(instances(id).single().status).isEqualTo(QuestStatus.ACTIVE)
        return id to yesterday.id
    }

    @Test
    fun deleteRemovesTheHabitAndItsOpenInstanceButKeepsLedgerAndHistory() = runTest {
        val (id, yesterdayInstance) = completedYesterdayOpenToday()
        val ledgerBefore = graph.db.ledgerDao().all()
        val balancesBefore = graph.ledger.balances()

        val result = graph.removeHabit.delete(id)

        assertThat(result).isInstanceOf(RemoveHabitResult.Removed::class.java)
        assertThat(graph.db.habitDao().get(id)).isNull()
        assertThat(instances(id)).isEmpty()
        assertThat(graph.db.questDao().get(yesterdayInstance)?.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(graph.db.ledgerDao().all()).isEqualTo(ledgerBefore)
        assertThat(graph.ledger.balances()).isEqualTo(balancesBefore)
        // Tomorrow it isn't generated again.
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        assertThat(instances(id)).isEmpty()
    }

    @Test
    fun archiveStopsFutureInstancesAndRestoreBringsTodayBack() = runTest {
        val (id, _) = completedYesterdayOpenToday()
        val ledgerBefore = graph.db.ledgerDao().all()

        graph.removeHabit.archive(id)

        assertThat(graph.db.habitDao().get(id)?.active).isFalse()
        assertThat(instances(id)).isEmpty()
        assertThat(graph.db.habitDao().observeArchived().first().map { it.id }).containsExactly(id)
        assertThat(graph.db.ledgerDao().all()).isEqualTo(ledgerBefore)

        assertThat(graph.restoreHabit(id)).isEqualTo(RestoreHabitResult.Restored)
        assertThat(graph.db.habitDao().get(id)?.active).isTrue()
        assertThat(instances(id).single().status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(graph.db.habitDao().observeArchived().first()).isEmpty()
    }

    @Test
    fun undoPutsBackExactlyWhatWasRemoved() = runTest {
        val (id, _) = completedYesterdayOpenToday()
        val habitBefore = graph.db.habitDao().get(id)
        val instanceBefore = instances(id).single()

        val removal = (graph.removeHabit.delete(id) as RemoveHabitResult.Removed).removal
        graph.restoreHabit.undo(removal)

        assertThat(graph.db.habitDao().get(id)).isEqualTo(habitBefore)
        assertThat(instances(id)).containsExactly(instanceBefore)
    }

    @Test
    fun todaysFinishedInstanceStaysWhenTheHabitIsDeleted() = runTest {
        graph.seedHunter()
        graph.rollover()
        val id = habit()
        val today = instances(id).single()
        graph.completeQuest(today.id)

        graph.removeHabit.delete(id)

        assertThat(graph.db.questDao().get(today.id)?.status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun restoringPastTheActiveLimitIsRefused() = runTest {
        graph.seedHunter()
        graph.rollover()
        val archived = habit("Archived one")
        graph.removeHabit.archive(archived)
        repeat(QuestRules.MAX_ACTIVE_CUSTOM) { habit("Habit $it") }

        assertThat(graph.restoreHabit(archived)).isEqualTo(RestoreHabitResult.LimitReached)
        assertThat(graph.db.habitDao().get(archived)?.active).isFalse()
    }

    @Test
    fun programHabitsCannotBeRemovedByHand() = runTest {
        graph.seedHunter()
        graph.rollover()
        val id = habit()
        graph.db.habitDao().upsert(checkNotNull(graph.db.habitDao().get(id)).copy(programId = "p1"))

        assertThat(graph.removeHabit.delete(id)).isEqualTo(RemoveHabitResult.ProgramHabit)
        assertThat(graph.db.habitDao().get(id)).isNotNull()
        assertThat(graph.db.questDao().getForDate(graph.today()).count { it.type == QuestType.HABIT && it.habitId == id }).isEqualTo(1)
    }
}
