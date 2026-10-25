package com.assismohanty.awaken.core.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.INSTAGRAM
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** ARCH §3.11: the check warns once per app per day, announces a forfeit once, and arms the next warning check. */
@RunWith(AndroidJUnit4::class)
// A plain Application: the real AwakenApp would start its coordinators against the app's own database.
@Config(application = Application::class)
class ScreenTimeWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val graph = TestGraph() // 2026-10-18 10:00 UTC
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var checker: ScreenTimeChecker

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val alerts = ScreenTimeAlertStore(
            PreferenceDataStoreFactory.create(scope = storeScope) { File(context.filesDir, "alerts-${UUID.randomUUID()}.preferences_pb") },
        )
        checker = ScreenTimeChecker(context, graph.disciplineSync, alerts, ScreenTimeNotifications(context, NotificationAccess(context)), graph.usage)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        graph.close()
    }

    private suspend fun disciplineRunning() {
        graph.seedHunter()
        graph.rollover()
        graph.setScreenTimeEnabled(true)
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
    }

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    private fun posted(): List<String> = shadowOf(manager).allNotifications.map { shadowOf(it).contentTitle.toString() }

    @Test
    fun nearTheLimitWarnsOnceADay() = runBlocking {
        disciplineRunning()
        graph.usage.minutes[INSTAGRAM] = 26

        checker.run()
        assertThat(posted()).containsExactly("Instagram: almost at your limit")

        manager.cancelAll()
        graph.usage.minutes[INSTAGRAM] = 28
        checker.run()
        assertThat(posted()).isEmpty()
    }

    @Test
    fun crossingAnnouncesTheForfeitOnce() = runBlocking {
        disciplineRunning()
        graph.usage.minutes[INSTAGRAM] = 31

        checker.run()
        assertThat(posted()).containsExactly("Limit crossed")

        manager.cancelAll()
        graph.usage.minutes[INSTAGRAM] = 50
        checker.run()
        assertThat(posted()).isEmpty()
    }

    @Test
    fun theNextWarningCheckIsArmedForWhenItIsDue() = runBlocking {
        disciplineRunning()
        graph.usage.minutes[INSTAGRAM] = 20 // 10 min left: warn in 5 min

        checker.run()

        val armed = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ScreenTimeChecker.ONE_SHOT).get()
            .first { it.state != WorkInfo.State.CANCELLED }
        assertThat(armed.initialDelayMillis).isEqualTo(Duration.ofMinutes(5).toMillis())
        assertThat(posted()).isEmpty()
    }

    @Test
    fun nothingHappensWhileItIsOff() = runBlocking {
        graph.seedHunter()
        graph.rollover()
        graph.usage.minutes[INSTAGRAM] = 500

        assertThat(checker.run()).isNull()
        assertThat(posted()).isEmpty()
    }
}
