package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.ai.AiPrefsRepository
import com.assismohanty.awaken.core.data.ai.OracleTrace
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.engine.path.Milestone
import com.assismohanty.awaken.engine.path.PathRules
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One Path with everything its window and detail screen show, for the current ISO week. */
data class PathSummary(
    val path: PathEntity,
    /** The current plan's ladder, in order. Empty while the first plan is still being written. */
    val milestones: List<Milestone>,
    /** This week's slots by blueprint order. */
    val weekSlots: List<PathSlotEntity>,
    val week: PathWeekEntity?,
    /** The last attempt failed for lack of a connection and will be retried. */
    val offline: Boolean,
    /** Set when the model suggested it and the engine agrees (GD §6.6.5). */
    val advance: AdvanceOffer?,
)

/** Why a new Path cannot be created right now, if it cannot. */
enum class CreateBlock { LIMIT, TODAY }

data class PathsModel(
    val gameDate: LocalDate,
    val paths: List<PathSummary>,
    val consentGiven: Boolean,
    val createBlock: CreateBlock?,
)

/** Everything the Paths tab and Path detail observe. Derived from rows, never stored (invariant 7). */
class ObservePathsUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val pathDao: PathDao,
    private val aiPrefs: AiPrefsRepository,
    private val trace: OracleTrace,
    private val advanceMilestone: AdvanceMilestoneUseCase,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<PathsModel?> =
        combine(hunterDao.observeProfile(), dayChanges) { profile, _ -> profile }.flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(null)
            val today = clock.gameDate(profile.dayStartHour)
            val isoWeek = PathScheduler.isoWeek(today)
            combine(
                pathDao.observeVisible(),
                pathDao.observeAllMilestones(),
                pathDao.observeSlotsForIsoWeek(isoWeek),
                pathDao.observeWeeksForIsoWeek(isoWeek),
                combine(aiPrefs.consent, trace.offlinePaths) { consent, offline -> consent.given to offline },
            ) { paths, milestones, slots, weeks, (consent, offline) ->
                val ladders = milestones.groupBy { it.pathId }.mapValues { (_, rows) ->
                    val latest = rows.maxOf { it.planVersion }
                    rows.filter { it.planVersion == latest }.sortedBy { it.idx }.map { it.toMilestone() }
                }
                PathsModel(
                    gameDate = today,
                    paths = paths.map { path ->
                        PathSummary(
                            path = path,
                            milestones = ladders[path.id].orEmpty(),
                            weekSlots = slots.filter { it.pathId == path.id },
                            week = weeks.firstOrNull { it.pathId == path.id },
                            offline = path.id in offline,
                            advance = null,
                        )
                    },
                    consentGiven = consent,
                    createBlock = null,
                )
            }.map { model ->
                // Offers and the create limit need extra reads; the flow re-runs whenever any observed row changes.
                val active = model.paths.map { summary ->
                    summary.copy(advance = if (summary.path.status == PathStatus.ACTIVE) advanceMilestone.offerFor(summary.path.id) else null)
                }
                model.copy(paths = active, createBlock = createBlock(active, today))
            }
        }

    private suspend fun createBlock(paths: List<PathSummary>, today: LocalDate): CreateBlock? {
        val activeOrPlanning = paths.count { it.path.status == PathStatus.PLANNING || it.path.status == PathStatus.ACTIVE }
        if (activeOrPlanning >= PathRules.MAX_ACTIVE) return CreateBlock.LIMIT
        // Archived Paths are not listed but still count against "one new Path per game-day".
        val createdToday = pathDao.countCreatedOn(today)
        return if (createdToday >= PathRules.MAX_CREATED_PER_DAY) CreateBlock.TODAY else null
    }
}
