package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.ai.OracleOutcome
import com.assismohanty.awaken.core.data.ai.OracleReply
import com.assismohanty.awaken.core.data.ai.PathOracleClient
import com.assismohanty.awaken.core.data.ai.dto.ReportRequest
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.path.PathInput
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.PathWeekRequest
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The "Paths (app)" row of ARCHITECTURE §6: everything between the Oracle's reply and Room, with the fake Oracle. */
@RunWith(AndroidJUnit4::class)
class PathUseCasesTest {
    // Wednesday of ISO week 2026-W43, 10:00 UTC (day starts at 04:00).
    private val graph = TestGraph(clock = com.assismohanty.awaken.testing.FakeClock(Instant.parse("2026-10-21T10:00:00Z")))
    private val pathDao = graph.pathDao
    private val questDao = graph.db.questDao()

    @After
    fun tearDown() = graph.close()

    private val input = PathInput(
        goal = "Reach 1900 on Codeforces",
        currentLevel = "Rated 1400",
        notes = "",
        primaryStat = Stat.INT,
        secondaryStat = null,
        activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        questsPerDay = 1,
        weeklyMinutes = 180,
    )

    private suspend fun createPath(consent: Boolean = true): String {
        graph.seedHunter()
        if (consent) graph.grantConsent()
        val created = graph.createPath(input)
        assertThat(created).isInstanceOf(CreatePathResult.Created::class.java)
        return (created as CreatePathResult.Created).pathId
    }

    /** The job the worker would run for a freshly created Path. */
    private suspend fun runPlanThenWeek(pathId: String, requestId: String = "req-1"): PathRequestResult =
        graph.runPathRequest(PathJob(pathId, PathRequestMode.PLAN_THEN_WEEK, requestId), attempt = 0)

    private fun today(): LocalDate = graph.today()

    @Test
    fun creatingAPathNeedsConsentAndSendsNothingWithoutIt() = runTest {
        graph.seedHunter()

        assertThat(graph.createPath(input)).isEqualTo(CreatePathResult.ConsentRequired)

        assertThat(pathDao.all()).isEmpty()
        assertThat(graph.pathWork.planCalls).isEmpty()
        assertThat(graph.oracle.totalCalls).isEqualTo(0)
    }

    @Test
    fun aNewPathIsPlanningWithOneOpenRequestAndOnePerDay() = runTest {
        val pathId = createPath()

        val path = pathDao.get(pathId)!!
        assertThat(path.status).isEqualTo(PathStatus.PLANNING)
        assertThat(pathDao.week(pathId, PathScheduler.isoWeek(today()))?.status).isEqualTo(PathWeekStatus.REQUESTED)
        assertThat(graph.pathWork.planCalls).containsExactly(com.assismohanty.awaken.testing.RecordingPathWorkScheduler.PlanCall(pathId, true))
        assertThat(graph.createPath(input.copy(goal = "Run a 10K"))).isEqualTo(CreatePathResult.CreatedTodayAlready)
    }

    @Test
    fun planThenWeekTurnsAGoalIntoAnActivePathWithQuestsOnToday() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())

        assertThat(runPlanThenWeek(pathId)).isEqualTo(PathRequestResult.Done)

        assertThat(pathDao.get(pathId)?.status).isEqualTo(PathStatus.ACTIVE)
        assertThat(pathDao.currentMilestones(pathId)).hasSize(5)
        assertThat(pathDao.week(pathId, PathScheduler.isoWeek(today()))?.status).isEqualTo(PathWeekStatus.READY)
        assertThat(graph.oracle.planCalls).isEqualTo(1)
        assertThat(graph.oracle.weekCalls).isEqualTo(1)
        // Wednesday is an active day: its quest joined the existing directive as a plain PATH quest.
        val pathQuests = questDao.getForDate(today()).filter { it.pathSlotId != null }
        assertThat(pathQuests).hasSize(1)
        assertThat(pathQuests.single().type).isEqualTo(QuestType.PATH)
        assertThat(pathQuests.single().stat).isEqualTo(Stat.INT)
    }

    @Test
    fun theSameWeekAppliedTwiceCreatesNothingNew() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val week = PathScheduler.isoWeek(today())
        val slots = pathDao.slotsForWeek(pathId, week)
        val reply = graph.oracle.weekQuests(
            graph.pathRequests.week(pathDao.get(pathId)!!, week, "req-2")!!,
            pathId,
            manualRetry = false,
        ) as OracleOutcome.Success<OracleReply>

        assertThat(graph.acceptWeek(pathId, week, reply.value)).isEqualTo(AcceptWeekResult.AlreadyApplied)

        assertThat(pathDao.slotsForWeek(pathId, week)).isEqualTo(slots)
        assertThat(questDao.getForDate(today()).filter { it.pathSlotId != null }).hasSize(1)
    }

    @Test
    fun aPlanArrivingMidWeekSchedulesOnlyTheRemainingDays() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId)

        val slots = pathDao.slotsForWeek(pathId, PathScheduler.isoWeek(today()))
        // Mon/Wed/Fri with three quests, plan accepted on Wednesday: Wed and Fri are left, so the third stays in stock.
        assertThat(slots.map { it.assignedGameDate }).containsExactly(today(), today().plusDays(2), null).inOrder()
    }

    @Test
    fun anUnfinishedPathQuestIsCarriedOnceThenSkipped() = runTest {
        val wednesday = today()
        val pathId = createPath()
        runPlanThenWeek(pathId)
        val first = pathDao.slotsForWeek(pathId, PathScheduler.isoWeek(today())).first()

        // Wednesday closes unfinished: re-offered on Friday.
        graph.clock.advance(Duration.ofDays(1))
        graph.rollover()
        val carried = pathDao.slot(first.id)!!
        assertThat(carried.assignedGameDate).isEqualTo(wednesday.plusDays(2))
        assertThat(carried.carriedOver).isTrue()
        assertThat(carried.outcome).isEqualTo(SlotOutcome.PENDING)

        // Friday closes unfinished too: SKIPPED, and it counts as not completed.
        graph.clock.advance(Duration.ofDays(2))
        graph.rollover()
        assertThat(pathDao.slot(first.id)!!.outcome).isEqualTo(SlotOutcome.SKIPPED)
    }

    @Test
    fun consentRevokedWhileARequestIsInFlightDiscardsTheReply() = runTest {
        val pathId = createPath()
        val revokingOracle = object : PathOracleClient {
            override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> {
                graph.revokeConsent()
                return graph.oracle.planPath(request)
            }

            override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean) =
                graph.oracle.weekQuests(request, pathId, manualRetry)

            override suspend fun report(report: ReportRequest) = graph.oracle.report(report)
        }
        val run = RunPathRequestUseCase(
            graph.db.hunterDao(), pathDao, graph.pathRequests, revokingOracle, graph.acceptPlan, graph.acceptWeek,
            graph.aiPrefs, graph.oracleTrace, graph.transactions, graph.clock,
        )

        assertThat(run(PathJob(pathId, PathRequestMode.PLAN_THEN_WEEK, "req-1"), attempt = 0)).isEqualTo(PathRequestResult.Done)

        assertThat(pathDao.currentMilestones(pathId)).isEmpty()
        assertThat(pathDao.get(pathId)?.status).isEqualTo(PathStatus.PLANNING)
        assertThat(graph.pathWork.cancelAllCalls).isEqualTo(1)
        assertThat(graph.aiPrefs.installId()).isNull()
    }

    @Test
    fun retriesResendTheSameRequestId() = runTest {
        val pathId = createPath()
        val seen = mutableListOf<String>()
        var failures = 1
        val flaky = object : PathOracleClient {
            override suspend fun planPath(request: PathPlanRequest): OracleOutcome<OracleReply> {
                seen += request.requestId
                return if (failures-- > 0) OracleOutcome.Offline else graph.oracle.planPath(request)
            }

            override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean) =
                graph.oracle.weekQuests(request, pathId, manualRetry)

            override suspend fun report(report: ReportRequest) = graph.oracle.report(report)
        }
        val run = RunPathRequestUseCase(
            graph.db.hunterDao(), pathDao, graph.pathRequests, flaky, graph.acceptPlan, graph.acceptWeek,
            graph.aiPrefs, graph.oracleTrace, graph.transactions, graph.clock,
        )
        val job = PathJob(pathId, PathRequestMode.PLAN_THEN_WEEK, "req-stable")

        assertThat(run(job, attempt = 0)).isEqualTo(PathRequestResult.Retry)
        assertThat(run(job, attempt = 1)).isEqualTo(PathRequestResult.Done)

        assertThat(seen).containsExactly("req-stable", "req-stable")
        assertThat(pathDao.get(pathId)?.status).isEqualTo(PathStatus.ACTIVE)
    }

    @Test
    fun givingUpMarksTheWeekFailedAndARetryIsLimitedToTwo() = runTest {
        val pathId = createPath()
        val down = object : PathOracleClient {
            override suspend fun planPath(request: PathPlanRequest) = OracleOutcome.Offline
            override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean) = OracleOutcome.Offline
            override suspend fun report(report: ReportRequest) = OracleOutcome.Offline
        }
        val run = RunPathRequestUseCase(
            graph.db.hunterDao(), pathDao, graph.pathRequests, down, graph.acceptPlan, graph.acceptWeek,
            graph.aiPrefs, graph.oracleTrace, graph.transactions, graph.clock,
        )

        val last = RunPathRequestUseCase.MAX_ATTEMPTS - 1
        assertThat(run(PathJob(pathId, PathRequestMode.PLAN_THEN_WEEK, "r"), attempt = last)).isEqualTo(PathRequestResult.Done)
        val week = PathScheduler.isoWeek(today())
        assertThat(pathDao.week(pathId, week)?.status).isEqualTo(PathWeekStatus.FAILED)

        // A planning Path is retried as a whole (no manual limit); an active one is limited to two per week.
        assertThat(graph.retryPath(pathId)).isEqualTo(RetryPathResult.Started)
        assertThat(graph.pathWork.planCalls.last().thenWeek).isTrue()
    }

    @Test
    fun aSpentBudgetGivesUpSilentlyWithoutRetrying() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId, "plan-ok")
        val week = PathScheduler.isoWeek(today())
        pathDao.upsertWeek(pathDao.week(pathId, week)!!.copy(status = PathWeekStatus.REQUESTED, manualRetries = 0))
        val broke = object : PathOracleClient {
            override suspend fun planPath(request: PathPlanRequest) = OracleOutcome.BudgetExhausted
            override suspend fun weekQuests(request: PathWeekRequest, pathId: String, manualRetry: Boolean) = OracleOutcome.BudgetExhausted
            override suspend fun report(report: ReportRequest) = OracleOutcome.BudgetExhausted
        }
        val run = RunPathRequestUseCase(
            graph.db.hunterDao(), pathDao, graph.pathRequests, broke, graph.acceptPlan, graph.acceptWeek,
            graph.aiPrefs, graph.oracleTrace, graph.transactions, graph.clock,
        )

        assertThat(run(PathJob(pathId, PathRequestMode.WEEK, "r", isoWeek = week), attempt = 0)).isEqualTo(PathRequestResult.Done)

        assertThat(pathDao.week(pathId, week)?.status).isEqualTo(PathWeekStatus.FAILED)
    }

    @Test
    fun theFirstRolloverOfANewWeekRequestsItWithTheProgressionSignal() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId)
        graph.pathWork.weekCalls.clear()

        // Next Monday: ISO week 2026-W44.
        graph.clock.advance(Duration.ofDays(5))
        graph.rollover()

        val nextWeek = PathScheduler.isoWeek(today())
        assertThat(nextWeek).isEqualTo("2026-W44")
        val row = pathDao.week(pathId, nextWeek)!!
        assertThat(row.status).isEqualTo(PathWeekStatus.REQUESTED)
        // Last week: 1 expired + 1 skipped-or-expired, nothing completed, fewer than 4 counted → HOLD.
        assertThat(row.progressionSignal).isEqualTo(ProgressionSignal.HOLD)
        assertThat(graph.pathWork.weekCalls.map { it.isoWeek }).contains(nextWeek)
    }

    @Test
    fun noWeekIsRequestedWithoutConsent() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId)
        graph.revokeConsent()
        graph.pathWork.weekCalls.clear()

        graph.clock.advance(Duration.ofDays(5))
        graph.rollover()

        assertThat(pathDao.week(pathId, PathScheduler.isoWeek(today()))).isNull()
        assertThat(graph.pathWork.weekCalls).isEmpty()
        // Existing Paths stay readable.
        assertThat(pathDao.get(pathId)?.status).isEqualTo(PathStatus.ACTIVE)
    }

    @Test
    fun completingAPathQuestPaysItsPricedRewardAndUndoReopensTheSlot() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val quest = questDao.getForDate(today()).first { it.pathSlotId != null }
        val slotId = quest.pathSlotId!!

        val result = graph.completeQuest(quest.id)

        assertThat(result).isInstanceOf(CompleteQuestResult.Completed::class.java)
        assertThat(pathDao.slot(slotId)?.outcome).isEqualTo(SlotOutcome.COMPLETED)
        assertThat(graph.undoCompletion(quest.id)).isInstanceOf(UndoResult.Undone::class.java)
        assertThat(pathDao.slot(slotId)?.outcome).isEqualTo(SlotOutcome.PENDING)
    }

    @Test
    fun feedbackIsStoredOnTheSlot() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val quest = questDao.getForDate(today()).first { it.pathSlotId != null }

        assertThat(graph.submitFeedback(quest.id, com.assismohanty.awaken.engine.path.Feedback.TOO_EASY)).isTrue()

        assertThat(pathDao.slot(quest.pathSlotId!!)?.feedback).isEqualTo(com.assismohanty.awaken.engine.path.Feedback.TOO_EASY)
    }

    @Test
    fun reportingAPathQuestRemovesItWithoutTouchingRerollPricing() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val quest = questDao.getForDate(today()).first { it.pathSlotId != null }

        assertThat(graph.reportPathQuest(quest.id, ReportReason.UNSAFE)).isEqualTo(ReportResult.Reported)

        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.REPORTED)
        assertThat(pathDao.slot(quest.pathSlotId!!)?.outcome).isEqualTo(SlotOutcome.REPORTED)
        assertThat(questDao.countRerolled(today())).isEqualTo(0)
        val report = pathDao.unsentReports().single()
        assertThat(report.reason).isEqualTo("unsafe")
        assertThat(report.title).isEqualTo(quest.title)
        assertThat(graph.pathWork.reportCalls).isEqualTo(1)
        // Reporting twice is a no-op.
        assertThat(graph.reportPathQuest(quest.id, ReportReason.OTHER)).isEqualTo(ReportResult.NotReportable)
    }

    @Test
    fun sendingReportsMarksThemSent() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val quest = questDao.getForDate(today()).first { it.pathSlotId != null }
        graph.reportPathQuest(quest.id, ReportReason.OTHER)

        assertThat(graph.sendReports()).isEqualTo(PathRequestResult.Done)

        assertThat(pathDao.unsentReports()).isEmpty()
        assertThat(graph.oracle.reportCalls).isEqualTo(1)
    }

    @Test
    fun rerollingAPathQuestSwapsInTheNextUnscheduledBlueprint() = runTest {
        val pathId = createPath()
        graph.generateDirective(today())
        runPlanThenWeek(pathId)
        val quest = questDao.getForDate(today()).first { it.pathSlotId != null }

        val result = graph.rerollQuest(quest.id)

        assertThat(result).isInstanceOf(RerollResult.Rerolled::class.java)
        val replacement = (result as RerollResult.Rerolled).replacement
        assertThat(replacement.type).isEqualTo(QuestType.PATH)
        assertThat(replacement.pathSlotId).isNotEqualTo(quest.pathSlotId)
        assertThat(pathDao.slot(quest.pathSlotId!!)?.outcome).isEqualTo(SlotOutcome.REROLLED)
        assertThat(questDao.get(quest.id)?.status).isEqualTo(QuestStatus.REROLLED)
        assertThat(pathDao.slot(replacement.pathSlotId!!)?.assignedGameDate).isEqualTo(today())
    }

    @Test
    fun advancingAMilestoneNeedsTheModelAndTheEngineAndPaysNothing() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId)
        val week = PathScheduler.isoWeek(today())
        val before = graph.ledger.balances()

        // The model hasn't suggested it: nothing is offered.
        assertThat(graph.advanceMilestone.offerFor(pathId)).isNull()

        // Suggested, but nothing completed yet (needs ≥ 4 since the milestone began and a ≥ 60% last week).
        pathDao.upsertWeek(pathDao.week(pathId, week)!!.copy(suggestAdvance = true))
        assertThat(graph.advanceMilestone.offerFor(pathId)).isNull()

        // 4 completed since the milestone began, and last week's rate was 100%.
        val lastWeek = PathScheduler.isoWeek(today().minusWeeks(1))
        val template = pathDao.slotsForWeek(pathId, week).first()
        repeat(4) { i ->
            pathDao.upsertSlot(
                template.copy(
                    id = "done-$i", isoWeek = lastWeek, blueprintIndex = 100 + i, assignedGameDate = today().minusDays(3),
                    outcome = SlotOutcome.COMPLETED, title = "Done $i",
                ),
            )
        }
        pathDao.upsert(pathDao.get(pathId)!!.copy(milestoneStartedGameDate = today().minusDays(10)))

        val offer = graph.advanceMilestone.offerFor(pathId)
        assertThat(offer?.next?.index).isEqualTo(1)
        assertThat(graph.advanceMilestone.advance(pathId)).isTrue()
        assertThat(pathDao.get(pathId)?.currentMilestoneIndex).isEqualTo(1)
        assertThat(graph.ledger.balances()).isEqualTo(before)
        assertThat(graph.advanceMilestone.offerFor(pathId)).isNull()
    }

    @Test
    fun aPlanWithTooFewMilestonesLeavesThePathPlanning() = runTest {
        val pathId = createPath()
        val tooShort = OracleReply("plan_path.v1", """{"promptVersion":"plan_path.v1","milestones":[{"title":"Only","summary":"x","exitCriterion":"y"}]}""")

        assertThat(graph.acceptPlan(pathId, tooShort)).isEqualTo(AcceptPlanResult.Invalid)

        assertThat(pathDao.get(pathId)?.status).isEqualTo(PathStatus.PLANNING)
        assertThat(pathDao.currentMilestones(pathId)).isEmpty()
    }

    @Test
    fun revokingConsentCancelsWorkForgetsTheInstallIdAndKeepsThePaths() = runTest {
        val pathId = createPath()
        assertThat(graph.aiPrefs.installId()).isNotNull()

        graph.revokeConsent()

        assertThat(graph.pathWork.cancelAllCalls).isEqualTo(1)
        assertThat(graph.aiPrefs.installId()).isNull()
        assertThat(graph.aiPrefs.hasConsent()).isFalse()
        assertThat(pathDao.get(pathId)).isNotNull()
        assertThat(pathDao.week(pathId, PathScheduler.isoWeek(today()))?.status).isEqualTo(PathWeekStatus.FAILED)
    }

    @Test
    fun aReplanIsAllowedOncePerIsoWeek() = runTest {
        val pathId = createPath()
        runPlanThenWeek(pathId)

        assertThat(graph.updatePath(pathId, input, replan = true)).isEqualTo(UpdatePathResult.Updated)
        assertThat(graph.updatePath(pathId, input, replan = true)).isEqualTo(UpdatePathResult.ReplanLimit)
        // Editing without a re-plan is always fine.
        assertThat(graph.updatePath(pathId, input.copy(weeklyMinutes = 240), replan = false)).isEqualTo(UpdatePathResult.Updated)
        assertThat(pathDao.get(pathId)?.weeklyMinutes).isEqualTo(240)
    }

    @Test
    fun threePathsAtMostAndPausedOnesDoNotCount() = runTest {
        graph.seedHunter()
        graph.grantConsent()
        // One Path per game-day: space the creations out.
        val ids = (0 until 3).map {
            val created = graph.createPath(input.copy(goal = "Goal number $it")) as CreatePathResult.Created
            graph.clock.advance(Duration.ofDays(1))
            created.pathId
        }
        assertThat(graph.createPath(input)).isEqualTo(CreatePathResult.LimitReached)

        // A Path must be ACTIVE to pause.
        pathDao.upsert(pathDao.get(ids[0])!!.copy(status = PathStatus.ACTIVE))
        assertThat(graph.changePathStatus.pause(ids[0])).isTrue()

        assertThat(graph.createPath(input)).isInstanceOf(CreatePathResult.Created::class.java)
    }
}
