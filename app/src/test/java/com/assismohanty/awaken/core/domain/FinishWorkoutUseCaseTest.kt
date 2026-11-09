package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.engine.body.Equipment
import com.assismohanty.awaken.engine.body.Muscle
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Finishing a workout pays STR XP once, inside the caps, and deleting it reverses the rows (GD §18.4, G-1 to G-11). */
@RunWith(AndroidJUnit4::class)
class FinishWorkoutUseCaseTest {
    private val graph = TestGraph()
    private val gym = graph.gym

    @After
    fun tearDown() = graph.close()

    private suspend fun benchId() = gym.searchExercises("barbell bench press medium grip", setOf(Equipment.BARBELL), Muscle.CHEST).first().id

    /** A started workout with [count] bench sets (120 s planned rest), each logged at [kg] x [reps] [gapSec] apart. */
    private suspend fun logBench(count: Int, kg: Double = 60.0, reps: Int = 8, gapSec: Long = 95): String {
        val id = gym.start("Push")
        gym.addExercise(id, benchId())
        repeat(count - 1) { gym.addSet(id, 0) }
        gym.setRest(id, 0, 120)
        graph.db.gymDao().sets(id).forEachIndexed { index, set ->
            if (index > 0) graph.clock.advance(Duration.ofSeconds(gapSec))
            gym.setWeight(set.id, kg)
            gym.setReps(set.id, reps)
            gym.completeSet(set.id)
        }
        return id
    }

    private suspend fun gymRows(): List<LedgerEntryEntity> = graph.db.ledgerDao().all().filter { it.sourceType == SourceType.GYM_WORKOUT }

    private suspend fun xpOf(id: String) = (graph.finishWorkout(id) as FinishResult.Finished).xp

    @Test
    fun threeSetsAfterTheMinimumRestPayFifteenXpAsTwoRowsAndFinishingTwiceIsOneSetOfRows() = runTest {
        graph.seedHunter()
        val id = logBench(3)

        val paid = xpOf(id)
        graph.finishWorkout(id)

        assertThat(paid.xp).isEqualTo(15)
        assertThat(paid.fullSets).isEqualTo(3)
        val rows = gymRows()
        assertThat(rows.map { it.kind }).containsExactly(LedgerKind.XP_TOTAL, LedgerKind.XP_STAT)
        assertThat(rows.single { it.kind == LedgerKind.XP_STAT }.stat).isEqualTo("STR")
        assertThat(rows.single { it.kind == LedgerKind.XP_TOTAL }.xpSource).isEqualTo("SELF")
        assertThat(graph.ledger.balances()).isEqualTo(graph.ledger.balancesFromLedger())
    }

    @Test
    fun aSetTooSoonAfterTheLastOneDoesNotCount() = runTest {
        graph.seedHunter()
        val id = logBench(2, gapSec = 20)

        val paid = xpOf(id)

        assertThat(paid.xp).isEqualTo(5)
    }

    @Test
    fun theSelfReportCapHoldsGymXpBack() = runTest {
        graph.seedHunter()
        graph.ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = LedgerKind.XP_TOTAL, delta = 598, sourceType = SourceType.QUEST, sourceId = "q-1",
                    gameDate = gym.today(), xpSource = "SELF", createdAt = graph.clock.now(),
                ),
            ),
        )
        val id = logBench(3)

        val paid = xpOf(id)

        assertThat(paid.xp).isEqualTo(2)
        assertThat(paid.capped).isTrue()
    }

    @Test
    fun aSetFarAboveTheConfirmedBestIsSavedButPaysNothingUntilALaterDayRepeatsIt() = runTest {
        graph.seedHunter()
        // Two earlier sessions at 60 kg x 5 (estimate 70), then 100 kg x 5 (estimate 116.7, over 1.2 x).
        repeat(2) {
            assertThat(xpOf(logBench(1, kg = 60.0, reps = 5)).xp).isEqualTo(5)
            graph.clock.advance(Duration.ofDays(1))
        }
        val jump = logBench(1, kg = 100.0, reps = 5)

        val flagged = xpOf(jump)

        assertThat(flagged.xp).isEqualTo(0)
        assertThat(flagged.unconfirmedSets).isEqualTo(1)
        assertThat(gymRows().filter { it.sourceId == jump }).isEmpty()
        // The same lift on a later game-day confirms it, and the day it was flagged is never paid after the fact.
        graph.clock.advance(Duration.ofDays(1))
        val repeat = xpOf(logBench(1, kg = 100.0, reps = 5))
        assertThat(repeat.unconfirmedSets).isEqualTo(0)
        assertThat(repeat.xp).isEqualTo(5)
        assertThat(gymRows().filter { it.sourceId == jump }).isEmpty()
    }

    @Test
    fun deletingAFinishedWorkoutReversesItsRowsAndFreesTheDailyCap() = runTest {
        graph.seedHunter()
        val id = logBench(3)
        graph.finishWorkout(id)
        val day = gym.today()
        assertThat(graph.db.ledgerDao().netGymXp(day)).isEqualTo(15)

        graph.deleteWorkout(id)

        val rows = graph.db.ledgerDao().all().filter { it.sourceId == id }
        assertThat(rows.filter { it.sourceType == SourceType.GYM_WORKOUT }).hasSize(2)
        assertThat(rows.filter { it.sourceType == SourceType.UNDO }).hasSize(2)
        assertThat(graph.db.ledgerDao().netGymXp(day)).isEqualTo(0)
        assertThat(graph.ledger.balances().xpTotal).isEqualTo(0)
        assertThat(graph.ledger.balances()).isEqualTo(graph.ledger.balancesFromLedger())
        assertThat(graph.db.gymDao().workout(id)).isNull()
    }

    @Test
    fun anEmptyWorkoutIsDroppedAndWritesNothing() = runTest {
        graph.seedHunter()
        val id = gym.start("Nothing")

        val result = graph.finishWorkout(id)

        assertThat(result).isEqualTo(FinishResult.DiscardedEmpty)
        assertThat(gymRows()).isEmpty()
    }
}
