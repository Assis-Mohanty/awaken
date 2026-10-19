package com.assismohanty.awaken.feature.status

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.repo.ShareImageWriter
import com.assismohanty.awaken.core.designsystem.components.HeatmapDay
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.domain.AchievementEntry
import com.assismohanty.awaken.core.domain.ChapterEntry
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.EquipTitleUseCase
import com.assismohanty.awaken.core.domain.HeatmapEntry
import com.assismohanty.awaken.core.domain.ObserveStatusUseCase
import com.assismohanty.awaken.core.domain.StatProgress
import com.assismohanty.awaken.core.domain.StatusModel
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.WeeklyReport
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class StatusUiState(
    val loading: Boolean = true,
    /** Title is filled in by the screen from [equippedTitleId] (display text lives in strings.xml). */
    val header: HunterHeaderModel? = null,
    val equippedTitleId: String? = null,
    val rank: Rank = Rank.E,
    val name: String = "",
    val level: Int = 1,
    val streak: Int = 0,
    val radarNow: Map<Stat, Float> = emptyMap(),
    val radarLastWeek: Map<Stat, Float> = emptyMap(),
    val radarMax: Float = 1f,
    val stats: List<StatProgress> = emptyList(),
    val heatmap: List<HeatmapDay> = emptyList(),
    val selectedDay: HeatmapEntry? = null,
    val achievements: List<AchievementEntry> = emptyList(),
    val chapters: List<ChapterEntry> = emptyList(),
    val openChapter: ChapterEntry? = null,
    val weeklyReport: WeeklyReport? = null,
    /** Sunday (GD §11), or forced from the debug toggle. */
    val weeklyOpen: Boolean = false,
    val projectedRecalibration: Map<Stat, DifficultyAdapter.Change> = emptyMap(),
    /** The share card is being captured offscreen. */
    val capturing: Boolean = false,
    /** Ready to hand to ACTION_SEND; the route clears it with [StatusEvent.ShareHandled]. */
    val shareUri: Uri? = null,
)

sealed interface StatusEvent {
    data class EquipTitle(val titleId: String?) : StatusEvent
    data class SelectDay(val date: LocalDate) : StatusEvent
    data class OpenChapter(val chapterId: String) : StatusEvent
    data object CloseChapter : StatusEvent
    data object ToggleDebugWeekly : StatusEvent
    data object Share : StatusEvent
    data class ShareCaptured(val bitmap: Bitmap) : StatusEvent
    data object ShareFailed : StatusEvent
    data object ShareHandled : StatusEvent
}

private data class LocalState(
    val selectedDate: LocalDate? = null,
    val openChapterId: String? = null,
    val debugWeekly: Boolean = false,
    val capturing: Boolean = false,
    val shareUri: Uri? = null,
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    observeStatus: ObserveStatusUseCase,
    dayRefresher: DayRefresher,
    private val equipTitle: EquipTitleUseCase,
    private val shareImageWriter: ShareImageWriter,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<StatusUiState> = combine(observeStatus(dayChanges = dayRefresher.ticks), local) { model, local ->
        model?.toUiState(local) ?: StatusUiState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatusUiState())

    fun onEvent(event: StatusEvent) {
        when (event) {
            is StatusEvent.EquipTitle -> viewModelScope.launch {
                try {
                    equipTitle(event.titleId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Equipping a title failed")
                    completionCenter.message(UserMessage.Error)
                }
            }
            is StatusEvent.SelectDay -> local.update { it.copy(selectedDate = event.date.takeUnless { d -> d == it.selectedDate }) }
            is StatusEvent.OpenChapter -> local.update { it.copy(openChapterId = event.chapterId) }
            StatusEvent.CloseChapter -> local.update { it.copy(openChapterId = null) }
            StatusEvent.ToggleDebugWeekly -> local.update { it.copy(debugWeekly = !it.debugWeekly) }
            StatusEvent.Share -> local.update { it.copy(capturing = true) }
            is StatusEvent.ShareCaptured -> viewModelScope.launch {
                try {
                    val uri = shareImageWriter.writePng(event.bitmap)
                    local.update { it.copy(capturing = false, shareUri = uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Writing the share image failed")
                    shareFailed()
                }
            }
            StatusEvent.ShareFailed -> shareFailed()
            StatusEvent.ShareHandled -> local.update { it.copy(shareUri = null) }
        }
    }

    private fun shareFailed() {
        local.update { it.copy(capturing = false, shareUri = null) }
        completionCenter.message(UserMessage.ShareFailed)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun StatusModel.toUiState(local: LocalState): StatusUiState = StatusUiState(
    loading = false,
    header = HunterHeaderModel(
        name = hunter.name,
        title = null,
        level = hunter.level,
        rank = hunter.rank,
        xpIntoLevel = hunter.xpIntoLevel,
        xpForLevel = hunter.xpForLevel,
        streak = hunter.streak,
        gold = hunter.gold,
        frameId = hunter.equippedFrameId,
        sigilId = hunter.equippedSigilId,
    ),
    equippedTitleId = hunter.equippedTitleId,
    rank = hunter.rank,
    name = hunter.name,
    level = hunter.level,
    streak = hunter.streak,
    radarNow = hunter.statLevels.mapValues { it.value.toFloat() },
    radarLastWeek = lastWeekLevels.mapValues { it.value.toFloat() },
    radarMax = radarMax.toFloat(),
    stats = stats,
    heatmap = heatmap.map { HeatmapDay(it.date, it.intensity, it.restDay) },
    selectedDay = local.selectedDate?.let { date -> heatmap.firstOrNull { it.date == date } },
    achievements = achievements,
    chapters = chapters,
    openChapter = local.openChapterId?.let { id -> chapters.firstOrNull { it.chapter.id == id && it.unlocked } },
    weeklyReport = weeklyReport,
    weeklyOpen = hunter.gameDate.dayOfWeek == DayOfWeek.SUNDAY || local.debugWeekly,
    projectedRecalibration = projectedRecalibration,
    capturing = local.capturing,
    shareUri = local.shareUri,
)
