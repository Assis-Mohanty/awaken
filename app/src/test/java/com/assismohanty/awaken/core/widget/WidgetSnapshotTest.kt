package com.assismohanty.awaken.core.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** ARCH §3.6: what the Today widget shows, and that the system can find it. */
@RunWith(AndroidJUnit4::class)
class WidgetSnapshotTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private suspend fun snapshot() = graph.observeToday(flowOf(Unit)).first().toWidgetSnapshot()

    @Test
    fun noHunterShowsTheOpenAwakenState() = runTest {
        assertThat(snapshot()).isEqualTo(WidgetSnapshot())
        assertThat(snapshot().hasHunter).isFalse()
    }

    @Test
    fun theCountMatchesTodayAndTheNextQuestIsTheMainFirst() = runTest {
        graph.seedHunter()
        graph.fixtureQuest("side one")
        graph.fixtureQuest("main quest", type = QuestType.MAIN)
        val done = graph.fixtureQuest("side two")
        graph.completeQuest(done.id)

        val shown = snapshot()

        assertThat(shown.hasHunter).isTrue()
        assertThat(shown.rank).isEqualTo(Rank.E)
        assertThat(shown.level).isAtLeast(1)
        assertThat(shown.completed).isEqualTo(1)
        assertThat(shown.total).isEqualTo(3)
        assertThat(shown.nextQuest).isEqualTo("main quest")
        assertThat(shown.cleared).isFalse()
    }

    @Test
    fun aPenaltyQuestComesBeforeEverythingElse() = runTest {
        graph.seedHunter()
        graph.fixtureQuest("main quest", type = QuestType.MAIN)
        graph.fixtureQuest("redemption", type = QuestType.PENALTY)

        assertThat(snapshot().nextQuest).isEqualTo("redemption")
    }

    @Test
    fun aClearedDayHasNoNextQuest() = runTest {
        graph.seedHunter()
        val main = graph.fixtureQuest("main quest", type = QuestType.MAIN)
        graph.completeQuest(main.id)

        val shown = snapshot()

        assertThat(shown.cleared).isTrue()
        assertThat(shown.nextQuest).isNull()
        assertThat(shown.completed).isEqualTo(1)
    }

    @Test
    fun completingAQuestChangesTheSnapshotSoTheWidgetRefreshes() = runTest {
        graph.seedHunter()
        val quest = graph.fixtureQuest("a")
        val before = snapshot()

        graph.completeQuest(quest.id)

        assertThat(snapshot()).isNotEqualTo(before)
        // Nothing changed since: identical snapshots are dropped by distinctUntilChanged, so no pointless redraw.
        assertThat(snapshot()).isEqualTo(snapshot())
    }

    @Test
    fun theSystemFindsTheWidgetReceiver() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName)

        val receivers = context.packageManager.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)

        val receiver = receivers.single { it.activityInfo.name == TodayWidgetReceiver::class.java.name }
        assertThat(receiver.activityInfo.exported).isTrue()
        assertThat(receiver.activityInfo.metaData.getInt("android.appwidget.provider")).isNotEqualTo(0)
    }
}
