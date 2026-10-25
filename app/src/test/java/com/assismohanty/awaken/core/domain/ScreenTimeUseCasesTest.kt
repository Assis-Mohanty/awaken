package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.CALCULATOR
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.DOCS
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.INSTAGRAM
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Screen Discipline end to end on a real in-memory database (GD §6.7, ENGINE_DECISIONS #86–#91). */
@RunWith(AndroidJUnit4::class)
class ScreenTimeUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val day1 = LocalDate.parse("2026-10-18")
    private val day2 = day1.plusDays(1)

    @After
    fun tearDown() = graph.close()

    private suspend fun enabledThenNextDay() {
        graph.seedHunter()
        graph.rollover()
        graph.setScreenTimeEnabled(true)
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
    }

    private suspend fun discipline(date: LocalDate) = graph.screenTimeReader.disciplineQuest(date)

    @Test
    fun enablingSeedsDefaultsAndStartsTomorrow() = runTest {
        graph.seedHunter()
        graph.rollover()

        graph.setScreenTimeEnabled(true)

        assertThat(checkNotNull(graph.db.hunterDao().getProfile()).screenTimeFrom).isEqualTo(day2)
        val rules = graph.db.screenTimeDao().allRules().associateBy { it.packageName }
        assertThat(rules.getValue(INSTAGRAM).category).isEqualTo(AppCategory.DISTRACTING)
        assertThat(rules.getValue(INSTAGRAM).dailyLimitMinutes).isEqualTo(30)
        assertThat(rules.getValue(DOCS).category).isEqualTo(AppCategory.PRODUCTIVE)
        assertThat(rules.getValue(CALCULATOR).category).isEqualTo(AppCategory.NEUTRAL)
        assertThat(rules.values.map { it.effectiveFrom }.toSet()).containsExactly(day2)
        // Nothing joins today's directive.
        graph.generateDirective(day1)
        assertThat(discipline(day1)).isNull()
    }

    @Test
    fun theNextDayGetsOneDisciplineQuest() = runTest {
        enabledThenNextDay()

        val quest = checkNotNull(discipline(day2))
        assertThat(quest.type).isEqualTo(QuestType.DISCIPLINE)
        assertThat(quest.stat).isEqualTo(Stat.WIL)
        assertThat(quest.verification).isEqualTo(Verification.DEVICE_USAGE)
        assertThat(quest.status).isEqualTo(QuestStatus.ACTIVE)
        graph.generateDirective(day2)
        assertThat(graph.db.questDao().getForDate(day2).count { it.type == QuestType.DISCIPLINE }).isEqualTo(1)
    }

    @Test
    fun withoutUsageAccessThereIsNoQuest() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setScreenTimeEnabled(true)
        graph.usage.access = false
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()

        assertThat(discipline(day2)).isNull()
    }

    @Test
    fun nearTheLimitWarnsAndCrossingForfeitsWithoutTouchingTheLedger() = runTest {
        enabledThenNextDay()
        val ledgerBefore = graph.ledger.balances()

        graph.usage.minutes[INSTAGRAM] = 26
        val near = checkNotNull(graph.disciplineSync())
        assertThat(near.warnings.map { it.packageName }).containsExactly(INSTAGRAM)
        assertThat(near.forfeitedNow).isFalse()
        assertThat(near.xpAtStake).isGreaterThan(0)

        graph.usage.minutes[INSTAGRAM] = 31
        val crossed = checkNotNull(graph.disciplineSync())
        assertThat(crossed.forfeitedNow).isTrue()
        assertThat(crossed.warnings).isEmpty()
        assertThat(discipline(day2)?.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.ledger.balances()).isEqualTo(ledgerBefore)

        // A later check reports the state but never forfeits twice.
        assertThat(checkNotNull(graph.disciplineSync()).forfeitedNow).isFalse()
    }

    @Test
    fun aHeldDayIsPaidAtRolloverOnItsOwnDay() = runTest {
        enabledThenNextDay()
        graph.usage.minutes[INSTAGRAM] = 30 // exactly the limit holds
        graph.usage.minutes[DOCS] = 300 // productive time never counts against anything
        val xpBefore = graph.ledger.balances().xpTotal

        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()

        val quest = checkNotNull(discipline(day2))
        assertThat(quest.status).isEqualTo(QuestStatus.COMPLETED)
        val rows = graph.db.ledgerDao().all().filter { it.kind == LedgerKind.XP_TOTAL && it.xpSource == "USAGE" }
        assertThat(rows).hasSize(1)
        assertThat(rows.single().gameDate).isEqualTo(day2)
        assertThat(rows.single().delta).isAnyOf(78L, 156L) // 40 × 1.5 × 1.30, doubled on a crit
        assertThat(graph.ledger.balances().xpTotal).isEqualTo(xpBefore + rows.single().delta)
        // Settling again (a second rollover) pays nothing more.
        graph.rollover()
        assertThat(graph.db.ledgerDao().all().count { it.kind == LedgerKind.XP_TOTAL && it.xpSource == "USAGE" }).isEqualTo(1)
    }

    @Test
    fun aCrossedDayIsNotPaidAtRollover() = runTest {
        enabledThenNextDay()
        graph.usage.minutes[INSTAGRAM] = 45

        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()

        assertThat(discipline(day2)?.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.db.ledgerDao().all().none { it.xpSource == "USAGE" }).isTrue()
    }

    @Test
    fun aManualCompletionIsRefused() = runTest {
        enabledThenNextDay()
        val quest = checkNotNull(discipline(day2))

        assertThat(graph.completeQuest(quest.id)).isEqualTo(CompleteQuestResult.VerificationRequired)
        // USAGE pays only for a day that has ended.
        assertThat(graph.completeQuest.completeVerified(quest.id, com.assismohanty.awaken.engine.model.VerificationSource.USAGE))
            .isEqualTo(CompleteQuestResult.WrongDay)
    }

    @Test
    fun anEditTodayKeepsTodaysLimit() = runTest {
        enabledThenNextDay()
        assertThat(graph.setAppRule(INSTAGRAM, AppCategory.DISTRACTING, 120)).isEqualTo(SetAppRuleResult.Saved)

        graph.usage.minutes[INSTAGRAM] = 40
        assertThat(checkNotNull(graph.disciplineSync()).forfeitedNow).isTrue()
        assertThat(graph.screenTimeReader.rulesInForce(day2.plusDays(1)).getValue(INSTAGRAM).dailyLimitMinutes).isEqualTo(120)
    }

    @Test
    fun invalidLimitsAreRefused() = runTest {
        enabledThenNextDay()
        assertThat(graph.setAppRule(INSTAGRAM, AppCategory.DISTRACTING, 33)).isEqualTo(SetAppRuleResult.InvalidLimit)
        assertThat(graph.setAppRule(INSTAGRAM, AppCategory.DISTRACTING, 300)).isEqualTo(SetAppRuleResult.InvalidLimit)
    }

    @Test
    fun turningItOffExpiresTodaysQuestAndStopsTheChecks() = runTest {
        enabledThenNextDay()

        graph.setScreenTimeEnabled(false)

        assertThat(discipline(day2)?.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.disciplineSync()).isNull()
    }

    @Test
    fun losingUsageAccessMidDayExpiresTheQuest() = runTest {
        enabledThenNextDay()
        graph.usage.access = false

        val check = checkNotNull(graph.disciplineSync())

        assertThat(check.forfeitedNow).isFalse()
        assertThat(discipline(day2)?.status).isEqualTo(QuestStatus.EXPIRED)
    }

    @Test
    fun aHeldDiscplineQuestCountsTowardClearingTheDay() = runTest {
        enabledThenNextDay()
        val record = graph.db.questDao().getForDate(day2).map {
            if (it.type == QuestType.DISCIPLINE) it.copy(status = QuestStatus.COMPLETED) else it
        }.toDayRecord(day2)
        assertThat(record.itemsCompleted).isEqualTo(1)
    }

    @Test
    fun theOverviewGroupsTodayByCategoryWithoutChangingAnything() = runTest {
        enabledThenNextDay()
        graph.usage.minutes[INSTAGRAM] = 31
        graph.usage.minutes[DOCS] = 50
        graph.setAppRule(INSTAGRAM, AppCategory.DISTRACTING, 60)

        val overview = checkNotNull(graph.loadScreenTime())

        assertThat(overview.hasAccess).isTrue()
        assertThat(overview.totalMinutes).isEqualTo(81)
        assertThat(overview.minutesByCategory[AppCategory.DISTRACTING]).isEqualTo(31)
        assertThat(overview.minutesByCategory[AppCategory.PRODUCTIVE]).isEqualTo(50)
        assertThat(overview.status).isEqualTo(DisciplineStatus.FORFEITED)
        val insta = overview.apps.first { it.packageName == INSTAGRAM }
        assertThat(insta.limitMinutes).isEqualTo(30)
        assertThat(insta.hasPendingChange).isTrue()
        assertThat(insta.nextLimitMinutes).isEqualTo(60)
        assertThat(overview.trend).hasSize(7)
        // Read-only: the forfeit itself only happens in the sync.
        assertThat(discipline(day2)?.status).isEqualTo(QuestStatus.ACTIVE)
    }

    @Test
    fun theOverviewWithoutAccessShowsNoApps() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.usage.access = false

        val overview = checkNotNull(graph.loadScreenTime())

        assertThat(overview.hasAccess).isFalse()
        assertThat(overview.apps).isEmpty()
        assertThat(overview.status).isEqualTo(DisciplineStatus.OFF)
    }
}
