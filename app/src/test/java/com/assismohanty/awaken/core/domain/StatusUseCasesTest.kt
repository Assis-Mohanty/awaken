package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.repo.Chapter
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.quest.newlyUnlockedChapters
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 9: Status reads (PRD F12, F16) on a real in-memory database. The test clock's game-day is a Sunday. */
@RunWith(AndroidJUnit4::class)
class StatusUseCasesTest {
    private val graph = TestGraph()
    private val observeStatus = ObserveStatusUseCase(
        graph.observeHunterState, graph.db.ledgerDao(), graph.db.completionDao(), graph.db.dayRecordDao(),
        graph.db.achievementDao(), graph.db.questDao(), graph.db.hunterDao(), graph.narrative,
    )
    private val equipTitle = EquipTitleUseCase(graph.db.hunterDao(), graph.db.achievementDao())

    @After
    fun tearDown() = graph.close()

    private suspend fun status(): StatusModel = observeStatus().filterNotNull().first()

    private suspend fun statXp(stat: Stat, xp: Long, date: LocalDate, sourceId: String) {
        graph.ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = LedgerKind.XP_STAT, stat = stat.name, delta = xp, sourceType = SourceType.QUEST,
                    sourceId = sourceId, gameDate = date, createdAt = graph.clock.now(),
                ),
            ),
        )
    }

    @Test
    fun lastWeekRadarExcludesThisWeeksRows() = runTest {
        graph.seedHunter()
        val today = graph.today()
        val monday = GameWeek.of(today).monday
        statXp(Stat.STR, 160, monday.minusDays(1), "last-week")
        statXp(Stat.STR, 3_080, today, "this-week")

        val model = status()

        assertThat(model.lastWeekLevels.getValue(Stat.STR)).isEqualTo(3)
        assertThat(model.stats.first { it.stat == Stat.STR }.level).isEqualTo(10)
        assertThat(model.radarMax).isEqualTo(20)
        assertThat(model.weeklyReport.statXpGained.getValue(Stat.STR)).isEqualTo(3_080)
    }

    @Test
    fun undoneCompletionsNetOutOfHeatmapAndWeeklyReport() = runTest {
        graph.seedHunter()
        val undone = graph.fixtureQuest("undone")
        graph.completeQuest(undone.id)
        graph.undoCompletion(undone.id)

        val afterUndo = status()
        assertThat(afterUndo.heatmap.first { it.date == graph.today() }.completions).isEqualTo(0)
        assertThat(afterUndo.weeklyReport.statXpGained.getValue(Stat.STR)).isEqualTo(0)
        assertThat(afterUndo.weeklyReport.questsCompleted).isEqualTo(0)

        val kept = graph.fixtureQuest("kept")
        graph.completeQuest(kept.id)

        val model = status()
        val day = model.heatmap.first { it.date == graph.today() }
        assertThat(day.completions).isEqualTo(1)
        assertThat(day.intensity).isEqualTo(1)
        assertThat(model.weeklyReport.questsCompleted).isEqualTo(1)
        assertThat(model.weeklyReport.bestDay).isEqualTo(graph.today())
    }

    @Test
    fun heatmapCoversEighteenWeeksEndingThisWeek() = runTest {
        graph.seedHunter()
        val heatmap = status().heatmap
        assertThat(heatmap).hasSize(18 * 7)
        assertThat(heatmap.last().date).isEqualTo(GameWeek.of(graph.today()).sunday)
    }

    @Test
    fun onlyTitlesFromUnlockedAchievementsCanBeEquipped() = runTest {
        graph.seedHunter()
        graph.db.achievementDao().insert(AchievementUnlockEntity("awakened", graph.clock.now()))

        assertThat(equipTitle("veteran")).isEqualTo(EquipTitleResult.Locked)
        assertThat(equipTitle("no_such_title")).isEqualTo(EquipTitleResult.Locked)
        assertThat(graph.db.hunterDao().getProfile()?.equippedTitleId).isNull()

        assertThat(equipTitle("the_awakened")).isEqualTo(EquipTitleResult.Equipped)
        assertThat(status().hunter.equippedTitleId).isEqualTo("the_awakened")

        assertThat(equipTitle(null)).isEqualTo(EquipTitleResult.Unequipped)
        assertThat(graph.db.hunterDao().getProfile()?.equippedTitleId).isNull()
    }

    @Test
    fun achievementsAndChaptersReflectUnlocks() = runTest {
        graph.seedHunter()
        graph.db.achievementDao().insert(AchievementUnlockEntity("first_blood", graph.clock.now()))

        val model = status()

        assertThat(model.achievements).hasSize(32)
        assertThat(model.achievements.filter { it.unlocked }.map { it.id }).containsExactly("first_blood")
        assertThat(model.chapters).hasSize(6)
        assertThat(model.chapters.filter { it.unlocked }.map { it.chapter.unlockLevel }).containsExactly(1)
    }

    @Test
    fun aLevelUpRevealsEveryChapterItCrossed() {
        val chapters = listOf(1, 5, 10, 20, 35, 50).map { Chapter("ch$it", it, "t$it", "b") }
        assertThat(newlyUnlockedChapters(chapters, 4, 5).map { it.index }).containsExactly(1)
        assertThat(newlyUnlockedChapters(chapters, 5, 6)).isEmpty()
        assertThat(newlyUnlockedChapters(chapters, 1, 12).map { it.value.unlockLevel }).containsExactly(5, 10).inOrder()
    }
}
