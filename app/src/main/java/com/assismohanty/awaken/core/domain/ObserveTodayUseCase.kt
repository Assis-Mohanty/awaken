package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.NarrativeRepository
import com.assismohanty.awaken.engine.SystemLines
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/** Everything the Today screen shows (DESIGN_SYSTEM §4.2). Rerolled-away quests are already filtered out. */
data class TodayModel(
    val hunter: HunterState,
    val quests: List<QuestInstanceEntity>,
    /** The daily System line (GD §11); null only if the narrative file has no lines. */
    val systemLine: String?,
)

internal val QuestInstanceEntity.isOpen: Boolean get() = status == QuestStatus.ACTIVE || status == QuestStatus.STARTED

class ObserveTodayUseCase @Inject constructor(
    private val observeHunterState: ObserveHunterStateUseCase,
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val reader: GameStateReader,
    private val trialDao: TrialDao,
    private val narrative: NarrativeRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dayChanges: Flow<Any>): Flow<TodayModel?> =
        observeHunterState(dayChanges).flatMapLatest { hunter ->
            if (hunter == null) return@flatMapLatest flowOf(null)
            questDao.observeForDate(hunter.gameDate).mapLatest { all ->
                val quests = all.filter { it.status != QuestStatus.REROLLED }
                TodayModel(hunter, quests, systemLine(hunter, quests))
            }
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
}
