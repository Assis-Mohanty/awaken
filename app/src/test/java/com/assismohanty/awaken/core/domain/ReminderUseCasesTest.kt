package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 12: what the two daily notifications say, and when they stay quiet (ARCH §3.5, ENGINE_DECISIONS #53-#54). */
@RunWith(AndroidJUnit4::class)
class ReminderUseCasesTest {
    private val graph = TestGraph() // game-day 2026-10-18, 10:00 UTC

    @After
    fun tearDown() = graph.close()

    private suspend fun hunterWithReminder(time: LocalTime? = LocalTime.of(7, 0)) {
        graph.seedHunter()
        graph.updateSettings.setReminder(time)
    }

    @Test
    fun theDirectiveNotificationCountsMainSidesAndHabits() = runTest {
        hunterWithReminder()
        graph.generateDirective(graph.today())

        val content = graph.buildReminder.directive() as ReminderContent.Directive

        val expected = graph.db.questDao().getForDate(graph.today()).count { it.type in setOf(QuestType.MAIN, QuestType.SIDE, QuestType.HABIT) }
        assertThat(content.count).isEqualTo(expected)
        assertThat(content.count).isAtLeast(4)
    }

    @Test
    fun theDirectiveNotificationRunsTheRolloverSoTheDirectiveExists() = runTest {
        hunterWithReminder()
        // Nothing generated yet: the rollover the notification triggers creates today's directive.
        assertThat(graph.buildReminder.directive()).isInstanceOf(ReminderContent.Directive::class.java)
        assertThat(graph.db.questDao().getForDate(graph.today())).isNotEmpty()
    }

    @Test
    fun reminderOffSilencesBothNotifications() = runTest {
        hunterWithReminder(time = null)
        graph.fixtureQuest("a")

        assertThat(graph.buildReminder.directive()).isNull()
        assertThat(graph.buildReminder.lastCall()).isNull()
    }

    @Test
    fun noHunterNothingToSay() = runTest {
        assertThat(graph.buildReminder.directive()).isNull()
        assertThat(graph.buildReminder.lastCall()).isNull()
    }

    @Test
    fun theLastCallCountsHowManyQuestsClearTheDay() = runTest {
        hunterWithReminder()
        val a = graph.fixtureQuest("a")
        graph.fixtureQuest("b")
        graph.fixtureQuest("c")

        assertThat(graph.buildReminder.lastCall()).isEqualTo(ReminderContent.LastCall(3))
        graph.completeQuest(a.id)
        assertThat(graph.buildReminder.lastCall()).isEqualTo(ReminderContent.LastCall(2))
    }

    @Test
    fun anOpenMainQuestMeansOneQuestClearsTheDay() = runTest {
        hunterWithReminder()
        graph.fixtureQuest("main", type = QuestType.MAIN)
        graph.fixtureQuest("side")

        assertThat(graph.buildReminder.lastCall()).isEqualTo(ReminderContent.LastCall(1))
    }

    @Test
    fun aClearedDayGetsNoLastCall() = runTest {
        hunterWithReminder()
        val main = graph.fixtureQuest("main", type = QuestType.MAIN)
        graph.fixtureQuest("side")
        graph.completeQuest(main.id)

        assertThat(graph.buildReminder.lastCall()).isNull()
    }

    @Test
    fun aRestDayGetsNoLastCall() = runTest {
        hunterWithReminder()
        graph.fixtureQuest("side")
        graph.db.dayRecordDao().upsert(DayRecordEntity(gameDate = graph.today(), restDay = true))

        assertThat(graph.buildReminder.lastCall()).isNull()
    }

    @Test
    fun aReminderAtOrAfterNinePmHasNoLastCall() = runTest {
        hunterWithReminder(LocalTime.of(21, 0))
        graph.fixtureQuest("side")

        assertThat(graph.buildReminder.lastCall()).isNull()
    }

    @Test
    fun nothingOpenMeansNothingToNudge() = runTest {
        hunterWithReminder()

        assertThat(graph.buildReminder.lastCall()).isNull()
    }
}
