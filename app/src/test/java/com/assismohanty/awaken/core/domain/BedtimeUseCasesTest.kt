package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.PickupKind
import com.assismohanty.awaken.engine.ScreenEvent
import com.assismohanty.awaken.engine.ScreenEventType
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.FakeUsageGateway.Companion.INSTAGRAM
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Pickups and verified phone-down nights end to end on a real in-memory database (GD §16.10-16.12, vectors #113-#121). */
@RunWith(AndroidJUnit4::class)
class BedtimeUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val day1 = LocalDate.parse("2026-10-18")
    private val day2 = day1.plusDays(1)
    private val clockApp = "com.google.android.deskclock"

    @After
    fun tearDown() = graph.close()

    /** Turned on during day 1, so the first counted night ends on day 2's morning; the clock is then day 2 at 10:00. */
    private suspend fun nightBehindUs() {
        graph.seedHunter()
        graph.rollover()
        assertThat(graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE))
            .isEqualTo(SetBedtimeResult.Saved(day2))
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
    }

    private suspend fun quest(date: LocalDate) = graph.bedtimeReader.quest(date)

    private suspend fun xpRows() = graph.db.ledgerDao().all().filter { it.kind == LedgerKind.XP_TOTAL }

    @Test
    fun turningItOnCountsTonightAndTheQuestBelongsToTheWakeDay() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE)

        graph.generateDirective(day1)
        assertThat(quest(day1)).isNull()

        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        val bedtime = checkNotNull(quest(day2))
        assertThat(bedtime.type).isEqualTo(QuestType.BEDTIME)
        assertThat(bedtime.status).isEqualTo(QuestStatus.ACTIVE)
        graph.generateDirective(day2)
        assertThat(graph.db.questDao().getForDate(day2).count { it.type == QuestType.BEDTIME }).isEqualTo(1)
    }

    @Test
    fun vector117_aKeptNightPaysTheQuestOnTheWakeDay() = runTest {
        nightBehindUs()
        graph.usage.minutes[INSTAGRAM] = 3

        val result = graph.settleCurrentBedtime()

        assertThat(result).isInstanceOf(CompleteQuestResult.Completed::class.java)
        val quest = checkNotNull(quest(day2))
        assertThat(quest.status).isEqualTo(QuestStatus.COMPLETED)
        val completion = checkNotNull(graph.db.completionDao().latestActive(quest.id))
        assertThat(completion.verificationSource).isEqualTo(VerificationSource.USAGE)
        assertThat(completion.gameDate).isEqualTo(day2)
        // 78 XP at streak 0 without a crit (vector #117); the quest can crit like any other.
        assertThat(completion.xp).isEqualTo(XpCalculator.questXp(BedtimeRules.BEDTIME_DIFFICULTY, 0, VerificationSource.USAGE, completion.crit))
        if (!completion.crit) assertThat(completion.xp).isEqualTo(78)
        assertThat(xpRows().any { it.sourceType == SourceType.QUEST && it.sourceId.startsWith(quest.id) }).isTrue()
    }

    @Test
    fun vector118_aBrokenNightExpiresWithNothingDeducted() = runTest {
        nightBehindUs()
        graph.usage.minutes[INSTAGRAM] = 6
        val before = graph.db.ledgerDao().all().size

        assertThat(graph.settleCurrentBedtime()).isNull()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(checkNotNull(quest(day2)).nightVerdict).isEqualTo(com.assismohanty.awaken.engine.BedtimeVerdict.BROKEN)
        assertThat(graph.db.ledgerDao().all()).hasSize(before)
        assertThat(graph.db.ledgerDao().all().none { it.delta < 0 && it.kind == LedgerKind.XP_TOTAL }).isTrue()
    }

    @Test
    fun vector119_aClockAppNeverBreaksTheNight() = runTest {
        nightBehindUs()
        graph.usage.nightAllowed += clockApp
        graph.usage.minutes[clockApp] = 12

        graph.settleCurrentBedtime()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun vector120_usageThatCannotBeReadIsNeverPaid() = runTest {
        nightBehindUs()
        graph.usage.readable = false

        graph.settleCurrentBedtime()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(checkNotNull(quest(day2)).nightVerdict).isEqualTo(com.assismohanty.awaken.engine.BedtimeVerdict.NO_DATA)
        assertThat(xpRows().none { it.sourceType == SourceType.QUEST && it.sourceId.startsWith(quest(day2)?.id.orEmpty()) }).isTrue()
    }

    @Test
    fun losingUsageAccessBeforeSettlementIsAlsoUnpaid() = runTest {
        nightBehindUs()
        graph.usage.access = false

        graph.settleCurrentBedtime()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.EXPIRED)
    }

    @Test
    fun aNightSoQuietThatTheWindowHasNoEventsIsKeptWhenTheLogIsAlive() = runTest {
        nightBehindUs()
        graph.usage.idle = true
        graph.usage.screenEvents += ScreenEvent(ScreenEventType.SCREEN_NON_INTERACTIVE, Instant.parse("2026-10-18T22:00:00Z"))

        graph.settleCurrentBedtime()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun anIdleWindowWithADeadLogIsNotKept() = runTest {
        nightBehindUs()
        graph.usage.idle = true // no events anywhere

        graph.settleCurrentBedtime()

        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.EXPIRED)
    }

    @Test
    fun withoutUsageAccessThereIsNoQuestThatDay() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE)
        graph.usage.access = false
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()

        assertThat(quest(day2)).isNull()
    }

    @Test
    fun theNightIsNotSettledWhileItIsStillRunning() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE)
        graph.clock.advance(Duration.ofHours(19)) // day 2, 05:00: after the day start, before the 06:30 wake time
        graph.rollover()
        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.ACTIVE)

        assertThat(graph.settleCurrentBedtime()).isNull()
        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.ACTIVE)

        graph.clock.advance(Duration.ofHours(2))
        graph.settleCurrentBedtime()
        assertThat(checkNotNull(quest(day2)).status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun settlingTwiceNeverPaysTwice() = runTest {
        nightBehindUs()
        graph.settleCurrentBedtime()
        val rows = graph.db.ledgerDao().all().size

        graph.settleCurrentBedtime()
        graph.settleBedtime(day2)

        assertThat(graph.db.ledgerDao().all()).hasSize(rows)
    }

    @Test
    fun vector121_anEditReachesTheNightAfterTonightNotTonight() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE) // tonight counts: from day 2
        // Edited at 22:00 the same day.
        graph.clock.advance(Duration.ofHours(12))
        assertThat(graph.setBedtime.enableOrEdit(0, BedtimeRules.DEFAULT_WAKE)).isEqualTo(SetBedtimeResult.Saved(day1.plusDays(2)))

        assertThat(graph.bedtimeReader.settingFor(day2)?.bedtimeMinute).isEqualTo(BedtimeRules.DEFAULT_BEDTIME)
        assertThat(graph.bedtimeReader.settingFor(day1.plusDays(2))?.bedtimeMinute).isEqualTo(0)
    }

    @Test
    fun anInvalidWindowIsRefused() = runTest {
        graph.seedHunter()
        assertThat(graph.setBedtime.enableOrEdit(2 * 60, 4 * 60 + 30)).isEqualTo(SetBedtimeResult.InvalidWindow)
        assertThat(graph.setBedtime.enableOrEdit(23 * 60 + 5, 6 * 60)).isEqualTo(SetBedtimeResult.InvalidWindow)
        assertThat(graph.db.screenTimeDao().allBedtime()).isEmpty()
    }

    @Test
    fun turningItOffStopsTonightAndDropsPendingEdits() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.setBedtime.enableOrEdit(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE)
        graph.setBedtime.enableOrEdit(0, BedtimeRules.DEFAULT_WAKE) // pending, from day 3

        graph.setBedtime.disable()

        assertThat(graph.bedtimeReader.settingFor(day2)).isNull()
        assertThat(graph.bedtimeReader.settingFor(day1.plusDays(5))).isNull()
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        assertThat(quest(day2)).isNull()
    }

    @Test
    fun theEveningCardOffersTonightAndTheMorningCardShowsTheResult() = runTest {
        nightBehindUs()
        val before = checkNotNull(graph.loadBedtimeToday())
        assertThat(before.tonight).isNotNull()
        assertThat(before.lastNight).isEqualTo(LastNight.Pending)

        graph.settleCurrentBedtime()
        assertThat(checkNotNull(graph.loadBedtimeToday()).lastNight).isInstanceOf(LastNight.Kept::class.java)

        graph.usage.access = false
        assertThat(checkNotNull(graph.loadBedtimeToday()).needsAccess).isTrue()
    }

    @Test
    fun aBrokenNightShowsAsBrokenEvenOnceTheLogCannotBeRead() = runTest {
        nightBehindUs()
        graph.usage.minutes[INSTAGRAM] = 9
        graph.settleCurrentBedtime()
        assertThat(checkNotNull(graph.loadBedtimeToday()).lastNight).isEqualTo(LastNight.Broken)

        // The verdict was stored when the night settled (ENGINE_DECISIONS #147), so a log that turns unreadable later
        // does not rewrite it.
        graph.usage.minutes.clear()
        graph.usage.readable = false
        assertThat(checkNotNull(graph.loadBedtimeToday()).lastNight).isEqualTo(LastNight.Broken)
    }

    @Test
    fun aNightThatCouldNotBeReadShowsAsNoData() = runTest {
        nightBehindUs()
        graph.usage.readable = false
        graph.settleCurrentBedtime()

        assertThat(checkNotNull(graph.loadBedtimeToday()).lastNight).isEqualTo(LastNight.NoData)
    }

    @Test
    fun vector113_thePickupsCardReadsTheDevicesEvents() = runTest {
        graph.seedHunter()
        graph.clock.instant = Instant.parse("2026-10-18T13:00:00Z")
        fun at(h: Int, m: Int, date: String = "2026-10-18") = Instant.parse("${date}T%02d:%02d:00Z".format(h, m))
        graph.usage.screenEvents += listOf(
            ScreenEvent(ScreenEventType.KEYGUARD_HIDDEN, at(7, 10)),
            ScreenEvent(ScreenEventType.KEYGUARD_HIDDEN, at(8, 0)),
            ScreenEvent(ScreenEventType.SCREEN_NON_INTERACTIVE, at(8, 5)),
            ScreenEvent(ScreenEventType.KEYGUARD_HIDDEN, at(12, 30)),
            ScreenEvent(ScreenEventType.FOREGROUND, at(23, 48, "2026-10-17")),
        )
        val load = LoadPickupsUseCase(graph.db.hunterDao(), graph.usage, graph.clock)

        val state = load() as PickupsState.Data

        assertThat(state.summary.kind).isEqualTo(PickupKind.PICKUPS)
        assertThat(state.summary.count).isEqualTo(3)
        assertThat(state.summary.firstPickup).isEqualTo(at(7, 10))
        assertThat(state.summary.lastUseYesterday).isEqualTo(at(23, 48, "2026-10-17"))
        assertThat(state.summary.longestStretch).isEqualTo(Duration.ofHours(4).plusMinutes(25))
    }

    @Test
    fun vector115_withoutAccessOrALogThereAreNoNumbers() = runTest {
        graph.seedHunter()
        val load = LoadPickupsUseCase(graph.db.hunterDao(), graph.usage, graph.clock)

        graph.usage.access = false
        assertThat(load()).isEqualTo(PickupsState.NoAccess)

        graph.usage.access = true
        graph.usage.readable = false
        assertThat(load()).isEqualTo(PickupsState.Unavailable)
    }

    @Test
    fun theStepGoalSnapsToTheGridAndCreatesNoLedgerRows() = runTest {
        graph.seedHunter()
        assertThat(checkNotNull(graph.db.hunterDao().getProfile()).stepGoal).isEqualTo(8_000)
        val rows = graph.db.ledgerDao().all().size

        graph.updateSettings.setStepGoal(8_700)
        assertThat(checkNotNull(graph.db.hunterDao().getProfile()).stepGoal).isEqualTo(8_500)
        graph.updateSettings.setStepGoal(99_999)
        assertThat(checkNotNull(graph.db.hunterDao().getProfile()).stepGoal).isEqualTo(30_000)

        assertThat(graph.db.ledgerDao().all()).hasSize(rows)
    }
}
