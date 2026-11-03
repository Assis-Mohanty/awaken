package com.assismohanty.awaken.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ListRow
import com.assismohanty.awaken.core.designsystem.components.QuietButton
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
 * Reads the phone-down row once a minute while Today is on screen (GD §16.12). Read-only: the settling itself runs in the
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

/**
 * Where the phone-down row sits in Today's plan (DECLUTTER_PLAN §4): tonight's window in Evening, last night's result in
 * Morning. Without usage access it sits in Evening and offers to fix that. Null: nothing to show.
 */
fun bedtimeBlock(model: BedtimeToday, currentBlock: TimeOfDay): TimeOfDay? = when {
    model.needsAccess -> TimeOfDay.EVENING
    currentBlock == TimeOfDay.EVENING && model.tonight != null -> TimeOfDay.EVENING
    model.lastNight != null -> TimeOfDay.MORNING
    else -> null
}

/**
 * The phone-down row (DESIGN_SYSTEM §9.3), a plan row. Evening shows tonight's window and what keeping it pays, with a
 * wind-down link from 30 minutes before bedtime; Morning shows last night's result. Without usage access it offers
 * nothing to earn and says why.
 */
@Composable
fun BedtimeRow(
    model: BedtimeToday,
    currentBlock: TimeOfDay,
    onWindDown: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line: String
    var windDown = false
    when {
        model.needsAccess -> line = lexString(R.string.bedtime_card_needs_access)
        currentBlock == TimeOfDay.EVENING && model.tonight != null -> {
            line = tonightLine(model.tonight)
            windDown = !model.now.isBefore(model.tonight.windDownFrom) && model.now.isBefore(model.tonight.window.end)
        }
        model.lastNight != null -> line = lastNightLine(model.lastNight)
        else -> return
    }
    ListRow(
        title = lexString(R.string.bedtime_card_title),
        icon = AwakenGlyphs.Moon,
        iconTint = AwakenTheme.colors.accent,
        secondary = line,
        modifier = modifier,
        trailing = when {
            model.needsAccess -> {{ QuietButton(text = lexString(R.string.bedtime_grant_access), onClick = onGrantAccess) }}
            windDown -> {{ QuietButton(text = lexString(R.string.bedtime_wind_down), onClick = onWindDown) }}
            else -> null
        },
    )
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
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, null, previewTonight, previewNow), TimeOfDay.EVENING, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeWindDownPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, null, previewTonight, previewWindow.start.minusSeconds(600)), TimeOfDay.EVENING, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeKeptPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, LastNight.Kept(78), previewTonight, previewNow), TimeOfDay.MORNING, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeBrokenPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, LastNight.Broken, previewTonight, previewNow), TimeOfDay.DAY, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeNoDataPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, LastNight.NoData, null, previewNow), TimeOfDay.MORNING, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeInProgressPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(false, LastNight.InProgress(previewWindow), previewTonight, previewNow), TimeOfDay.MORNING, {}, {}) }
}

@PhonePreviews
@Composable
private fun BedtimeNeedsAccessPreview() = AwakenTheme(reduceMotion = true) {
    AwakenCard(reveal = false) { BedtimeRow(BedtimeToday(true, null, previewTonight, previewNow), TimeOfDay.EVENING, {}, {}) }
}
