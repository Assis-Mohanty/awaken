package com.assismohanty.awaken.feature.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.components.CrystalCell
import com.assismohanty.awaken.core.designsystem.components.CrystalGalleryGrid
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.crystalTint
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.CrystalEntry
import com.assismohanty.awaken.core.domain.ObserveCrystalGalleryUseCase
import com.assismohanty.awaken.engine.CrystalForm
import com.assismohanty.awaken.engine.CrystalOutcome
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CrystalGalleryUiState(
    val loading: Boolean = true,
    /** Every finished run, newest first, before the subject filter. */
    val entries: List<CrystalEntry> = emptyList(),
    /** Null shows every subject. */
    val subjectId: String? = null,
) {
    /** The subjects that appear in the gallery, for the filter chips. */
    val subjects: List<Pair<String, String>>
        get() = entries.mapNotNull { e -> e.subjectId?.let { id -> e.subjectName?.let { id to it } } }.distinct()
    val visible: List<CrystalEntry> get() = entries.filter { subjectId == null || it.subjectId == subjectId }
}

sealed interface CrystalGalleryEvent {
    data class Filter(val subjectId: String?) : CrystalGalleryEvent
}

@HiltViewModel
class CrystalGalleryViewModel @Inject constructor(observe: ObserveCrystalGalleryUseCase) : ViewModel() {
    private val filter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CrystalGalleryUiState> = combine(observe(), filter) { entries, subjectId ->
        CrystalGalleryUiState(loading = false, entries = entries, subjectId = subjectId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CrystalGalleryUiState())

    fun onEvent(event: CrystalGalleryEvent) {
        when (event) {
            is CrystalGalleryEvent.Filter -> filter.update { if (it == event.subjectId) null else event.subjectId }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun CrystalGalleryRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CrystalGalleryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CrystalGalleryScreen(state, viewModel::onEvent, onBack, modifier)
}

/** The crystal gallery (DESIGN_SYSTEM §9.3): one crystal per finished focus run, a month at a time, filterable by subject. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrystalGalleryScreen(state: CrystalGalleryUiState, onEvent: (CrystalGalleryEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AwakenTheme.colors
    GateBackground(rank = Rank.E, modifier = modifier) {
        LargeTitleScaffold(title = lexString(R.string.crystals_title), onBack = onBack) {
            when {
                state.loading -> Unit
                state.entries.isEmpty() -> EmptyState(line = lexString(R.string.crystals_empty), actionLabel = null, onAction = {})
                else -> {
                    if (state.subjects.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            state.subjects.forEach { (id, name) ->
                                ChoiceChip(text = name, selected = state.subjectId == id, onClick = { onEvent(CrystalGalleryEvent.Filter(id)) })
                            }
                        }
                    }
                    state.visible.groupBy { YearMonth.from(it.gameDate) }.forEach { (month, runs) ->
                        Text(monthName(month, currentLocale()), style = AwakenTheme.type.label, color = colors.textSecondary)
                        CrystalGalleryGrid(runs.map { it.toCell() })
                    }
                    Text(lexString(R.string.crystals_note), style = AwakenTheme.type.caption, color = colors.textMuted)
                }
            }
        }
    }
}

private fun monthName(month: YearMonth, locale: Locale): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))


@Composable
private fun CrystalEntry.toCell(): CrystalCell {
    val whose = subjectName ?: stat.label()
    val status = lexString(
        when (outcome) {
            CrystalOutcome.CLEARED -> R.string.crystals_status_cleared
            CrystalOutcome.COLLAPSED -> R.string.crystals_status_collapsed
            CrystalOutcome.LOGGED -> R.string.crystals_status_logged
        },
    )
    val date = gameDate.format(DateTimeFormatter.ofPattern("d MMMM", currentLocale()))
    return CrystalCell(
        key = runId,
        stage = stage,
        tint = crystalTint(colourIndex, stat),
        tier = tier,
        cracked = form == CrystalForm.CRACKED,
        shard = form == CrystalForm.SHARD,
        caption = lexString(R.string.crystals_caption, minutes),
        description = lexString(R.string.crystals_cell_cd, whose, minutes, status, date),
    )
}

// Previews

private fun entry(id: String, day: Long, outcome: CrystalOutcome, stage: Int, tier: GateRank, minutes: Int, subject: String?, colour: Int?, stat: Stat = Stat.INT) =
    CrystalEntry(
        runId = id,
        startedAt = Instant.parse("2026-11-02T08:00:00Z"),
        gameDate = LocalDate.of(2026, 11, 3).minusDays(day),
        form = when (outcome) {
            CrystalOutcome.CLEARED -> CrystalForm.WHOLE
            CrystalOutcome.COLLAPSED -> CrystalForm.CRACKED
            CrystalOutcome.LOGGED -> CrystalForm.SHARD
        },
        outcome = outcome,
        stage = stage,
        tier = tier,
        minutes = minutes,
        subjectId = subject,
        subjectName = subject,
        stat = stat,
        colourIndex = colour,
    )

private val previewEntries = listOf(
    entry("1", 0, CrystalOutcome.CLEARED, 5, GateRank.C, 50, "Maths", 1),
    entry("2", 1, CrystalOutcome.COLLAPSED, 3, GateRank.D, 25, "Maths", 1),
    entry("3", 2, CrystalOutcome.LOGGED, 0, GateRank.E, 9, null, null, Stat.WIL),
    entry("4", 4, CrystalOutcome.CLEARED, 5, GateRank.B, 90, "Code", 4),
    entry("5", 5, CrystalOutcome.CLEARED, 5, GateRank.E, 15, null, null),
    entry("6", 40, CrystalOutcome.CLEARED, 5, GateRank.D, 25, "Reading", 2),
)

@PhonePreviews
@Composable
private fun CrystalGalleryPreview() = AwakenTheme(reduceMotion = true) {
    CrystalGalleryScreen(CrystalGalleryUiState(loading = false, entries = previewEntries), {}, {})
}

@PhonePreviews
@Composable
private fun CrystalGalleryFilteredPreview() = AwakenTheme(reduceMotion = true) {
    CrystalGalleryScreen(CrystalGalleryUiState(loading = false, entries = previewEntries, subjectId = "Maths"), {}, {})
}

@PhonePreviews
@Composable
private fun CrystalGalleryEmptyPreview() = AwakenTheme(reduceMotion = true) {
    CrystalGalleryScreen(CrystalGalleryUiState(loading = false), {}, {})
}

