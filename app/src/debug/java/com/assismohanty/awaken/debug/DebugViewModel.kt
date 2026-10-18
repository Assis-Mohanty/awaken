package com.assismohanty.awaken.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.domain.CompleteQuestUseCase
import com.assismohanty.awaken.core.domain.CreateHunterUseCase
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.RunRolloverUseCase
import com.assismohanty.awaken.core.domain.HunterState
import com.assismohanty.awaken.core.domain.ObserveHunterStateUseCase
import com.assismohanty.awaken.core.domain.RerollQuestUseCase
import com.assismohanty.awaken.core.domain.StartQuestUseCase
import com.assismohanty.awaken.core.domain.UndoCompletionUseCase
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DebugUiState(
    val hunter: HunterState? = null,
    val now: Instant? = null,
    val gameDate: LocalDate? = null,
    val offsetDays: Long = 0,
    val quests: List<QuestInstanceEntity> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val ledger: List<LedgerEntryEntity> = emptyList(),
    /** Raw result of the last action — this is a developer tool, so results are shown as-is. */
    val lastResult: String? = null,
)

sealed interface DebugEvent {
    data object SeedHunter : DebugEvent
    data object AddDay : DebugEvent
    data object ResetDays : DebugEvent
    data object ForceRollover : DebugEvent
    data object RebuildBalances : DebugEvent
    data object GrantShield : DebugEvent
    data object GrantLevel : DebugEvent
    data object WipeData : DebugEvent
    data class Begin(val questInstanceId: String) : DebugEvent
    data class Complete(val questInstanceId: String) : DebugEvent
    data class Undo(val questInstanceId: String) : DebugEvent
    data class Reroll(val questInstanceId: String) : DebugEvent
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    observeHunterState: ObserveHunterStateUseCase,
    hunterDao: HunterDao,
    questDao: QuestDao,
    ledgerDao: LedgerDao,
    private val settings: DebugSettings,
    private val clock: Clock,
    private val ledger: LedgerRepository,
    private val createHunter: CreateHunterUseCase,
    private val rollover: RunRolloverUseCase,
    private val dayRefresher: DayRefresher,
    private val startQuest: StartQuestUseCase,
    private val completeQuest: CompleteQuestUseCase,
    private val undoCompletion: UndoCompletionUseCase,
    private val rerollQuest: RerollQuestUseCase,
    private val db: AwakenDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val lastResult = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayQuests = combine(hunterDao.observeProfile(), settings.offsetDays) { profile, _ -> profile }
        .flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList()) else questDao.observeForDate(clock.gameDate(profile.dayStartHour))
        }

    val uiState: StateFlow<DebugUiState> = combine(
        observeHunterState(dayChanges = settings.offsetDays),
        todayQuests,
        ledger.observeBalances(),
        ledgerDao.observeRecent(LEDGER_ROWS),
        combine(settings.offsetDays, lastResult) { offset, result -> offset to result },
    ) { hunter, quests, balances, rows, (offset, result) ->
        DebugUiState(
            hunter = hunter,
            now = clock.now(),
            gameDate = hunter?.gameDate,
            offsetDays = offset,
            quests = quests,
            balances = balances.values.toSortedMap(),
            ledger = rows,
            lastResult = result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DebugUiState())

    fun onEvent(event: DebugEvent) {
        viewModelScope.launch {
            lastResult.value = try {
                handle(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private suspend fun handle(event: DebugEvent): String = when (event) {
        DebugEvent.SeedHunter -> {
            createHunter(name = "Test Hunter", focusStats = listOf(Stat.STR, Stat.INT))
            "Seeded. ${rollover()}"
        }
        DebugEvent.AddDay -> {
            val target = settings.offsetDays.value + 1
            settings.addDays(1)
            settings.offsetDays.first { it == target } // the clock reads this flow; wait until it moved
            // The same refresh the app runs on foreground: rollover, notices, and every screen re-derives "today".
            dayRefresher.refresh()
            "Clock +1 day; rollover ran."
        }
        DebugEvent.ResetDays -> {
            settings.reset()
            "Clock offset reset (finalized days stay finalized)."
        }
        DebugEvent.ForceRollover -> {
            dayRefresher.refresh()
            "Rollover ran."
        }
        DebugEvent.RebuildBalances -> {
            val matched = ledger.balancesFromLedger() == ledger.balances()
            ledger.rebuildBalancesFromLedger()
            "Rebuilt. Cache matched ledger before rebuild: $matched"
        }
        DebugEvent.GrantShield -> uiState.value.gameDate?.let { today ->
            ledger.insertIdempotent(
                listOf(
                    LedgerEntryEntity(
                        kind = LedgerKind.ITEM,
                        itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID,
                        delta = 1,
                        sourceType = SourceType.SHOP,
                        sourceId = "debug:${UUID.randomUUID()}",
                        gameDate = today,
                        createdAt = clock.now(),
                    ),
                ),
            )
            "Granted 1 Streak Shield."
        } ?: "Seed a hunter first."
        DebugEvent.GrantLevel -> uiState.value.hunter?.let { hunter ->
            // Exactly enough XP for the next level; xp_source is empty so it never counts toward the daily caps.
            val needed = hunter.xpForLevel - hunter.xpIntoLevel
            ledger.insertIdempotent(
                listOf(
                    LedgerEntryEntity(
                        kind = LedgerKind.XP_TOTAL,
                        delta = needed,
                        sourceType = SourceType.QUEST,
                        sourceId = "debug:${UUID.randomUUID()}",
                        gameDate = hunter.gameDate,
                        createdAt = clock.now(),
                    ),
                ),
            )
            "Granted $needed XP (level ${hunter.level + 1})."
        } ?: "Seed a hunter first."
        DebugEvent.WipeData -> {
            withContext(ioDispatcher) { db.clearAllTables() }
            settings.reset()
            "All data wiped."
        }
        is DebugEvent.Begin -> "Begin: ${startQuest(event.questInstanceId)}"
        is DebugEvent.Complete -> completeQuest(event.questInstanceId).toString()
        is DebugEvent.Undo -> undoCompletion(event.questInstanceId).toString()
        is DebugEvent.Reroll -> rerollQuest(event.questInstanceId).toString()
    }

    private companion object {
        const val LEDGER_ROWS = 100
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
