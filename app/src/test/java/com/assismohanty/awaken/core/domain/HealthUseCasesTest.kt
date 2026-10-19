package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.HealthDay
import com.assismohanty.awaken.engine.HealthSession
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 11: Health Connect sync on a real in-memory database with a fake gateway (ARCH §3.4, ENGINE_DECISIONS #50–#52). */
@RunWith(AndroidJUnit4::class)
class HealthUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00

    @After
    fun tearDown() = graph.close()

    private suspend fun connected() {
        graph.seedHunter()
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.Enabled)
    }

    private suspend fun profile() = checkNotNull(graph.db.hunterDao().getProfile())

    private suspend fun status(id: String) = graph.db.questDao().get(id)?.status

    @Test
    fun aStepsQuestCompletesVerifiedWhenTheTargetIsMet() = runTest {
        connected()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 7_000)
        graph.health.day = HealthDay(steps = 7_200)

        val result = graph.healthSync() as HealthSyncResult.Synced

        assertThat(result.completed).hasSize(1)
        assertThat(result.completed.single().verification).isEqualTo(VerificationSource.HEALTH)
        assertThat(result.completed.single().undoable).isFalse()
        assertThat(status(quest.id)).isEqualTo(QuestStatus.COMPLETED)
        assertThat(graph.ledger.balances().xpTotal).isEqualTo(result.completed.single().reward.xp.toLong())
        assertThat(graph.health.lastWindow?.start).isEqualTo(Instant.parse("2026-10-18T04:00:00Z"))
        assertThat(graph.completionCenter.events.first().filterIsInstance<CompletionEvent.Completed>()).hasSize(1)
    }

    @Test
    fun belowTheTargetNothingHappens() = runTest {
        connected()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 7_000)
        graph.health.day = HealthDay(steps = 6_999)

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.Synced(emptyList()))
        assertThat(status(quest.id)).isEqualTo(QuestStatus.ACTIVE)
        assertThat(graph.ledger.balances().xpTotal).isEqualTo(0)
    }

    @Test
    fun syncingAgainIsANoOp() = runTest {
        connected()
        graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 7_000)
        graph.health.day = HealthDay(steps = 9_000)
        graph.healthSync()
        val after = graph.ledger.balances()

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.Synced(emptyList()))
        assertThat(graph.ledger.balances()).isEqualTo(after)
    }

    @Test
    fun selfQuestsAreNeverTouched() = runTest {
        connected()
        val self = graph.fixtureQuest("pushups")
        graph.health.day = HealthDay(steps = 50_000)

        graph.healthSync()

        assertThat(status(self.id)).isEqualTo(QuestStatus.ACTIVE)
        // No open HEALTH quest, so Health Connect isn't even read.
        assertThat(graph.health.reads).isEqualTo(0)
    }

    @Test
    fun exerciseAndSleepUseTheirOwnWindows() = runTest {
        connected()
        val workout = graph.fixtureQuest("workout", verification = Verification.HEALTH_EXERCISE, target = 30)
        val sleep = graph.fixtureQuest("sleep", verification = Verification.HEALTH_SLEEP, target = 7)
        graph.health.day = HealthDay(
            exercise = listOf(HealthSession(Instant.parse("2026-10-18T07:00:00Z"), Instant.parse("2026-10-18T07:35:00Z"))),
            sleep = listOf(HealthSession(Instant.parse("2026-10-17T23:00:00Z"), Instant.parse("2026-10-18T06:30:00Z"))),
        )

        graph.healthSync()

        assertThat(status(workout.id)).isEqualTo(QuestStatus.COMPLETED)
        assertThat(status(sleep.id)).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun offMeansNothingIsRead() = runTest {
        graph.seedHunter()
        graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 10)

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.Disabled)
        assertThat(graph.health.reads).isEqualTo(0)
    }

    @Test
    fun aRevokedPermissionTurnsVerifiedQuestsOffWithoutNagging() = runTest {
        connected()
        graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 10)
        graph.health.granted = setOf("read.steps")

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.Lost)

        assertThat(profile().healthEnabled).isFalse()
        assertThat(graph.reader.hunterContext(profile(), graph.today()).healthAvailable).isFalse()
        assertThat(graph.health.reads).isEqualTo(0)
    }

    @Test
    fun uninstallingHealthConnectTurnsVerifiedQuestsOff() = runTest {
        connected()
        graph.health.status = HealthStatus.NOT_INSTALLED

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.Lost)
        assertThat(profile().healthEnabled).isFalse()
    }

    @Test
    fun aFailedReadChangesNothingAndKeepsHealthOn() = runTest {
        connected()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 10)
        graph.health.failReads = true

        assertThat(graph.healthSync()).isEqualTo(HealthSyncResult.ReadFailed)
        assertThat(status(quest.id)).isEqualTo(QuestStatus.ACTIVE)
        assertThat(profile().healthEnabled).isTrue()
    }

    @Test
    fun enablingNeedsAllThreePermissionsAndAnInstalledHealthConnect() = runTest {
        graph.seedHunter()
        graph.health.granted = setOf("read.steps", "read.sleep")
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.PermissionsMissing)
        assertThat(profile().healthEnabled).isFalse()

        graph.health.status = HealthStatus.UPDATE_REQUIRED
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.Unavailable)

        graph.health.status = HealthStatus.AVAILABLE
        graph.health.granted = graph.health.requiredPermissions
        assertThat(graph.enableHealth()).isEqualTo(EnableHealthResult.Enabled)
        assertThat(profile().healthEnabled).isTrue()
    }

    @Test
    fun disconnectingRevokesAndTurnsOff() = runTest {
        connected()

        graph.disableHealth()

        assertThat(graph.health.revoked).isTrue()
        assertThat(profile().healthEnabled).isFalse()
    }

    @Test
    fun aHealthCompletionCannotBeUndone() = runTest {
        connected()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 10)
        graph.health.day = HealthDay(steps = 100)
        graph.healthSync()

        assertThat(graph.undoCompletion(quest.id)).isEqualTo(UndoResult.HealthVerified)
        assertThat(status(quest.id)).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun healthQuestsStillCannotBeCompletedByHand() = runTest {
        connected()
        val quest = graph.fixtureQuest("walk", verification = Verification.HEALTH_STEPS, target = 10)

        assertThat(graph.completeQuest(quest.id)).isEqualTo(CompleteQuestResult.VerificationRequired)
    }

    @Test
    fun onboardingWithHealthGrantedOffersVerifiedQuestsFromDayOne() = runTest {
        graph.completeOnboarding(
            name = "Kael",
            focusStats = setOf(Stat.STR, Stat.VIT),
            training = Awakening.Answer.MID,
            study = Awakening.Answer.MID,
            sleep = Awakening.Answer.MID,
            reminderTime = LocalTime.of(7, 0),
            healthEnabled = true,
        )

        assertThat(profile().healthEnabled).isTrue()
        assertThat(graph.reader.hunterContext(profile(), graph.today()).healthAvailable).isTrue()
    }
}
