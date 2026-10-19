package com.assismohanty.awaken.core.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Phase 12: the reminders are armed with the right delays and the worker posts what the use case built. */
@RunWith(AndroidJUnit4::class)
class ReminderWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val graph = TestGraph() // 2026-10-18 10:00 UTC
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        scheduler = ReminderScheduler(context, graph.db.hunterDao(), graph.clock)
    }

    @After
    fun tearDown() = graph.close()

    private fun work(kind: ReminderKind): WorkInfo? =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(kind.workName).get().firstOrNull()

    private fun delayHours(info: WorkInfo): Double = info.initialDelayMillis / 3_600_000.0

    @Test
    fun bothRemindersAreArmedAtTheirNextLocalTimes() = runBlocking {
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))

        scheduler.reschedule()

        // 10:00 now: 07:00 is 21 h away (tomorrow), 21:00 is 11 h away (today).
        assertThat(delayHours(checkNotNull(work(ReminderKind.DIRECTIVE)))).isWithin(0.01).of(21.0)
        assertThat(delayHours(checkNotNull(work(ReminderKind.LAST_CALL)))).isWithin(0.01).of(11.0)
        assertThat(work(ReminderKind.DIRECTIVE)?.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun changingTheReminderReplacesTheArmedWork() = runBlocking {
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))
        scheduler.reschedule()

        graph.updateSettings.setReminder(LocalTime.of(12, 0))
        scheduler.reschedule()

        assertThat(delayHours(checkNotNull(work(ReminderKind.DIRECTIVE)))).isWithin(0.01).of(2.0)
    }

    @Test
    fun reminderOffCancelsBoth() = runBlocking {
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))
        scheduler.reschedule()

        graph.updateSettings.setReminder(null)
        scheduler.reschedule()

        ReminderKind.entries.forEach { assertThat(work(it)?.state).isAnyOf(null, WorkInfo.State.CANCELLED) }
    }

    @Test
    fun aReminderAtOrAfterNinePmHasNoLastCallWork() = runBlocking {
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(21, 30))

        scheduler.reschedule()

        assertThat(delayHours(checkNotNull(work(ReminderKind.DIRECTIVE)))).isWithin(0.01).of(11.5)
        assertThat(work(ReminderKind.LAST_CALL)?.state).isAnyOf(null, WorkInfo.State.CANCELLED)
    }

    private fun worker(kind: ReminderKind): ReminderWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ReminderWorker(appContext, workerParameters, graph.buildReminder, ReminderNotifications(appContext, NotificationAccess(appContext)), scheduler)
        }
        return TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ReminderScheduler.KIND to kind.name))
            .build()
    }

    private fun postedNotifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun theDirectiveWorkerPostsTheCountAndReArmsTomorrow() = runBlocking {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))
        graph.generateDirective(graph.today())

        val result = worker(ReminderKind.DIRECTIVE).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val posted = postedNotifications().single()
        assertThat(posted.extras.getCharSequence("android.text").toString()).startsWith("Your directive is ready: ")
        assertThat(posted.channelId).isEqualTo("directive")
        assertThat(work(ReminderKind.DIRECTIVE)?.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun withoutThePermissionNothingIsPostedButTheNextRunIsArmed() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))
        graph.generateDirective(graph.today())

        worker(ReminderKind.DIRECTIVE).doWork()

        assertThat(postedNotifications()).isEmpty()
        assertThat(work(ReminderKind.DIRECTIVE)?.state).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun theLastCallWorkerSaysHowManyQuestsClearTheDay() = runBlocking {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        graph.seedHunter()
        graph.updateSettings.setReminder(LocalTime.of(7, 0))
        graph.fixtureQuest("a")
        graph.fixtureQuest("b")
        graph.fixtureQuest("c")

        worker(ReminderKind.LAST_CALL).doWork()

        val posted = postedNotifications().single()
        assertThat(posted.extras.getCharSequence("android.text").toString()).isEqualTo("3 quests from clearing the day.")
        assertThat(posted.channelId).isEqualTo("last_call")
    }

    @Test
    fun anUnknownKindIsIgnored() = runBlocking {
        val bad = TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    ReminderWorker(appContext, workerParameters, graph.buildReminder, ReminderNotifications(appContext, NotificationAccess(appContext)), scheduler)
            })
            .setInputData(workDataOf(ReminderScheduler.KIND to "NOPE"))
            .build()

        assertThat(bad.doWork()).isEqualTo(ListenableWorker.Result.success())
        assertThat(postedNotifications()).isEmpty()
    }

    @Test
    fun androidTenNeedsNoPermissionAndAndroidThirteenAsksUntilAllowed() {
        val access = NotificationAccess(context)
        assertThat(access.isGranted(sdkInt = 29)).isTrue()
        assertThat(access.canAsk(sdkInt = 29)).isFalse()

        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(access.isGranted(sdkInt = 33)).isFalse()
        assertThat(access.canAsk(sdkInt = 33)).isTrue()

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(access.isGranted(sdkInt = 33)).isTrue()
        assertThat(access.canAsk(sdkInt = 33)).isFalse()
    }
}
