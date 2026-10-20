package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.ai.AiPrefsRepository
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.path.PathInput
import com.assismohanty.awaken.engine.path.PathInputProblem
import com.assismohanty.awaken.engine.path.PathRules
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.ProgressionSignal
import java.util.UUID
import javax.inject.Inject

sealed interface CreatePathResult {
    data class Created(val pathId: String) : CreatePathResult
    data object NoHunter : CreatePathResult
    /** The AI consent sheet has not been accepted (DS §4.8): nothing is created and nothing is sent. */
    data object ConsentRequired : CreatePathResult
    data class Invalid(val problems: List<PathInputProblem>) : CreatePathResult
    /** Already 3 Paths active or being planned (GD §6.6.1). */
    data object LimitReached : CreatePathResult
    /** One Path per game-day, so create/delete churn can't hammer the proxy (GD §6.6.1). */
    data object CreatedTodayAlready : CreatePathResult
}

/** Creates a Path in PLANNING and asks the Oracle for its ladder and first week (ARCH §3.7). */
class CreatePathUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val aiPrefs: AiPrefsRepository,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: PathInput): CreatePathResult {
        val clean = input.copy(goal = input.goal.trim(), currentLevel = input.currentLevel.trim(), notes = input.notes.trim())
        val created = transactions.run {
            val profile = hunterDao.getProfile() ?: return@run CreatePathResult.NoHunter
            if (!aiPrefs.hasConsent()) return@run CreatePathResult.ConsentRequired
            val problems = PathRules.problems(clean)
            if (problems.isNotEmpty()) return@run CreatePathResult.Invalid(problems)
            val today = clock.gameDate(profile.dayStartHour)
            if (pathDao.countActiveOrPlanning() >= PathRules.MAX_ACTIVE) return@run CreatePathResult.LimitReached
            if (pathDao.countCreatedOn(today) >= PathRules.MAX_CREATED_PER_DAY) return@run CreatePathResult.CreatedTodayAlready

            val now = clock.now()
            val id = UUID.randomUUID().toString()
            pathDao.upsert(
                PathEntity(
                    id = id,
                    goal = clean.goal,
                    currentLevel = clean.currentLevel,
                    notes = clean.notes,
                    primaryStat = clean.primaryStat,
                    secondaryStat = clean.secondaryStat,
                    activeDaysMask = clean.activeDays.toMask(),
                    questsPerDay = clean.questsPerDay,
                    weeklyMinutes = clean.weeklyMinutes,
                    status = PathStatus.PLANNING,
                    milestoneStartedGameDate = today,
                    createdAt = now,
                    createdGameDate = today,
                ),
            )
            // One open request covers the whole "plan, then this week" job; the UI reads its status.
            pathDao.insertWeekIfAbsent(
                PathWeekEntity(
                    pathId = id,
                    isoWeek = PathScheduler.isoWeek(today),
                    status = PathWeekStatus.REQUESTED,
                    progressionSignal = ProgressionSignal.HOLD,
                    promptVersion = "",
                    responseSha256 = "",
                    requestedAt = now,
                ),
            )
            CreatePathResult.Created(id)
        }
        if (created is CreatePathResult.Created) pathWork.enqueuePlan(created.pathId, thenWeek = true)
        return created
    }
}

sealed interface UpdatePathResult {
    data object Updated : UpdatePathResult
    data object NotFound : UpdatePathResult
    data class Invalid(val problems: List<PathInputProblem>) : UpdatePathResult
    /** The Path is archived and cannot be edited. */
    data object Archived : UpdatePathResult
    data object ConsentRequired : UpdatePathResult
    /** One re-plan per ISO week per Path (GD §6.6.2). */
    data object ReplanLimit : UpdatePathResult
}

/**
 * Edits a Path. Days, quests per day and time budget shape *next* week's request; the goal stays what the ladder was
 * written for unless [replan] asks the Oracle for a new ladder (max once per ISO week).
 */
class UpdatePathUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val aiPrefs: AiPrefsRepository,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(pathId: String, input: PathInput, replan: Boolean): UpdatePathResult {
        val clean = input.copy(goal = input.goal.trim(), currentLevel = input.currentLevel.trim(), notes = input.notes.trim())
        val result = transactions.run {
            val profile = hunterDao.getProfile() ?: return@run UpdatePathResult.NotFound
            val path = pathDao.get(pathId) ?: return@run UpdatePathResult.NotFound
            if (path.status == PathStatus.ARCHIVED) return@run UpdatePathResult.Archived
            val problems = PathRules.problems(clean)
            if (problems.isNotEmpty()) return@run UpdatePathResult.Invalid(problems)
            val isoWeek = PathScheduler.isoWeek(clock.gameDate(profile.dayStartHour))
            if (replan) {
                if (!aiPrefs.hasConsent()) return@run UpdatePathResult.ConsentRequired
                if (path.lastReplanIsoWeek == isoWeek) return@run UpdatePathResult.ReplanLimit
            }
            pathDao.upsert(
                path.copy(
                    goal = clean.goal,
                    currentLevel = clean.currentLevel,
                    notes = clean.notes,
                    primaryStat = clean.primaryStat,
                    secondaryStat = clean.secondaryStat,
                    activeDaysMask = clean.activeDays.toMask(),
                    questsPerDay = clean.questsPerDay,
                    weeklyMinutes = clean.weeklyMinutes,
                    lastReplanIsoWeek = if (replan) isoWeek else path.lastReplanIsoWeek,
                ),
            )
            UpdatePathResult.Updated
        }
        if (result is UpdatePathResult.Updated && replan) pathWork.enqueuePlan(pathId, thenWeek = false)
        return result
    }
}

/** Pause, resume and archive (GD §6.6.1). Paused and archived Paths are not offered; existing slots are kept. */
class ChangePathStatusUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val pathWeeks: PathWeekRequester,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun pause(pathId: String): Boolean = change(pathId, from = setOf(PathStatus.ACTIVE), to = PathStatus.PAUSED)

    suspend fun archive(pathId: String): Boolean =
        change(pathId, from = setOf(PathStatus.PLANNING, PathStatus.ACTIVE, PathStatus.PAUSED), to = PathStatus.ARCHIVED)

    /** Resuming asks for this week's quests right away if the Path missed them while paused. */
    suspend fun resume(pathId: String): Boolean {
        if (!change(pathId, from = setOf(PathStatus.PAUSED), to = PathStatus.ACTIVE)) return false
        hunterDao.getProfile()?.let { pathWeeks.requestDue(clock.gameDate(it.dayStartHour)) }
        return true
    }

    private suspend fun change(pathId: String, from: Set<PathStatus>, to: PathStatus): Boolean = transactions.run {
        val path = pathDao.get(pathId) ?: return@run false
        if (path.status !in from) return@run false
        pathDao.upsert(path.copy(status = to))
        true
    }
}

sealed interface RetryPathResult {
    data object Started : RetryPathResult
    /** Both manual retries for this week are used (GD §6.6.7). */
    data object LimitReached : RetryPathResult
    data object ConsentRequired : RetryPathResult
    data object NothingToRetry : RetryPathResult
}

/** The [Retry] on a Path that "could not chart this week" (DS §4.8); counts toward the manual-retry limit. */
class RetryPathUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val aiPrefs: AiPrefsRepository,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(pathId: String): RetryPathResult {
        if (!aiPrefs.hasConsent()) return RetryPathResult.ConsentRequired
        var enqueue: (() -> Unit)? = null
        val result = transactions.run {
            val profile = hunterDao.getProfile() ?: return@run RetryPathResult.NothingToRetry
            val path = pathDao.get(pathId) ?: return@run RetryPathResult.NothingToRetry
            val now = clock.now()
            val isoWeek = PathScheduler.isoWeek(clock.gameDate(profile.dayStartHour))
            val week = pathDao.week(pathId, isoWeek)
            when {
                path.status == PathStatus.PLANNING -> {
                    // No ladder yet: ask again for the plan, then the week.
                    pathDao.upsertWeek(
                        (week ?: newWeek(pathId, isoWeek, now)).copy(status = PathWeekStatus.REQUESTED, requestedAt = now),
                    )
                    enqueue = { pathWork.enqueuePlan(pathId, thenWeek = true) }
                    RetryPathResult.Started
                }
                path.status == PathStatus.ACTIVE && week != null && week.status in RETRYABLE -> {
                    if (week.manualRetries >= MAX_MANUAL_RETRIES) return@run RetryPathResult.LimitReached
                    pathDao.upsertWeek(
                        week.copy(status = PathWeekStatus.REQUESTED, requestedAt = now, manualRetries = week.manualRetries + 1),
                    )
                    enqueue = { pathWork.enqueueWeek(pathId, isoWeek, manual = true) }
                    RetryPathResult.Started
                }
                else -> RetryPathResult.NothingToRetry
            }
        }
        enqueue?.invoke()
        return result
    }

    private fun newWeek(pathId: String, isoWeek: String, now: java.time.Instant) = PathWeekEntity(
        pathId = pathId,
        isoWeek = isoWeek,
        status = PathWeekStatus.REQUESTED,
        progressionSignal = ProgressionSignal.HOLD,
        promptVersion = "",
        responseSha256 = "",
        requestedAt = now,
    )

    companion object {
        /** GD §6.6.7: two manual retries per Path per ISO week. */
        const val MAX_MANUAL_RETRIES = 2
        private val RETRYABLE = setOf(PathWeekStatus.FAILED, PathWeekStatus.EMPTY)
    }
}

/** The consent sheet's [Enable AI Paths] (DS §4.8): records consent and creates the install ID. */
class GrantAiConsentUseCase @Inject constructor(
    private val aiPrefs: AiPrefsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        aiPrefs.grantConsent(clock.now())
    }
}

/**
 * Settings → AI Paths off (ARCH §3.7): cancel every request, forget the install ID, keep existing Paths and slots
 * readable and completable. No new plans are requested until consent is given again.
 */
class RevokeAiConsentUseCase @Inject constructor(
    private val aiPrefs: AiPrefsRepository,
    private val pathDao: PathDao,
    private val pathWork: PathWorkScheduler,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke() {
        aiPrefs.revokeConsent()
        pathWork.cancelAllPathWork()
        transactions.run { pathDao.failAllRequested() }
    }
}
