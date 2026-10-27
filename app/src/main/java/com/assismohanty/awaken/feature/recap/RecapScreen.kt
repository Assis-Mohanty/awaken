package com.assismohanty.awaken.feature.recap

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.RecapCard
import com.assismohanty.awaken.core.designsystem.components.RecapPager
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.ShareCapture
import com.assismohanty.awaken.core.designsystem.components.ShareCardModel
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.currentLocale
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.formatOneDecimal
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.lexicon.lexPlural
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.RecapModel
import com.assismohanty.awaken.engine.RecapPayload
import com.assismohanty.awaken.engine.insight.BucketInsight
import com.assismohanty.awaken.engine.insight.InsightRule
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.feature.status.insightText
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun RecapRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = lexString(R.string.share_report_chooser)
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType(PNG_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onEvent(RecapEvent.ShareHandled)
    }
    RecapScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Recap (DESIGN_SYSTEM §8.2, GD §14.6): 3–5 story pages; a page whose data is missing is left out. Grants nothing. */
@Composable
fun RecapScreen(state: RecapUiState, onEvent: (RecapEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val recap = state.recap
    LargeTitleScaffold(title = recap?.payload?.let { periodName(it) } ?: lexString(R.string.recap_title), onBack = onBack, modifier = modifier) {
        when {
            state.loading -> SkeletonCard()
            recap == null -> InsightCard(text = lexString(R.string.recap_missing))
            else -> {
                val pages = pages(recap, onShare = { onEvent(RecapEvent.Share) }, shareEnabled = state.hunter != null && !state.capturing)
                val page = state.page.coerceIn(0, pages.lastIndex)
                RecapPager(pageCount = pages.size, page = page, onPageChange = { onEvent(RecapEvent.Page(it)) }) { shown -> pages[shown]() }
                val hunter = state.hunter
                if (state.capturing && hunter != null) {
                    ShareCapture(
                        model = ShareCardModel(hunter.name, hunter.rank, hunter.level, hunter.statLevels, hunter.radarMax, shareStats(recap.payload)),
                        onCaptured = { onEvent(RecapEvent.ShareCaptured(it)) },
                        onFailed = { onEvent(RecapEvent.ShareFailed) },
                    )
                }
            }
        }
    }
}

private typealias Page = @Composable () -> Unit

@Composable
private fun pages(recap: RecapModel, onShare: () -> Unit, shareEnabled: Boolean): List<Page> {
    val p = recap.payload
    val kicker = periodName(p).uppercase(currentLocale())
    return buildList {
        add {
            RecapCard(kicker, formatCount(p.questsCompleted), lexString(R.string.recap_quests), listOf(lexString(R.string.recap_levels) to formatCount(p.levelsGained)))
        }
        val effortRows = listOfNotNull(
            p.gateHours?.let { lexString(R.string.recap_focus_hours) to formatOneDecimal(it) },
            p.consistencyEligible.takeIf { it > 0 }?.let {
                lexString(R.string.recap_consistency) to lexPlural(R.plurals.recap_consistency_value, it, p.consistencyCleared, it)
            },
        )
        val streak = p.bestStreak
        if (streak != null || effortRows.isNotEmpty()) {
            add {
                RecapCard(
                    kicker = lexString(R.string.recap_effort).uppercase(currentLocale()),
                    hero = streak?.let { formatCount(it) } ?: effortRows.first().second,
                    heroLabel = if (streak != null) lexString(R.string.recap_best_streak) else effortRows.first().first,
                    rows = if (streak != null) effortRows else effortRows.drop(1),
                )
            }
        }
        val top = p.topStat
        if (top != null) {
            add {
                RecapCard(
                    kicker = lexString(R.string.recap_growth).uppercase(currentLocale()),
                    hero = top.label(),
                    heroLabel = lexString(R.string.recap_top_stat),
                    rows = listOfNotNull(p.topStatGrowth?.let { lexString(R.string.recap_stat_levels) to formatCount(it) }),
                )
            }
        }
        if (p.hasBody) {
            add {
                val rows = listOfNotNull(
                    p.distanceKm?.let { lexString(R.string.recap_distance) to lexString(R.string.recap_distance_value, formatOneDecimal(it)) },
                    p.averageSteps?.let { lexString(R.string.recap_steps) to formatCount(it) },
                    p.averageSleepHours?.let { lexString(R.string.recap_sleep) to lexString(R.string.recap_sleep_value, formatOneDecimal(it)) },
                )
                RecapCard(lexString(R.string.recap_body).uppercase(currentLocale()), rows.first().second, rows.first().first, rows.drop(1))
            }
        }
        add {
            RecapCard(
                kicker = lexString(R.string.recap_summary).uppercase(currentLocale()),
                hero = formatCount(p.achievementsEarned),
                heroLabel = lexString(R.string.recap_achievements),
                rows = emptyList(),
            ) {
                recap.insight?.let { InsightCard(text = insightText(it)) }
                SecondaryButton(text = lexString(R.string.action_share_report), onClick = onShare, enabled = shareEnabled, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun shareStats(p: RecapPayload): List<Pair<String, String>> = listOfNotNull(
    lexString(R.string.recap_quests) to formatCount(p.questsCompleted),
    lexString(R.string.recap_levels) to formatCount(p.levelsGained),
    p.bestStreak?.let { lexString(R.string.recap_best_streak) to formatCount(it) },
)

/** "October 2026" for a month, "2026" for a year. */
@Composable
private fun periodName(p: RecapPayload): String {
    val start = LocalDate.parse(p.start)
    return if (p.yearly) start.year.toString() else YearMonth.from(start).format(DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale()))
}

private const val PNG_MIME = "image/png"

private val previewPayload = RecapPayload(
    periodKey = "M:2026-10",
    yearly = false,
    start = "2026-10-01",
    end = "2026-10-31",
    questsCompleted = 112,
    levelsGained = 3,
    gateHours = 12.5,
    bestStreak = 14,
    consistencyCleared = 24,
    consistencyEligible = 29,
    topStat = Stat.STR,
    topStatGrowth = 2,
    distanceKm = 84.2,
    averageSteps = 8_120,
    averageSleepHours = 7.2,
    achievementsEarned = 4,
)

private val previewHunter = RecapHunter("Assis", Rank.D, 14, Stat.entries.associateWith { 6f }, 10f)

@PhonePreviews
@Composable
private fun RecapFirstPagePreview() {
    AwakenTheme { RecapScreen(RecapUiState(loading = false, recap = RecapModel(previewPayload, null), hunter = previewHunter), {}, {}) }
}

@PhonePreviews
@Composable
private fun RecapSummaryPreview() {
    val insight = BucketInsight(InsightRule.SLEEP, aIsHigher = true, higherMean = 4.1, lowerMean = 3.0, percent = 37)
    AwakenTheme {
        RecapScreen(RecapUiState(loading = false, recap = RecapModel(previewPayload, insight), hunter = previewHunter, page = 4), {}, {})
    }
}

@PhonePreviews
@Composable
private fun RecapMissingPreview() {
    AwakenTheme { RecapScreen(RecapUiState(loading = false, recap = null), {}, {}) }
}
