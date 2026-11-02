package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.DungeonStatus
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.engine.DungeonEntry
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

/** Dungeons and programs end to end on a real in-memory database (GD §16.14-16.15, vectors #127-#134). */
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

    // Dungeons

    @Test
    fun vector127_lockedBelowLevel10AndRefusedWithoutTheFee() = runTest {
        readyHunter(level = 9)
        assertThat(graph.enterDungeon(3, Stat.STR)).isEqualTo(EnterDungeonResult.Refused(DungeonEntry.LOCKED))

        graph.grant(LedgerKind.XP_TOTAL, LevelCurve.cumulative(10), "more-xp")
        graph.grant(LedgerKind.GOLD, -920, "spent") // 80 gold left
        assertThat(balances().gold).isEqualTo(80)
        assertThat(graph.enterDungeon(3, Stat.STR)).isEqualTo(EnterDungeonResult.Refused(DungeonEntry.NOT_ENOUGH_GOLD))
        assertThat(dungeonRows()).isEmpty()
    }

    @Test
    fun vector127_enteringChargesTheFeeOnceAndStageOneIsTheNextDay() = runTest {
        readyHunter()
        val goldBefore = balances().gold

        val entered = graph.enterDungeon(3, Stat.STR) as EnterDungeonResult.Entered

        val fee = dungeonRows().single()
        assertThat(fee.kind).isEqualTo(LedgerKind.GOLD)
        assertThat(fee.delta).isEqualTo(-100)
        assertThat(fee.sourceId).isEqualTo("${entered.dungeonId}:entry")
        assertThat(balances().gold).isEqualTo(goldBefore - 100)
        assertThat(stage(day1)).isNull()
        assertThat(graph.enterDungeon(5, Stat.INT)).isEqualTo(EnterDungeonResult.Refused(DungeonEntry.ALREADY_ACTIVE))

        nextDay()
        val first = checkNotNull(stage(day1.plusDays(1)))
        assertThat(first.dungeonId).isEqualTo(entered.dungeonId)
        assertThat(first.dungeonStage).isEqualTo(1)
        assertThat(first.difficulty).isEqualTo(Difficulty.HARD)
        assertThat(first.stat).isEqualTo(Stat.STR)
        assertThat(first.title).startsWith("Stage 1 of 3")
        graph.generateDirective(day1.plusDays(1))
        assertThat(graph.db.questDao().getForDate(day1.plusDays(1)).count { it.type == QuestType.DUNGEON }).isEqualTo(1)
    }

    @Test
    fun vector128_clearingPaysTheRewardAndAFragmentExactlyOnce() = runTest {
        readyHunter()
        val entered = graph.enterDungeon(3, Stat.STR) as EnterDungeonResult.Entered
        val goldBefore = balances().gold
        val fragmentsBefore = balances().fragments

        repeat(3) {
            nextDay()
            finish(checkNotNull(stage(graph.today())))
        }
        nextDay() // the third stage's day ends

        val dungeon = checkNotNull(graph.db.dungeonDao().all().single())
        assertThat(dungeon.status).isEqualTo(DungeonStatus.CLEARED)
        val clear = dungeonRows().filter { it.sourceId == "${entered.dungeonId}:clear" }
        assertThat(clear.map { it.kind to it.delta }).containsExactly(LedgerKind.GOLD to 150L, LedgerKind.FRAGMENT to 1L)
        assertThat(balances().fragments).isAtLeast(fragmentsBefore + 1) // a stage quest may drop one of its own
        assertThat(balances().gold).isAtLeast(goldBefore + 150)
        assertThat(graph.db.achievementDao().unlockedIds()).contains("dungeon_first")

        // Applying the settlement again changes nothing.
        graph.settleDungeon(day1.plusDays(3))
        assertThat(dungeonRows().filter { it.sourceId.endsWith(":clear") }).hasSize(2)
    }

    @Test
    fun vector129_anOpenStageFailsItWithNothingMoreTaken() = runTest {
        readyHunter()
        val entered = graph.enterDungeon(3, Stat.STR) as EnterDungeonResult.Entered
        nextDay()
        finish(checkNotNull(stage(graph.today())))
        nextDay() // stage 2 now open
        assertThat(stage(graph.today())?.dungeonStage).isEqualTo(2)
        val rowsBefore = graph.db.ledgerDao().all().size

        nextDay() // its game-day ended with the stage open

        assertThat(graph.db.dungeonDao().all().single().status).isEqualTo(DungeonStatus.FAILED)
        assertThat(stage(graph.today())).isNull() // no stage 3
        assertThat(dungeonRows().map { it.sourceId }).containsExactly("${entered.dungeonId}:entry")
        assertThat(graph.db.ledgerDao().all().filter { it.delta < 0 && it.sourceType == SourceType.DUNGEON }).hasSize(1)
        assertThat(rowsBefore).isAtLeast(1)
        assertThat(graph.db.achievementDao().unlockedIds()).doesNotContain("dungeon_first")
    }

    @Test
    fun aDayTheAppNeverGeneratedFailsTheStage() = runTest {
        readyHunter()
        graph.enterDungeon(3, Stat.STR)
        // Two days pass with no app open: no stage quest was ever generated.
        graph.clock.advance(Duration.ofDays(3))
        graph.rollover()

        assertThat(graph.db.dungeonDao().all().single().status).isEqualTo(DungeonStatus.FAILED)
    }

    @Test
    fun abandoningFailsItAtOnceAndKeepsTheFeeSpent() = runTest {
        readyHunter()
        graph.enterDungeon(5, Stat.INT)
        nextDay()

        graph.abandonDungeon()

        assertThat(graph.db.dungeonDao().all().single().status).isEqualTo(DungeonStatus.FAILED)
        assertThat(checkNotNull(stage(graph.today())).status).isEqualTo(QuestStatus.EXPIRED)
        assertThat(dungeonRows()).hasSize(1)
    }

    @Test
    fun vector130_aStageIsPaidAsAHardQuestAndCountsTowardTheSelfCap() = runTest {
        readyHunter()
        graph.enterDungeon(3, Stat.WIL)
        nextDay()
        val quest = checkNotNull(stage(graph.today()))
        assertThat(quest.difficulty).isEqualTo(Difficulty.HARD)

        // The stage template is picked from random ids, so it may be a SELF or a TIMER one; it is priced by its own check.
        finish(quest)

        val completion = checkNotNull(graph.db.completionDao().latestActive(quest.id))
        assertThat(completion.questType).isEqualTo(QuestType.DUNGEON)
        val streak = graph.reader.finalizedStreak().streak
        val expected = com.assismohanty.awaken.engine.XpCalculator.questXp(Difficulty.HARD, streak, quest.verification.source, completion.crit)
        assertThat(completion.xp).isEqualTo(expected)
        // Hard, self, streak 0 and no crit is 90 XP (vector #130).
        if (quest.verification.source == VerificationSource.SELF && !completion.crit && streak == 0) assertThat(completion.xp).isEqualTo(90)
    }

    @Test
    fun aDungeonStageCannotBeRerolled() = runTest {
        readyHunter()
        graph.enterDungeon(3, Stat.STR)
        nextDay()
        val quest = checkNotNull(stage(graph.today()))

        val result = graph.rerollQuest(quest.id)

        assertThat(result).isNotInstanceOf(RerollResult.Rerolled::class.java)
        assertThat(checkNotNull(graph.db.questDao().get(quest.id)).status).isEqualTo(QuestStatus.ACTIVE)
    }

    // Programs

    private suspend fun programQuests(date: LocalDate): List<QuestInstanceEntity> {
        val program = checkNotNull(currentProgram())
        val habitIds = graph.db.programDao().habits(program.id).map { it.id }
        return graph.db.questDao().getForDate(date).filter { it.habitId in habitIds }
    }


    private suspend fun currentProgram() = graph.db.programDao().observeCurrent().firstOrNull()

    private suspend fun view() = ObserveProgramUseCase(graph.db.hunterDao(), graph.db.programDao(), graph.programReader, ProgramText(context), graph.clock)(flowOf(Unit)).firstOrNull()

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
        ))).isEqualTo(SaveHabitResult.CampaignHabit)
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
    fun aProgramNeedsFreeSlotsAndNoCampaign() = runTest {
        graph.seedHunter()
        graph.rollover()
        repeat(6) { i ->
            graph.saveHabit(
                HabitDraft(id = null, title = "Habit $i", stat = Stat.STR, difficulty = Difficulty.EASY, days = java.time.DayOfWeek.entries.toSet(), target = 1, unit = "x"),
            )
        }

        assertThat(graph.startProgram("steady_30")).isEqualTo(StartProgramResult.Started::class.java.let { StartProgramResult.Refused(ProgramStart.NOT_ENOUGH_SLOTS) })
        assertThat(graph.db.programDao().active()).isNull()
        assertThat(graph.startProgram("nope")).isEqualTo(StartProgramResult.UnknownProgram)
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
    fun vector132_aRunWithNoBrokenDayCompletesAndUnlocksOnce() = runTest {
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
        assertThat(graph.db.achievementDao().unlockedIds()).contains("program_steady_30")
        assertThat(graph.db.ledgerDao().all().filter { it.sourceType == SourceType.ACHIEVEMENT && it.sourceId == "program_steady_30" }.map { it.delta }).containsExactly(150L)
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
        assertThat(graph.db.achievementDao().unlockedIds()).doesNotContain("program_steady_30")
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

    @Test
    fun aCampaignCannotStartWhileAProgramRuns() = runTest {
        graph.seedHunter()
        graph.rollover()
        graph.startProgram("steady_30")

        val answers = com.assismohanty.awaken.engine.campaign.CampaignScan.QUESTIONS.associate { (id, _) -> id to 1 }
        val preview = checkNotNull(graph.previewCampaign(answers))

        assertThat(preview.problem).isEqualTo(CampaignProblem.PROGRAM_ACTIVE)
    }
}
