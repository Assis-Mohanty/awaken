package com.assismohanty.awaken.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.reelcounter.ReelWidgetData
import com.assismohanty.awaken.core.domain.ObserveFocusGoalUseCase
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.designsystem.components.ScreenTimeTileModel
import com.assismohanty.awaken.core.domain.BedtimeReader
import com.assismohanty.awaken.core.domain.LoadScreenTimeSummaryUseCase
import com.assismohanty.awaken.core.lock.FocusLockSystemAccess
import com.assismohanty.awaken.core.reelcounter.ReelSystemAccess
import com.assismohanty.awaken.feature.today.toTile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L
private const val WEEK_DAYS = 7L
private const val SECONDS_PER_MINUTE = 60L

/** Today's focus (the Focus tab): today's focus against the daily goal, the focus streak, and the last 7 game-days. */
data class FocusCardState(
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    /** Daily goal in minutes (GD §16.4); 0 until it has been read. */
    val goal: Int = 0,
    val streak: Int = 0,
) {
    /** The bar's fill, 0..1. */
    val progress: Float get() = if (goal <= 0) 0f else (todayMinutes.toFloat() / goal).coerceIn(0f, 1f)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusCardViewModel @Inject constructor(
    hunterDao: HunterDao,
    focusDao: FocusDao,
    clock: Clock,
    observeFocusGoal: ObserveFocusGoalUseCase,
    dayRefresher: DayRefresher,
) : ViewModel() {
    private val week = hunterDao.observeProfile()
        .flatMapLatest { profile ->
            val today = clock.gameDate(profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)
            focusDao.observeSecondsByDay(today.minusDays(WEEK_DAYS - 1), today).map { rows ->
                (rows.filter { it.gameDate == today }.sumOf { it.seconds } / SECONDS_PER_MINUTE).toInt() to
                    (rows.sumOf { it.seconds } / SECONDS_PER_MINUTE).toInt()
            }
        }

    val uiState: StateFlow<FocusCardState> = combine(week, observeFocusGoal(dayRefresher.ticks)) { (todayMinutes, weekMinutes), goal ->
        FocusCardState(todayMinutes = todayMinutes, weekMinutes = weekMinutes, goal = goal?.goal ?: 0, streak = goal?.streak ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FocusCardState())
}

data class FocusDayUi(val date: LocalDate, val minutes: Int)

/** [name] is null for sessions with no subject. */
data class FocusSubjectUi(val name: String?, val minutes: Int)

data class FocusReportUiState(
    val loading: Boolean = true,
    val days: List<FocusDayUi> = emptyList(),
    val subjects: List<FocusSubjectUi> = emptyList(),
) {
    val totalMinutes: Int get() = days.sumOf { it.minutes }
}

/** The weekly focus report: minutes per day and per subject over the last 7 game-days (GD §8.1 P8). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusReportViewModel @Inject constructor(
    hunterDao: HunterDao,
    focusDao: FocusDao,
    clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<FocusReportUiState> = hunterDao.observeProfile()
        .flatMapLatest { profile ->
            val today = clock.gameDate(profile?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR)
            val from = today.minusDays(WEEK_DAYS - 1)
            combine(
                focusDao.observeSecondsByDay(from, today),
                focusDao.observeSecondsBySubject(from, today),
                focusDao.observeAll(),
            ) { byDay, bySubject, subjects ->
                val names = subjects.associate { it.id to it.name }
                val seconds = byDay.associate { it.gameDate to it.seconds }
                FocusReportUiState(
                    loading = false,
                    days = (0 until WEEK_DAYS).map { from.plusDays(it) }
                        .map { FocusDayUi(it, ((seconds[it] ?: 0L) / SECONDS_PER_MINUTE).toInt()) },
                    subjects = bySubject
                        .map { FocusSubjectUi(it.subjectId?.let(names::get), (it.seconds / SECONDS_PER_MINUTE).toInt()) }
                        .filter { it.minutes > 0 }
                        .sortedByDescending { it.minutes },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FocusReportUiState())
}

/** What the Focus tab shows beyond [FocusCardState]. Grows as focus tools are added. */
data class FocusHubUiState(
    /** Reels watched today; null while the Reel counter is off. */
    val reelsToday: Int? = null,
    val date: LocalDate? = null,
    val screenTime: ScreenTimeTileModel = ScreenTimeTileModel.Loading,
    val lockEnabled: Boolean = false,
    val allowedCount: Int = 0,
    val nightsEnabled: Boolean = false,
    /** Minutes since midnight; only meaningful while [nightsEnabled]. */
    val nightsBedtimeMinute: Int = 0,
    /** Focus Lock or Reel counter is on but Android is not allowing what it needs. */
    val permissionsNeedAttention: Boolean = false,
)

/** The reads that change outside the app (usage access, accessibility, battery), taken again on every resume. */
private data class SystemReads(
    val date: LocalDate? = null,
    val screenTime: ScreenTimeTileModel = ScreenTimeTileModel.Loading,
    val focusLockReady: Boolean = false,
    val reelAccessibility: Boolean = false,
    val batteryUnrestricted: Boolean = false,
)

@HiltViewModel
class FocusHubViewModel @Inject constructor(
    reelData: ReelWidgetData,
    prefs: UiPrefsRepository,
    bedtime: BedtimeReader,
    private val loadScreenTime: LoadScreenTimeSummaryUseCase,
    private val focusAccess: FocusLockSystemAccess,
    private val reelAccess: ReelSystemAccess,
    private val clock: Clock,
) : ViewModel() {
    private val reads = MutableStateFlow(SystemReads())

    val uiState: StateFlow<FocusHubUiState> = combine(reelData.observe(), prefs.prefs, bedtime.observeSettings(), reads) { reels, p, nights, system ->
        val latest = nights.maxByOrNull { it.effectiveFrom }
        val lockNeeds = p.focusLock && !(system.focusLockReady && system.batteryUnrestricted)
        val reelNeeds = reels != null && !(system.reelAccessibility && system.batteryUnrestricted)
        FocusHubUiState(
            reelsToday = reels,
            date = system.date,
            screenTime = system.screenTime,
            lockEnabled = p.focusLock,
            allowedCount = p.allowedApps.size,
            nightsEnabled = latest?.enabled == true,
            nightsBedtimeMinute = latest?.bedtimeMinute ?: 0,
            permissionsNeedAttention = lockNeeds || reelNeeds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FocusHubUiState())

    /** Back on the tab: read usage and the Android switches again. A failed screen-time read shows as not tracked, never a made-up number. */
    fun refresh() {
        reads.update {
            it.copy(
                date = clock.now().atZone(clock.zone()).toLocalDate(),
                focusLockReady = focusAccess.permissionsReady(),
                reelAccessibility = reelAccess.accessibilityEnabled(),
                batteryUnrestricted = focusAccess.batteryUnrestricted(),
            )
        }
        viewModelScope.launch {
            val tile = try {
                loadScreenTime()?.toTile() ?: ScreenTimeTileModel.NotTracked
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Focus screen time read failed")
                ScreenTimeTileModel.NotTracked
            }
            reads.update { it.copy(screenTime = tile) }
        }
    }
}
