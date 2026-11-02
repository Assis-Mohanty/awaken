package com.assismohanty.awaken.feature.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.BedtimeToday
import com.assismohanty.awaken.core.domain.LastNight
import com.assismohanty.awaken.core.domain.LoadBedtimeTodayUseCase
import com.assismohanty.awaken.core.domain.TonightNight
import com.assismohanty.awaken.engine.BedtimeWindow
import com.assismohanty.awaken.engine.model.TimeOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * Reads the phone-down card once a minute while Today is on screen (GD §16.12). Read-only: the settling itself runs in the
 * screen-time check and the rollover, never here, so opening Today cannot pay or expire anything.
 */
@HiltViewModel
class BedtimeCardViewModel @Inject constructor(private val load: LoadBedtimeTodayUseCase) : ViewModel() {
    val uiState: StateFlow<BedtimeToday?> = flow {
        while (true) {
            emit(
                try {
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Reading the phone-down card failed")
                    null
                },
            )
            delay(REFRESH_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val REFRESH_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Today's phone-down card with its own ViewModel, so the Today model stays about quests. */
@Composable
fun BedtimeCardRoute(
    currentBlock: TimeOfDay,
    onWindDown: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BedtimeCardViewModel = hiltViewModel(),
) {
    val model by viewModel.uiState.collectAsStateWithLifecycle()
    model?.let { BedtimeCard(it, currentBlock, onWindDown, onGrantAccess, modifier) }
}

/**
 * The phone-down card (DESIGN_SYSTEM §9.3). Evening shows tonight's window and what keeping it pays, with a wind-down button
 * from 30 minutes before bedtime; the rest of the day shows last night's result. Without usage access it offers nothing
 * to earn and says why.
 */
@Composable
fun BedtimeCard(
    model: BedtimeToday,
    currentBlock: TimeOfDay,
    onWindDown: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AwakenTheme.colors
    val line: String?
    var windDown = false
    when {
        model.needsAccess -> line = lexString(R.string.bedtime_card_needs_access)
        currentBlock == TimeOfDay.EVENING && model.tonight != null -> {
            line = tonightLine(model.tonight)
            windDown = !model.now.isBefore(model.tonight.windDownFrom) && model.now.isBefore(model.tonight.window.end)
        }
        model.lastNight != null -> line = lastNightLine(model.lastNight)
        else -> line = null
    }
    if (line == null) return
    AwakenCard(reveal = false, modifier = modifier.fillMaxWidth()) {
        Text(lexString(R.string.bedtime_card_title), style = AwakenTheme.type.label, color = colors.textSecondary)
        Text(line, style = AwakenTheme.type.body, color = colors.textPrimary)
        if (model.needsAccess) {
            SecondaryButton(text = lexString(R.string.bedtime_grant_access), onClick = onGrantAccess, modifier = Modifier.fillMaxWidth())
        } else if (windDown) {
            SecondaryButton(text = lexString(R.string.bedtime_wind_down), onClick = onWindDown, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun tonightLine(tonight: TonightNight): String =
    lexString(R.string.bedtime_card_tonight, clock(tonight.window.start), clock(tonight.window.end), tonight.xpAtStake)

@Composable
private fun lastNightLine(last: LastNight): String = when (last) {
    is LastNight.InProgress -> lexString(R.string.bedtime_card_in_progress, clock(last.window.end))
    LastNight.Pending -> lexString(R.string.bedtime_card_pending)
    is LastNight.Kept -> lexString(R.string.bedtime_card_kept, last.xp)
    LastNight.Broken -> lexString(R.string.bedtime_card_broken)
    LastNight.NoData -> lexString(R.string.bedtime_card_no_data)
}

private fun clock(at: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(at)

// Previews

private val previewWindow = BedtimeWindow(Instant.parse("2026-11-02T23:00:00Z"), Instant.parse("2026-11-03T06:30:00Z"))
private val previewTonight = TonightNight(previewWindow, xpAtStake = 78, windDownFrom = previewWindow.start.minusSeconds(1_800))
private val previewNow = Instant.parse("2026-11-02T22:10:00Z")

@PhonePreviews
@Composable
private fun BedtimeEveningPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, null, previewTonight, previewNow), TimeOfDay.EVENING, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeWindDownPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, null, previewTonight, previewWindow.start.minusSeconds(600)), TimeOfDay.EVENING, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeKeptPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, LastNight.Kept(78), previewTonight, previewNow), TimeOfDay.MORNING, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeBrokenPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, LastNight.Broken, previewTonight, previewNow), TimeOfDay.DAY, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeNoDataPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, LastNight.NoData, null, previewNow), TimeOfDay.MORNING, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeInProgressPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(false, LastNight.InProgress(previewWindow), previewTonight, previewNow), TimeOfDay.MORNING, {}, {})
}

@PhonePreviews
@Composable
private fun BedtimeNeedsAccessPreview() = AwakenTheme(reduceMotion = true) {
    BedtimeCard(BedtimeToday(true, null, previewTonight, previewNow), TimeOfDay.EVENING, {}, {})
}
