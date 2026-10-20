package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.ai.OracleReply
import com.assismohanty.awaken.core.data.ai.OracleTrace
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.AiReportEntity
import com.assismohanty.awaken.core.data.db.entity.PathMilestoneEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.path.Feedback
import com.assismohanty.awaken.engine.path.Milestone
import com.assismohanty.awaken.engine.path.PathPricing
import com.assismohanty.awaken.engine.path.PathProgression
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.PathValidator
import com.assismohanty.awaken.engine.path.PlanValidation
import com.assismohanty.awaken.engine.path.SlotOutcome
import com.assismohanty.awaken.engine.path.ValidationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

sealed interface AcceptPlanResult {
    data class Accepted(val milestones: Int) : AcceptPlanResult
    /** Fewer than 4 usable milestones, or nothing parseable (GD §6.6.2): the Path stays PLANNING. */
    data object Invalid : AcceptPlanResult
    data object NotFound : AcceptPlanResult
}

/** Turns the Oracle's ladder into milestones — only after `PathValidator` (CLAUDE.md invariant 5). */
class AcceptPlanUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val trace: OracleTrace,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(pathId: String, reply: OracleReply): AcceptPlanResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run AcceptPlanResult.NotFound
        val path = pathDao.get(pathId)?.takeIf { it.status != PathStatus.ARCHIVED } ?: return@run AcceptPlanResult.NotFound
        trace.recordPlan(reply.rawJson)
        when (val validation = PathValidator.validatePlan(reply.rawJson)) {
            is PlanValidation.Failed -> AcceptPlanResult.Invalid
            is PlanValidation.Valid -> {
                val version = pathDao.latestPlanVersion(pathId) + 1
                pathDao.insertMilestones(
                    validation.milestones.map { PathMilestoneEntity(pathId, it.index, it.title, it.summary, it.exitCriterion, version) },
                )
                pathDao.upsert(
                    path.copy(
                        status = if (path.status == PathStatus.PLANNING) PathStatus.ACTIVE else path.status,
                        currentMilestoneIndex = 0,
                        milestoneStartedGameDate = clock.gameDate(profile.dayStartHour),
                    ),
                )
                AcceptPlanResult.Accepted(validation.milestones.size)
            }
        }
    }
}

sealed interface AcceptWeekResult {
    /** [kept] blueprints survived validation; 0 means the week is EMPTY ("could not chart this week"). */
    data class Applied(val kept: Int, val dropped: Int) : AcceptWeekResult
    /** The same week was applied before (vector 32): nothing changed. */
    data object AlreadyApplied : AcceptWeekResult
    /** The ISO week ended before the reply arrived, so its quests would land in the wrong week. */
    data object Stale : AcceptWeekResult
    data object NotActive : AcceptWeekResult
}

/**
 * Validates, prices and schedules one week of blueprints in ONE transaction (ARCH §3.7, GD §6.6.4-6.6.6): V1-V9, then
 * `PathPricing` (the model's numbers never set a reward), then `PathScheduler.assign`, slots inserted with IGNORE on the
 * scheduling key, the `path_week` row, and — if today's directive already exists — today's Path quests.
 */
class AcceptWeekPlanUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val questDao: QuestDao,
    private val planner: PathQuestPlanner,
    private val trace: OracleTrace,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(pathId: String, isoWeek: String, reply: OracleReply): AcceptWeekResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run AcceptWeekResult.NotActive
        val entity = pathDao.get(pathId)?.takeIf { it.status == PathStatus.ACTIVE } ?: return@run AcceptWeekResult.NotActive
        val week = pathDao.week(pathId, isoWeek) ?: return@run AcceptWeekResult.NotActive
        if (week.status == PathWeekStatus.READY || week.status == PathWeekStatus.EMPTY) return@run AcceptWeekResult.AlreadyApplied
        val today = clock.gameDate(profile.dayStartHour)
        if (PathScheduler.isoWeek(today) != isoWeek) {
            pathDao.setWeekStatus(pathId, isoWeek, PathWeekStatus.FAILED)
            return@run AcceptWeekResult.Stale
        }

        val path = entity.toPath()
        val context = ValidationContext(
            path = path,
            ownedEquipment = profile.ownedEquipment,
            injuryTags = if (profile.injuryMode) profile.injuryTags else emptySet(),
            recentTitles = pathDao.titlesSince(pathId, today.minusDays(RECENT_TITLE_DAYS)),
        )
        val validation = PathValidator.validateWeek(reply.rawJson, context)
        trace.recordWeek(reply.rawJson, validation.drops.map { "${it.rule} #${it.index}: ${it.detail}" })
        val slots = PathScheduler.assign(PathPricing.price(validation.kept), path, today)
        pathDao.insertSlots(slots.map { it.toEntity() })
        val now = clock.now()
        pathDao.upsertWeek(
            week.copy(
                status = if (slots.isEmpty()) PathWeekStatus.EMPTY else PathWeekStatus.READY,
                promptVersion = reply.promptVersion,
                responseSha256 = sha256(reply.rawJson),
                receivedAt = now,
                suggestAdvance = validation.suggestAdvance,
            ),
        )
        // A directive that already exists gets these as plain PATH quests; a later directive picks them up itself.
        if (slots.any { it.assignedDate == today } && questDao.countGenerated(today) > 0) {
            questDao.insertAll(planner.draftsFor(today).map { it.copy(type = QuestType.PATH).toEntity(now) })
        }
        AcceptWeekResult.Applied(kept = slots.size, dropped = validation.drops.size)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** V7 looks back 14 days (GD §6.6.4). */
        const val RECENT_TITLE_DAYS = 14L
    }
}

/** The "The System judges you ready for: <next milestone>" moment (DS §4.8). */
data class AdvanceOffer(val pathId: String, val next: Milestone)

/**
 * Milestone advance (GD §6.6.5): offered only when the model suggested it **and** the engine agrees. Advancing is
 * narrative only — no XP, no gold — so there is nothing to farm.
 */
class AdvanceMilestoneUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun offerFor(pathId: String): AdvanceOffer? = transactions.run { offer(pathId) }

    suspend fun advance(pathId: String): Boolean = transactions.run {
        val offer = offer(pathId) ?: return@run false
        val profile = hunterDao.getProfile() ?: return@run false
        val path = pathDao.get(pathId) ?: return@run false
        pathDao.upsert(path.copy(currentMilestoneIndex = offer.next.index, milestoneStartedGameDate = clock.gameDate(profile.dayStartHour)))
        clearSuggestion(pathId, profile.dayStartHour)
        true
    }

    /** [Stay]: the suggestion for this week is dismissed. */
    suspend fun stay(pathId: String) = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run
        clearSuggestion(pathId, profile.dayStartHour)
    }

    private suspend fun offer(pathId: String): AdvanceOffer? {
        val profile = hunterDao.getProfile() ?: return null
        val path = pathDao.get(pathId)?.takeIf { it.status == PathStatus.ACTIVE } ?: return null
        val next = pathDao.currentMilestones(pathId).getOrNull(path.currentMilestoneIndex + 1)?.toMilestone() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val week = pathDao.week(pathId, PathScheduler.isoWeek(today)) ?: return null
        val lastWeek = PathProgression.stats(pathDao.lastWeekOutcomes(pathId, today))
        val completed = pathDao.completedSince(pathId, path.milestoneStartedGameDate)
        return if (PathProgression.canOfferAdvance(week.suggestAdvance, completed, lastWeek)) AdvanceOffer(pathId, next) else null
    }

    private suspend fun clearSuggestion(pathId: String, dayStartHour: Int) {
        val isoWeek = PathScheduler.isoWeek(clock.gameDate(dayStartHour))
        pathDao.week(pathId, isoWeek)?.let { pathDao.upsertWeek(it.copy(suggestAdvance = false)) }
    }
}

/** The three optional chips on a Path quest's reward sheet (DS §3 FeedbackChips). */
class SubmitFeedbackUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val pathDao: PathDao,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(questInstanceId: String, feedback: Feedback?): Boolean = transactions.run {
        val slotId = questDao.get(questInstanceId)?.pathSlotId ?: return@run false
        pathDao.setFeedback(slotId, feedback)
        true
    }
}

/** Why a hunter reported a Path quest (DS §4.8); the wire value is `name.lowercase()`. */
enum class ReportReason { UNSAFE, NOT_RELEVANT, IMPOSSIBLE, OFFENSIVE, OTHER }

sealed interface ReportResult {
    data object Reported : ReportResult
    data object NotReportable : ReportResult
}

/**
 * "Report this quest" (PRD F21, DS §4.8): the quest leaves today's Directive immediately with no reward impact, and the
 * text goes to the outbox for `/v1/reports`. Reporting is not a reroll, so it never changes what a reroll costs.
 */
class ReportPathQuestUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val pathDao: PathDao,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(questInstanceId: String, reason: ReportReason): ReportResult {
        val result = transactions.run {
            val quest = questDao.get(questInstanceId) ?: return@run ReportResult.NotReportable
            val slot = quest.pathSlotId?.let { pathDao.slot(it) } ?: return@run ReportResult.NotReportable
            if (quest.status != QuestStatus.ACTIVE && quest.status != QuestStatus.STARTED) return@run ReportResult.NotReportable
            questDao.setStatus(quest.id, QuestStatus.REPORTED, completedAt = null)
            pathDao.setOutcome(slot.id, SlotOutcome.REPORTED)
            pathDao.insertReport(
                AiReportEntity(
                    id = UUID.randomUUID().toString(),
                    pathSlotId = slot.id,
                    reason = reason.name.lowercase(),
                    promptVersion = pathDao.week(slot.pathId, slot.isoWeek)?.promptVersion.orEmpty(),
                    title = slot.title,
                    description = slot.description,
                    createdAt = clock.now(),
                ),
            )
            ReportResult.Reported
        }
        if (result == ReportResult.Reported) pathWork.enqueueReports()
        return result
    }
}
