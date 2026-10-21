package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.NarrativeRepository
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.SystemLines
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/** The Rank Trial slot on Today (GD §5). */
sealed interface TrialSummary {
    /** The level reached the next band and no cooldown applies: the trial can begin. */
    data class Available(val rank: Rank) : TrialSummary
    data class Active(val rank: Rank, val cleared: Int, val stages: Int, val daysLeft: Int) : TrialSummary
}

/** Where a Path quest came from; [gateRank] is set for GATE-mode quests. */
data class PathBadgeInfo(val pathName: String, val gateRank: GateRank?)

/** Everything the Today screen shows (DESIGN_SYSTEM §4.2). Rerolled-away and reported quests are already filtered out. */
data class TodayModel(
    val hunter: HunterState,
    val quests: List<QuestInstanceEntity>,
    /** The daily System line (GD §11); null only if the narrative file has no lines. */
    val systemLine: String?,
    val trial: TrialSummary? = null,
    /** Path name and gate per quest id, for the Path badge on rows (DS §3 PathQuestBadge). */
    val pathBadges: Map<String, PathBadgeInfo> = emptyMap(),
)

internal val QuestInstanceEntity.isOpen: Boolean get() = status == QuestStatus.ACTIVE || status == QuestStatus.STARTED

class ObserveTodayUseCase @Inject constructor(
    private val observeHunterState: ObserveHunterStateUseCase,
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val reader: GameStateReader,
    private val trialDao: TrialDao,
    private val pathDao: PathDao,
    private val trialReader: TrialReader,
    private val narrative: NarrativeRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<TodayModel?> =
        observeHunterState(dayChanges).flatMapLatest { hunter ->
            if (hunter == null) return@flatMapLatest flowOf(null)
            questDao.observeForDate(hunter.gameDate).mapLatest { all ->
                val quests = all.filter { !it.status.isHidden }
                TodayModel(hunter, quests, systemLine(hunter, quests), trialSummary(hunter), pathBadges(quests))
            }
        }

    private suspend fun trialSummary(hunter: HunterState): TrialSummary? {
        val active = trialDao.active()
        if (active != null) {
            val progress = trialReader.progress(active, hunter.gameDate)
            val daysLeft = ChronoUnit.DAYS.between(hunter.gameDate, active.expiresGameDate).toInt() + 1
            return TrialSummary.Active(active.targetRank, progress.cleared.size, RankRules.TRIAL_STAGES, daysLeft.coerceAtLeast(0))
        }
        return RankRules.trialAvailable(
            level = hunter.level,
            passedTrials = trialDao.passedRanks().toSet(),
            trialActive = false,
            lastFailedAt = trialDao.lastFailedAt(),
            now = clock.now(),
        )?.let { TrialSummary.Available(it) }
    }

    private suspend fun systemLine(hunter: HunterState, quests: List<QuestInstanceEntity>): String? {
        val profile = hunterDao.getProfile() ?: return null
        val lastActive = reader.lastActiveDates()
        val key = SystemLines.contextKey(
            penaltyOpen = quests.any { it.type == QuestType.PENALTY && it.isOpen },
            trialActive = trialDao.activeCount() > 0,
            streak = hunter.streak,
            todayCleared = hunter.todayCleared,
            neglected = SystemLines.neglectedStat(lastActive, hunter.gameDate),
        )
        return SystemLines.pick(narrative.systemLines(), key, profile.hunterId, hunter.gameDate, hunter.streak)
    }

    private suspend fun pathBadges(quests: List<QuestInstanceEntity>): Map<String, PathBadgeInfo> {
        if (quests.none { it.pathSlotId != null }) return emptyMap()
        val paths = pathDao.all().associateBy { it.id }
        return buildMap {
            quests.forEach { quest ->
                val slot = quest.pathSlotId?.let { pathDao.slot(it) } ?: return@forEach
                val path = paths[slot.pathId] ?: return@forEach
                put(quest.id, PathBadgeInfo(path.goal.take(BADGE_NAME_MAX), slot.gateRank))
            }
        }
    }

    private companion object {
        const val BADGE_NAME_MAX = 28
    }
}
