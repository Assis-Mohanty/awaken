package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.ProgramBreakChoice
import com.assismohanty.awaken.engine.ProgramStart
import com.assismohanty.awaken.engine.ProgramStatus
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Programs end to end on a real in-memory database (GD §16.15, vectors #131-#134). */
@RunWith(AndroidJUnit4::class)
class LongArcUseCasesTest {
    private val graph = TestGraph() // clock: 2026-10-18 10:00 UTC, day start 04:00
    private val day1 = LocalDate.parse("2026-10-18")
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() = graph.close()

    private suspend fun readyHunter(level: Int = 10, gold: Long = 1_000) {
        graph.seedHunter()
        graph.rollover()
        graph.grant(LedgerKind.XP_TOTAL, LevelCurve.cumulative(level), "test-xp")
        graph.grant(LedgerKind.GOLD, gold, "test-gold")
    }

    private suspend fun nextDay() {
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
    }

    private suspend fun balances() = graph.ledger.balances()

    private suspend fun stage(date: LocalDate): QuestInstanceEntity? =
        graph.db.questDao().getForDate(date).firstOrNull { it.type == QuestType.DUNGEON }

    /** Completes a quest the way its verification allows. */
    private suspend fun finish(quest: QuestInstanceEntity) {
        if (quest.verification.source == VerificationSource.SELF) graph.completeQuest(quest.id) else graph.completeQuest.completeVerified(quest.id, quest.verification.source)
    }

    private suspend fun dungeonRows() = graph.db.ledgerDao().all().filter { it.sourceType == SourceType.DUNGEON }

    // Programs

    private suspend fun programQuests(date: LocalDate): List<QuestInstanceEntity> {
        val program = checkNotNull(currentProgram())
        val habitIds = graph.db.programDao().habits(program.id).map { it.id }
        return graph.db.questDao().getForDate(date).filter { it.habitId in habitIds }
    }


    private suspend fun currentProgram() = graph.db.programDao().observeCurrent().firstOrNull()

    private suspend fun view() = ObserveProgramUseCase(graph.db.hunterDao(), graph.db.programDao(), graph.programReader, ProgramText(context), graph.reader, graph.clock)(flowOf(Unit)).firstOrNull()

    private suspend fun keepToday() {
        programQuests(graph.today()).forEach { quest ->
            if (quest.verification.source == VerificationSource.SELF) graph.completeQuest(quest.id) else graph.completeQuest.completeVerified(quest.id, quest.verification.source)
        }
    }

    @Test
    fun startingMakesReadOnlyHabitsAndRefusesASecondArc() = runTest {
        graph.seedHunter()
        graph.rollover()

        val started = graph.startProgram("steady_30") as StartProgramResult.Started

        val habits = graph.db.programDao().habits(started.programId)
        assertThat(habits).hasSize(3)
        assertThat(habits.all { it.active && it.programId == started.programId }).isTrue()
        // Health is off, so the walking rule is the plain one.
        val walk = habits.first { it.unit == "min" && it.target == 30 }
        assertThat(walk.verification).isEqualTo(Verification.SELF)
        assertThat(habits.count { it.verification == Verification.TIMER }).isEqualTo(1)
        assertThat(programQuests(day1)).hasSize(3)
        assertThat(graph.startProgram("groundwork_66")).isEqualTo(StartProgramResult.Refused(ProgramStart.PROGRAM_ACTIVE))
        assertThat(graph.saveHabit(com.assismohanty.awaken.core.domain.HabitDraft(
            id = habits.first().id, title = "x", stat = Stat.STR, difficulty = Difficulty.EASY, days = java.time.DayOfWeek.entries.toSet(), target = 1, unit = "x",
        ))).isEqualTo(SaveHabitResult.ProgramHabit)
        assertThat(graph.db.habitDao().get(habits.first().id)?.title).isEqualTo(habits.first().title)
    }

    @Test
    fun aHealthRuleFallsBackToItsPlainFormOnADayWithoutHealth() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.enableHealth()
        val id = (graph.startProgram("steady_30") as StartProgramResult.Started).programId
        val walkHabit = graph.db.programDao().habits(id).single { it.verification == Verification.HEALTH_STEPS }
        assertThat(programQuests(day1).single { it.habitId == walkHabit.id }.verification).isEqualTo(Verification.HEALTH_STEPS)

        graph.disableHealth()
        nextDay()

        val plain = programQuests(graph.today()).single { it.habitId == walkHabit.id }
        assertThat(plain.verification).isEqualTo(Verification.SELF)
        assertThat(plain.target to plain.unit).isEqualTo(30 to "min")
        assertThat(plain.title).isEqualTo(context.getString(com.assismohanty.awaken.R.string.program_rule_walk_plain))
        // The stored habit keeps its Health form, so it comes back with Health.
        assertThat(graph.db.habitDao().get(walkHabit.id)?.verification).isEqualTo(Verification.HEALTH_STEPS)
        keepToday()
        assertThat(graph.programReader.dayVerdict(checkNotNull(graph.db.programDao().active()), com.assismohanty.awaken.engine.ProgramRules.spec("steady_30")!!, graph.today()))
            .isEqualTo(com.assismohanty.awaken.engine.ProgramDay.KEPT)
    }

    @Test
    fun anIroncladNightNobodyCouldReadIsNotAskedButABrokenOneBreaksTheDay() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("ironclad_75")
        graph.setBedtime.enableOrEdit(com.assismohanty.awaken.engine.BedtimeRules.DEFAULT_BEDTIME, com.assismohanty.awaken.engine.BedtimeRules.DEFAULT_WAKE)
        nextDay() // day 2: the first night is behind us and its quest exists
        val program = checkNotNull(graph.db.programDao().active())
        val spec = com.assismohanty.awaken.engine.ProgramRules.spec("ironclad_75")!!
        keepToday()
        graph.usage.readable = false

        graph.settleCurrentBedtime()

        val night = checkNotNull(graph.bedtimeReader.quest(graph.today()))
        assertThat(night.status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(graph.programReader.dayVerdict(program, spec, graph.today())).isEqualTo(com.assismohanty.awaken.engine.ProgramDay.KEPT)
        // The same night with evidence of use breaks the day.
        graph.db.questDao().expireNight(night.id, com.assismohanty.awaken.engine.BedtimeVerdict.BROKEN)
        assertThat(graph.programReader.dayVerdict(program, spec, graph.today())).isEqualTo(com.assismohanty.awaken.engine.ProgramDay.BROKEN)
    }

    @Test
    fun vector131_aBrokenDayAsksAndRestartMakesTodayDayOne() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")
        // Day 1 is kept, day 2 is not.
        keepToday()
        nextDay()
        nextDay() // day 2 ends with nothing done

        val program = checkNotNull(graph.db.programDao().active())
        val view = checkNotNull(view())
        assertThat(view.marks.take(3)).containsExactly(ProgramMark.KEPT, ProgramMark.BROKEN, ProgramMark.TODAY).inOrder()
        assertThat(view.pendingBreak).isEqualTo(day1.plusDays(1))

        assertThat(graph.chooseProgramBreak(ProgramBreakChoice.RESTART)).isEqualTo(ChooseBreakResult.Done)

        assertThat(checkNotNull(graph.db.programDao().active()).runStartDate).isEqualTo(graph.today())
        assertThat(graph.db.programDao().marks(program.id).single().choice).isEqualTo(ProgramBreakChoice.RESTART)
        assertThat(graph.chooseProgramBreak(ProgramBreakChoice.CONTINUE)).isEqualTo(ChooseBreakResult.NothingToAnswer)
    }

    @Test
    fun vector131_continueKeepsTheCountAndMarksTheDay() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")
        nextDay() // day 1 ends broken

        graph.chooseProgramBreak(ProgramBreakChoice.CONTINUE)

        val program = checkNotNull(graph.db.programDao().active())
        assertThat(program.runStartDate).isEqualTo(day1)
        assertThat(graph.db.programDao().marks(program.id).single().choice).isEqualTo(ProgramBreakChoice.CONTINUE)
        assertThat(com.assismohanty.awaken.engine.ProgramRules.dayNumber(program.runStartDate, graph.today())).isEqualTo(2)
    }

    @Test
    fun vector133_anUnansweredBreakBecomesContinueAtTheNextRollover() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")
        nextDay() // day 1 broken; nobody answers
        assertThat(graph.db.programDao().marks(checkNotNull(graph.db.programDao().active()).id)).isEmpty()

        nextDay()

        val marks = graph.db.programDao().marks(checkNotNull(graph.db.programDao().active()).id)
        assertThat(marks.single().gameDate).isEqualTo(day1)
        assertThat(marks.single().choice).isEqualTo(ProgramBreakChoice.CONTINUE)
    }

    @Test
    fun vector134_aRestDayIsNotBrokenAndTheCounterAdvances() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")
        // Day 1 is plain (nothing done: broken); day 2 is a rest day planned in advance.
        assertThat(graph.setRestDay(graph.today().plusDays(1), rest = true)).isEqualTo(DayPlanResult.Saved)
        nextDay()
        nextDay()

        val view = checkNotNull(view())
        assertThat(view.marks.take(3)).containsExactly(ProgramMark.BROKEN, ProgramMark.REST, ProgramMark.TODAY).inOrder()
        assertThat(view.brokenDays).isEqualTo(1) // the rest day added nothing
        assertThat(view.dayToday).isEqualTo(3)
    }

    @Test
    fun vector132_aRunWithNoBrokenDayCompletes() = runTest {
        graph.seedHunter()
        graph.rollover()
        val id = (graph.startProgram("steady_30") as StartProgramResult.Started).programId
        repeat(30) {
            keepToday()
            nextDay()
        }

        val program = checkNotNull(currentProgram())
        assertThat(program.status).isEqualTo(ProgramStatus.COMPLETED)
        assertThat(graph.db.programDao().habits(id).none { it.active }).isTrue() // archived
        assertThat(graph.db.ledgerDao().all().none { it.sourceType == SourceType.ACHIEVEMENT }).isTrue() // a program pays no gold of its own
        assertThat(graph.db.programDao().active()).isNull()
    }

    @Test
    fun vector132_aRunWithAContinuedBreakOnlyFinishes() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")
        repeat(30) { day ->
            if (day != 3) keepToday()
            nextDay()
        }

        val program = checkNotNull(currentProgram())
        assertThat(program.status).isEqualTo(ProgramStatus.FINISHED)
        assertThat(graph.db.ledgerDao().all().none { it.delta < 0 && it.sourceType != SourceType.SHOP }).isTrue() // nothing is ever deducted
    }

    @Test
    fun abandoningArchivesTheHabitsAndDeductsNothing() = runTest {
        graph.seedHunter()
        graph.rollover()
        val id = (graph.startProgram("steady_30") as StartProgramResult.Started).programId
        val rows = graph.db.ledgerDao().all().size

        graph.abandonProgram()

        assertThat(graph.db.programDao().habits(id).none { it.active }).isTrue()
        assertThat(checkNotNull(currentProgram()).status).isEqualTo(ProgramStatus.ABANDONED)
        assertThat(graph.db.ledgerDao().all()).hasSize(rows)
    }

}
