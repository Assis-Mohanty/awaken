package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.engine.HabitProblem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.testing.FakeClock
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Habit types, flexible weeks, reminders and skips against a real database (GD §16.1-16.2, vectors #85-#95). */
@RunWith(AndroidJUnit4::class)
class HabitTypesTest {
    // 2026-10-19 is a Monday; the day starts at 04:00, so 10:00 UTC is morning of that game-day.
    private val graph = TestGraph(clock = FakeClock(Instant.parse("2026-10-19T10:00:00Z")))
    private val questDao = graph.db.questDao()

    @After
    fun tearDown() = graph.close()

    private fun draft(
        title: String = "Habit",
        type: HabitType = HabitType.CHECK,
        target: Int = 1,
        unit: String = "",
        frequency: HabitFrequency = HabitFrequency.DAYS_OF_WEEK,
        timesPerWeek: Int? = null,
        reminder: Int? = null,
    ) = HabitDraft(
        id = null, title = title, stat = Stat.WIL, difficulty = Difficulty.NORMAL, days = DayOfWeek.entries.toSet(),
        target = target, unit = unit, type = type, frequency = frequency, timesPerWeek = timesPerWeek, reminderMinutes = reminder,
    )

    private suspend fun save(draft: HabitDraft): String {
        graph.seedHunter()
        graph.rollover()
        return (graph.saveHabit(draft) as SaveHabitResult.Saved).habitId
    }

    private suspend fun instance(habitId: String) = questDao.habitInstanceOn(habitId, graph.today())

    private fun assertXp(outcome: CompletionOutcome, base: Int) {
        assertThat(outcome.reward.xp).isEqualTo(base)
    }

    private suspend fun xpRows() = graph.db.ledgerDao().all().filter { it.kind == LedgerKind.XP_TOTAL }

    private suspend fun nextDay() {
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
    }

    // --- count habits (#85, #86) ---

    @Test
    fun aCountHabitPaysOnlyWhenItReachesItsTarget() = runTest {
        val id = save(draft("Drink water", HabitType.QUANTITY, target = 8, unit = "glasses"))
        val quest = instance(id)!!

        repeat(7) { assertThat(graph.adjustQuantity.plus(quest.id)).isInstanceOf(HabitActionResult.Progressed::class.java) }
        assertThat(instance(id)?.progress).isEqualTo(7)
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(xpRows()).isEmpty()

        val result = graph.adjustQuantity.plus(quest.id) as HabitActionResult.Completed
        assertXp(result.outcome, base = 60) // SELF, Normal, streak 0
        assertThat(result.outcome.reward.gold).isEqualTo(Math.round(result.outcome.reward.xp * 0.5).toInt())
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(xpRows().map { it.sourceType }).containsExactly(SourceType.QUEST)
    }

    @Test
    fun aCountHabitCannotBeTickedByHand() = runTest {
        val id = save(draft("Drink water", HabitType.QUANTITY, target = 8, unit = "glasses"))

        assertThat(graph.completeQuest(instance(id)!!.id)).isEqualTo(CompleteQuestResult.VerificationRequired)
        assertThat(xpRows()).isEmpty()
    }

    @Test
    fun minusOnACompletedCountHabitIsAnOrdinaryUndo() = runTest {
        val id = save(draft("Drink water", HabitType.QUANTITY, target = 2, unit = "glasses"))
        val quest = instance(id)!!
        graph.adjustQuantity.plus(quest.id)
        graph.adjustQuantity.plus(quest.id)

        assertThat(graph.adjustQuantity.minus(quest.id)).isEqualTo(HabitActionResult.Progressed(1))

        assertThat(instance(id)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(instance(id)?.progress).isEqualTo(1)
        assertThat(xpRows().sumOf { it.delta }).isEqualTo(0L) // reward and its reversal net to nothing
        // The next tap completes it again.
        assertThat(graph.adjustQuantity.plus(quest.id)).isInstanceOf(HabitActionResult.Completed::class.java)
    }

    @Test
    fun minusStopsAtZeroAndIsNotAllowedOnADifferentType() = runTest {
        val count = save(draft("Water", HabitType.QUANTITY, target = 4, unit = "glasses"))
        val check = (graph.saveHabit(draft("Stretch")) as SaveHabitResult.Saved).habitId

        assertThat(graph.adjustQuantity.minus(instance(count)!!.id)).isEqualTo(HabitActionResult.NotAllowed)
        assertThat(graph.adjustQuantity.plus(instance(check)!!.id)).isEqualTo(HabitActionResult.NotAllowed)
    }

    @Test
    fun aCountTargetOutsideTwoToAHundredIsRejected() = runTest {
        graph.seedHunter()

        assertThat(graph.saveHabit(draft(type = HabitType.QUANTITY, target = 101)))
            .isEqualTo(SaveHabitResult.InvalidHabit(HabitProblem.QUANTITY_TARGET))
        assertThat(graph.saveHabit(draft(type = HabitType.QUANTITY, target = 1)))
            .isEqualTo(SaveHabitResult.InvalidHabit(HabitProblem.QUANTITY_TARGET))
    }

    // --- timed habits (#87-#89) ---

    @Test
    fun aTimedHabitDerivesItsDifficultyFromItsMinutesAndIsTimerVerified() = runTest {
        val id = save(draft("Read", HabitType.TIMED, target = 30))

        val habit = graph.db.habitDao().get(id)!!
        assertThat(habit.difficulty).isEqualTo(Difficulty.NORMAL)
        assertThat(habit.verification).isEqualTo(Verification.TIMER)
        assertThat(habit.unit).isEqualTo("min")
        // No manual completion: it needs its focus session.
        assertThat(graph.completeQuest(instance(id)!!.id)).isEqualTo(CompleteQuestResult.VerificationRequired)
    }

    @Test
    fun aClearedTimedSessionPaysTheHabitOnceAndNotAsACustomRun() = runTest {
        val id = save(draft("Read", HabitType.TIMED, target = 30))
        val quest = instance(id)!!

        val started = graph.startGate.startHabit(quest.id) as StartGateResult.Started
        assertThat(started.run.habitQuestId).isEqualTo(quest.id)
        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.STARTED)

        graph.clock.advance(Duration.ofMinutes(31))
        val cleared = graph.gateRuns.check() as GateCheck.Cleared

        assertXp(cleared.outcome, base = 69) // TIMER, Normal, streak 0: 40 x 1.5 x 1.15
        assertThat(xpRows().map { it.sourceType }).containsExactly(SourceType.QUEST)
        assertThat(graph.db.ledgerDao().all().map { it.sourceType }).doesNotContain(SourceType.GATE)
        // Not one of the day's four paid custom runs (GD §8.1 P5).
        assertThat(graph.db.focusDao().paidCustomRuns(graph.today())).isEqualTo(0)
        // Final, like a gate.
        assertThat(graph.undoCompletion(quest.id)).isEqualTo(UndoResult.GateClear)
        assertThat(cleared.outcome.undoable).isFalse()
    }

    @Test
    fun aNinetyFiveMinuteTimedHabitIsExtremeForOneHundredThirtyEightXp() = runTest {
        val id = save(draft("Deep work", HabitType.TIMED, target = 95))
        assertThat(graph.db.habitDao().get(id)?.difficulty).isEqualTo(Difficulty.EXTREME)

        graph.startGate.startHabit(instance(id)!!.id)
        graph.clock.advance(Duration.ofMinutes(96))

        assertXp((graph.gateRuns.check() as GateCheck.Cleared).outcome, base = 138)
    }

    @Test
    fun aTimedHabitOfFourteenMinutesIsRejected() = runTest {
        graph.seedHunter()

        assertThat(graph.saveHabit(draft(type = HabitType.TIMED, target = 14)))
            .isEqualTo(SaveHabitResult.InvalidHabit(HabitProblem.TIMED_MINUTES))
    }

    @Test
    fun aCollapsedTimedSessionLeavesTheHabitOpenForAnotherGo() = runTest {
        val id = save(draft("Read", HabitType.TIMED, target = 30))
        val quest = instance(id)!!
        val run = (graph.startGate.startHabit(quest.id) as StartGateResult.Started).run

        graph.gateRuns.abandon()

        assertThat(graph.db.gateRunDao().get(run.id)?.status).isEqualTo(GateRunStatus.ABANDONED)
        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(xpRows()).isEmpty()
        assertThat(graph.startGate.startHabit(quest.id)).isInstanceOf(StartGateResult.Started::class.java)
    }

    @Test
    fun onlyATimedHabitCanStartASession() = runTest {
        val id = save(draft("Stretch"))

        assertThat(graph.startGate.startHabit(instance(id)!!.id)).isEqualTo(StartGateResult.NotATimedHabit)
    }

    // --- avoid habits (#90, #91) ---

    @Test
    fun keptItOpensInTheEveningAndPaysSelfXp() = runTest {
        val id = save(draft("No sugar", HabitType.NEGATIVE))
        val quest = instance(id)!!

        assertThat(graph.avoidHabit.keptToday(quest.id)).isEqualTo(HabitActionResult.NotAllowed) // 10:00, before the evening
        assertThat(xpRows()).isEmpty()

        graph.clock.advance(Duration.ofHours(11)) // 21:00
        val kept = graph.avoidHabit.keptToday(quest.id) as HabitActionResult.Completed
        assertXp(kept.outcome, base = 60)
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.COMPLETED)
    }

    @Test
    fun anAvoidDayNobodyConfirmedIsMissedAndPaysNothing() = runTest {
        val id = save(draft("No sugar", HabitType.NEGATIVE))
        val first = instance(id)!!

        nextDay()

        assertThat(questDao.get(first.id)?.status).isEqualTo(QuestStatus.MISSED)
        assertThat(xpRows()).isEmpty()
    }

    @Test
    fun aSlipIsMissedAtOnceAndCanBeTakenBackUntilTheDayEnds() = runTest {
        val id = save(draft("No sugar", HabitType.NEGATIVE))
        val quest = instance(id)!!

        assertThat(graph.avoidHabit.slipped(quest.id)).isEqualTo(HabitActionResult.Done)
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.MISSED)

        assertThat(graph.avoidHabit.undoSlip(quest.id)).isEqualTo(HabitActionResult.Done)
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.ACTIVE)

        // Once the game-day has ended a slip cannot be taken back.
        graph.avoidHabit.slipped(quest.id)
        nextDay()
        assertThat(graph.avoidHabit.undoSlip(quest.id)).isEqualTo(HabitActionResult.NotFound)
        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.MISSED)
    }

    @Test
    fun anAvoidHabitMustRunOnFixedDays() = runTest {
        graph.seedHunter()

        assertThat(graph.saveHabit(draft(type = HabitType.NEGATIVE, frequency = HabitFrequency.TIMES_PER_WEEK, timesPerWeek = 3)))
            .isEqualTo(SaveHabitResult.InvalidHabit(HabitProblem.NEGATIVE_NEEDS_DAYS))
    }

    // --- times a week (#92) ---

    @Test
    fun aThreeTimesAWeekHabitStopsOnceDoneAndNeverRecordsAMiss() = runTest {
        val id = save(draft("Gym", frequency = HabitFrequency.TIMES_PER_WEEK, timesPerWeek = 3))

        // Mon: done. Tue: done. Wed: nothing. Thu: done.
        graph.completeQuest(instance(id)!!.id)
        nextDay()
        graph.completeQuest(instance(id)!!.id)
        nextDay()
        val wednesday = instance(id)!!
        nextDay()
        assertThat(questDao.get(wednesday.id)?.status).isEqualTo(QuestStatus.EXPIRED) // expired, never MISSED
        graph.completeQuest(instance(id)!!.id) // Thursday: the third completion

        nextDay() // Friday: the week's quota is met, so no instance
        assertThat(instance(id)).isNull()
        nextDay()
        nextDay()
        assertThat(instance(id)).isNull() // Saturday and Sunday too

        nextDay() // the next Monday
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.ACTIVE)
    }

    @Test
    fun aFlexibleHabitCannotBeSkipped() = runTest {
        val id = save(draft("Gym", frequency = HabitFrequency.TIMES_PER_WEEK, timesPerWeek = 3))

        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.NOT_NEEDED)
        assertThat(graph.skipHabit.skip(id)).isEqualTo(HabitActionResult.NotAllowed)
    }

    // --- reminders (#93) ---

    @Test
    fun aThirdHabitCannotTakeAReminder() = runTest {
        graph.seedHunter()
        graph.rollover()
        assertThat(graph.saveHabit(draft("One", reminder = 480))).isInstanceOf(SaveHabitResult.Saved::class.java)
        assertThat(graph.saveHabit(draft("Two", reminder = 540))).isInstanceOf(SaveHabitResult.Saved::class.java)

        assertThat(graph.saveHabit(draft("Three", reminder = 600))).isEqualTo(SaveHabitResult.ReminderLimit)
        // The same habits can still be edited, and a third habit can be saved without one.
        assertThat(graph.saveHabit(draft("Three"))).isInstanceOf(SaveHabitResult.Saved::class.java)
        assertThat(graph.db.habitDao().countRemindersExcept("none")).isEqualTo(2)
    }

    @Test
    fun savingAHabitReArmsTheReminders() = runTest {
        graph.seedHunter()
        graph.rollover()
        val before = graph.habitReminders.rearmed

        graph.saveHabit(draft("One", reminder = 480))

        assertThat(graph.habitReminders.rearmed).isEqualTo(before + 1)
    }

    @Test
    fun aReminderOutsideTheDayIsRejected() = runTest {
        graph.seedHunter()

        assertThat(graph.saveHabit(draft(reminder = 24 * 60))).isEqualTo(SaveHabitResult.InvalidTarget)
    }

    // --- skip today (#95) ---

    @Test
    fun aSkippedHabitIsNotAMissNotAnItemAndGivesTheSkipBackWhenUndone() = runTest {
        val id = save(draft("Stretch"))
        val monday = instance(id)!!

        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.AVAILABLE)
        assertThat(graph.skipHabit.skip(id)).isEqualTo(HabitActionResult.Done)
        assertThat(instance(id)?.status).isEqualTo(QuestStatus.SKIPPED)
        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.SKIPPED)

        nextDay() // Tuesday: this week's one skip is used
        assertThat(questDao.get(monday.id)?.status).isEqualTo(QuestStatus.SKIPPED) // closing the day does not turn it into a miss
        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.NO_SKIPS_LEFT)
        assertThat(graph.skipHabit.skip(id)).isEqualTo(HabitActionResult.NotAllowed)
    }

    @Test
    fun takingASkipBackGivesTheAllowanceBack() = runTest {
        val id = save(draft("Stretch"))

        graph.skipHabit.skip(id)
        assertThat(graph.skipHabit.undoSkip(id)).isEqualTo(HabitActionResult.Done)

        assertThat(instance(id)?.status).isEqualTo(QuestStatus.ACTIVE)
        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.AVAILABLE)
    }

    @Test
    fun aCompletedHabitCannotBeSkipped() = runTest {
        val id = save(draft("Stretch"))
        graph.completeQuest(instance(id)!!.id)

        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.NONE)
        assertThat(graph.skipHabit.skip(id)).isEqualTo(HabitActionResult.NotAllowed)
    }

    @Test
    fun theSkipAllowanceRenewsWithTheNextIsoWeek() = runTest {
        val id = save(draft("Stretch"))
        graph.skipHabit.skip(id) // Monday
        repeat(7) { nextDay() } // the next Monday

        assertThat(graph.skipHabit.state(id)).isEqualTo(SkipState.AVAILABLE)
    }

    // --- nothing changes for habits that existed before ---

    @Test
    fun anOldStyleHabitStillBehavesLikeBefore() = runTest {
        val id = save(HabitDraft(null, "Read 10 pages", Stat.INT, Difficulty.EASY, DayOfWeek.entries.toSet(), 10, "pages"))

        val habit = graph.db.habitDao().get(id)!!
        assertThat(habit.type).isEqualTo(HabitType.CHECK)
        assertThat(habit.frequency).isEqualTo(HabitFrequency.DAYS_OF_WEEK)
        assertThat(habit.reminderTime).isNull()
        assertThat(graph.completeQuest(instance(id)!!.id)).isInstanceOf(CompleteQuestResult.Completed::class.java)
    }
}
