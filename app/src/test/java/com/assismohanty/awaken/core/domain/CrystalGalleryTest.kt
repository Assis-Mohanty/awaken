package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.engine.CrystalForm
import com.assismohanty.awaken.engine.CrystalRules
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The crystal gallery reads finished gate runs and nothing else (GD §16.5): no tables, no ledger rows. */
@RunWith(AndroidJUnit4::class)
class CrystalGalleryTest {
    private val graph = TestGraph()
    private val start = Instant.parse("2026-10-18T08:00:00Z")

    @After
    fun tearDown() = graph.close()

    private fun run(
        id: String,
        status: GateRunStatus,
        mode: GateMode,
        plannedSec: Int,
        rank: GateRank = GateRank.D,
        endedAfter: Duration? = null,
        subjectId: String? = null,
        startedAt: Instant = start,
    ) = GateRunEntity(
        id = id, questInstanceId = "q-$id", gateRank = rank, stat = Stat.INT, label = null, plannedSec = plannedSec, startedAt = startedAt,
        endedAt = endedAfter?.let { startedAt.plus(it) }, bootCount = 1, status = status, mode = mode, subjectId = subjectId,
    )

    private suspend fun gallery() = ObserveCrystalGalleryUseCase(graph.db.gateRunDao(), graph.db.focusDao(), graph.db.hunterDao(), graph.clock)().first()

    @Test
    fun everyKindOfFinishedRunBecomesOneCrystalAndRunningOnesDoNot() = runTest {
        graph.seedHunter()
        graph.db.focusDao().insertSubject(FocusSubjectEntity("s1", "Maths", createdAt = start))
        val dao = graph.db.gateRunDao()
        dao.upsert(run("cleared", GateRunStatus.CLEARED, GateMode.PRESET, 25 * 60, GateRank.D, Duration.ofMinutes(25), subjectId = "s1", startedAt = start.plusSeconds(3)))
        dao.upsert(run("collapsed", GateRunStatus.COLLAPSED, GateMode.CUSTOM, 40 * 60, GateRank.D, Duration.ofMinutes(20), startedAt = start.plusSeconds(2)))
        dao.upsert(run("logged", GateRunStatus.LOGGED, GateMode.STOPWATCH, 9 * 60, GateRank.E, Duration.ofMinutes(9), startedAt = start.plusSeconds(1)))
        dao.upsert(run("running", GateRunStatus.RUNNING, GateMode.PRESET, 25 * 60, startedAt = start.plusSeconds(4)))
        dao.upsert(run("abandoned", GateRunStatus.ABANDONED, GateMode.PRESET, 25 * 60, endedAfter = Duration.ofMinutes(1), startedAt = start.plusSeconds(5)))

        val entries = gallery().associateBy { it.runId }

        assertThat(gallery().map { it.runId }).containsExactly("cleared", "collapsed", "logged").inOrder() // newest first
        val cleared = entries.getValue("cleared")
        assertThat(cleared.form).isEqualTo(CrystalForm.WHOLE)
        assertThat(cleared.stage).isEqualTo(5)
        assertThat(cleared.tier).isEqualTo(GateRank.D)
        assertThat(cleared.minutes).isEqualTo(25)
        assertThat(cleared.subjectName).isEqualTo("Maths")
        assertThat(cleared.colourIndex).isEqualTo(CrystalRules.colourIndex("s1"))
        val collapsed = entries.getValue("collapsed")
        assertThat(collapsed.form).isEqualTo(CrystalForm.CRACKED)
        assertThat(collapsed.stage).isEqualTo(2) // 20 of 40 minutes
        assertThat(collapsed.minutes).isEqualTo(20)
        assertThat(collapsed.tier).isEqualTo(GateRank.D) // a custom run takes the focus tier of its planned length
        val logged = entries.getValue("logged")
        assertThat(logged.form).isEqualTo(CrystalForm.SHARD)
        assertThat(logged.tier).isEqualTo(GateRank.E)
        assertThat(logged.colourIndex).isNull()
    }

    @Test
    fun readingTheGalleryWritesNothing() = runTest {
        graph.seedHunter()
        graph.db.gateRunDao().upsert(run("a", GateRunStatus.CLEARED, GateMode.PRESET, 25 * 60, endedAfter = Duration.ofMinutes(25)))
        val ledger = graph.db.ledgerDao().all().size

        gallery()

        assertThat(graph.db.ledgerDao().all()).hasSize(ledger)
    }

    @Test
    fun aCollapsedRunsPausedTimeDoesNotGrowTheCrystal() = runTest {
        graph.seedHunter()
        val paused = run("p", GateRunStatus.COLLAPSED, GateMode.PRESET, 50 * 60, GateRank.C, Duration.ofMinutes(30)).copy(pausedBeforeMs = Duration.ofMinutes(10).toMillis())
        graph.db.gateRunDao().upsert(paused)

        // 30 minutes of wall time minus 10 paused = 20 of 50 minutes = stage 2.
        assertThat(gallery().single().stage).isEqualTo(2)
    }
}
