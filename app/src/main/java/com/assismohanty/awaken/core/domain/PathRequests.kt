package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.ai.AiPrefsRepository
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.path.ConstraintsDto
import com.assismohanty.awaken.engine.path.PathPlanRequest
import com.assismohanty.awaken.engine.path.PathProgression
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathWeekRequest
import com.assismohanty.awaken.engine.path.PathWire
import com.assismohanty.awaken.engine.path.WeekOutcome
import java.time.LocalDate
import javax.inject.Inject

/** Equipment and Injury Mode tags the Oracle must respect (ARCH §3.7); the validator enforces them again. */
internal fun HunterProfileEntity.pathConstraints(): ConstraintsDto =
    PathWire.constraints(ownedEquipment, if (injuryMode) injuryTags else emptySet())

/** Last week's slots as the progression signal and the next prompt see them (GD §6.6.5). */
internal suspend fun PathDao.lastWeekOutcomes(pathId: String, today: LocalDate): List<WeekOutcome> =
    slotsForWeek(pathId, PathScheduler.isoWeek(today.minusWeeks(1))).map { WeekOutcome(it.title, it.outcome, it.feedback) }

/** Builds the exact requests sent to the Oracle from stored state (data minimization: ARCH §3.7 "Request content"). */
class PathRequests @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val clock: Clock,
) {
    suspend fun plan(path: PathEntity, requestId: String): PathPlanRequest? {
        val profile = hunterDao.getProfile() ?: return null
        return PathWire.planRequest(requestId, path.toPath(), profile.pathConstraints())
    }

    /** Null when the Path has no ladder yet or no row for the week. */
    suspend fun week(path: PathEntity, isoWeek: String, requestId: String): PathWeekRequest? {
        val profile = hunterDao.getProfile() ?: return null
        val milestones = pathDao.currentMilestones(path.id)
        val milestone = milestones.getOrNull(path.currentMilestoneIndex) ?: milestones.lastOrNull() ?: return null
        val week = pathDao.week(path.id, isoWeek) ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val lastWeek = pathDao.lastWeekOutcomes(path.id, today)
        return PathWire.weekRequest(
            requestId = requestId,
            path = path.toPath(),
            milestone = milestone.toMilestone(),
            signal = week.progressionSignal,
            lastWeek = lastWeek,
            constraints = profile.pathConstraints(),
        )
    }
}

/**
 * Records that a Path needs this week's quests and hands the work to the scheduler (GD §6.6.2). The `path_week` primary
 * key makes it idempotent; requests still open this week are re-enqueued with keep-existing semantics, which recovers
 * from a process death between the row and the work. Without consent nothing is asked.
 */
class PathWeekRequester @Inject constructor(
    private val pathDao: PathDao,
    private val aiPrefs: AiPrefsRepository,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun requestDue(today: LocalDate) {
        if (!aiPrefs.hasConsent()) return
        val isoWeek = PathScheduler.isoWeek(today)
        val now = clock.now()
        val open = transactions.run {
            pathDao.active().forEach { path ->
                if (pathDao.currentMilestones(path.id).isEmpty() || pathDao.week(path.id, isoWeek) != null) return@forEach
                pathDao.insertWeekIfAbsent(
                    PathWeekEntity(
                        pathId = path.id,
                        isoWeek = isoWeek,
                        status = PathWeekStatus.REQUESTED,
                        progressionSignal = PathProgression.signal(pathDao.lastWeekOutcomes(path.id, today)),
                        promptVersion = "",
                        responseSha256 = "",
                        requestedAt = now,
                    ),
                )
            }
            pathDao.requestedWeeks().filter { it.isoWeek == isoWeek }
        }
        open.forEach { pathWork.enqueueWeek(it.pathId, it.isoWeek, manual = false) }
    }
}
