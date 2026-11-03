package com.assismohanty.awaken.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveFocusGoalUseCase
import com.assismohanty.awaken.engine.GameDay
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

/** The Focus section on Today: today's focus against the daily goal, the focus streak, and the last 7 game-days. */
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
