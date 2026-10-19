package com.assismohanty.awaken.feature.status

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.Chapter
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.ChapterRevealDialog
import com.assismohanty.awaken.core.designsystem.components.Heatmap
import com.assismohanty.awaken.core.designsystem.components.HeatmapDay
import com.assismohanty.awaken.core.designsystem.components.HunterHeader
import com.assismohanty.awaken.core.designsystem.components.HunterHeaderModel
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RadarChart
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.ShareCard
import com.assismohanty.awaken.core.designsystem.components.ShareCardModel
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.XpBar
import com.assismohanty.awaken.core.designsystem.components.achievementConditionRes
import com.assismohanty.awaken.core.designsystem.components.achievementNameRes
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.label
import com.assismohanty.awaken.core.designsystem.components.titleNameRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.AchievementEntry
import com.assismohanty.awaken.core.domain.ChapterEntry
import com.assismohanty.awaken.core.domain.HeatmapEntry
import com.assismohanty.awaken.core.domain.StatProgress
import com.assismohanty.awaken.debug.DebugEntry
import com.assismohanty.awaken.engine.AchievementEvaluator
import com.assismohanty.awaken.engine.DifficultyAdapter
import com.assismohanty.awaken.engine.GameWeek
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.StatusMath
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun StatusRoute(onOpenSettings: () -> Unit, modifier: Modifier = Modifier, viewModel: StatusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_report_chooser)
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType(PNG_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to the picked app.
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onEvent(StatusEvent.ShareHandled)
    }
    StatusScreen(state = state, onEvent = viewModel::onEvent, onOpenSettings = onOpenSettings, modifier = modifier)
}

/** Status (DESIGN_SYSTEM §4.5): header → radar → stats → heatmap → titles & achievements → chapters → weekly report. */
@Composable
fun StatusScreen(
    state: StatusUiState,
    onEvent: (StatusEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val header = state.header ?: return@Column
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            val title = state.equippedTitleId?.let(::titleNameRes)?.let { stringResource(it) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = AwakenGlyphs.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = AwakenTheme.colors.mist,
                    )
                }
            }
            HunterHeader(header.copy(title = title), large = true)
            RadarWindow(state)
            StatsWindow(state.rank, state.stats)
            ActivityWindow(state, onEvent)
            AchievementsWindow(state.rank, state.achievements, state.equippedTitleId, onEvent)
            ChaptersWindow(state.rank, state.chapters, onEvent)
            WeeklyWindow(state, onEvent)
        }
    }
    state.openChapter?.let { entry ->
        ChapterRevealDialog(
            number = state.chapters.indexOf(entry) + 1,
            title = entry.chapter.title,
            body = entry.chapter.body,
            onDismiss = { onEvent(StatusEvent.CloseChapter) },
        )
    }
    if (state.capturing) {
        ShareCapture(
            model = shareCardModel(state),
            onCaptured = { onEvent(StatusEvent.ShareCaptured(it)) },
            onFailed = { onEvent(StatusEvent.ShareFailed) },
        )
    }
}

@Composable
private fun RadarWindow(state: StatusUiState) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.status_window_radar), accent = colors.rank(state.rank), reveal = false) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RadarChart(values = state.radarNow, maxValue = state.radarMax, previous = state.radarLastWeek)
        }
        Text(stringResource(R.string.status_radar_legend), style = AwakenTheme.type.caption, color = colors.mist)
    }
}

@Composable
private fun StatsWindow(rank: Rank, stats: List<StatProgress>) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.status_window_stats), accent = colors.rank(rank), reveal = false) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            stats.forEach { stat ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stat.stat.label(), style = type.label, color = colors.stat(stat.stat), modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.level_short, stat.level), style = type.numericSmall, color = colors.bone)
                    }
                    XpBar(level = stat.level, xpIntoLevel = stat.xpIntoLevel, xpForLevel = stat.xpForLevel)
                }
            }
        }
    }
}

@Composable
private fun ActivityWindow(state: StatusUiState, onEvent: (StatusEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.status_window_activity), accent = colors.rank(state.rank), reveal = false) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Heatmap(days = state.heatmap, onDayClick = { onEvent(StatusEvent.SelectDay(it.date)) })
        }
        val selected = state.selectedDay
        Text(
            text = if (selected == null) stringResource(R.string.status_heatmap_hint) else daySummary(selected),
            style = type.caption,
            color = if (selected == null) colors.mist else colors.bone,
        )
    }
}

@Composable
private fun daySummary(day: HeatmapEntry): String {
    val date = formatDate(day.date)
    return if (day.restDay && day.completions == 0) {
        stringResource(R.string.status_heatmap_rest, date)
    } else {
        pluralStringResource(R.plurals.status_heatmap_day, day.completions, date, day.completions)
    }
}

@Composable
private fun AchievementsWindow(
    rank: Rank,
    achievements: List<AchievementEntry>,
    equippedTitleId: String?,
    onEvent: (StatusEvent) -> Unit,
) {
    val colors = AwakenTheme.colors
    SystemWindow(
        title = stringResource(R.string.status_window_achievements),
        accent = colors.rank(rank),
        reveal = false,
        headerTrailing = {
            Text(
                stringResource(R.string.status_achievements_count, achievements.count { it.unlocked }, achievements.size),
                style = AwakenTheme.type.caption,
                color = colors.mist,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            achievements.chunked(GRID_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { entry ->
                        AchievementCell(entry, equipped = entry.titleId != null && entry.titleId == equippedTitleId, onEvent, Modifier.weight(1f))
                    }
                    repeat(GRID_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AchievementCell(entry: AchievementEntry, equipped: Boolean, onEvent: (StatusEvent) -> Unit, modifier: Modifier) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val name = achievementNameRes(entry.id)?.let { stringResource(it) } ?: entry.id
    val titleName = entry.titleId?.let(::titleNameRes)?.let { stringResource(it) }
    val canEquip = entry.unlocked && entry.titleId != null
    val actionLabel = stringResource(if (equipped) R.string.action_unequip_title else R.string.action_equip_title)
    Column(
        modifier = modifier
            .heightIn(min = AwakenTheme.dimens.touchTarget)
            .then(
                if (canEquip) {
                    Modifier.clickable(onClickLabel = actionLabel, role = Role.Button) {
                        onEvent(StatusEvent.EquipTitle(if (equipped) null else entry.titleId))
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        // Locked entries are silhouetted: dimmed text plus an explicit "Locked" label (never colour alone).
        Text(name, style = type.label, color = if (entry.unlocked) colors.bone else colors.fog)
        if (entry.unlocked) {
            titleName?.let { Text(stringResource(R.string.status_title_reward, it), style = type.caption, color = colors.mist) }
            if (equipped) Pill(text = stringResource(R.string.status_title_equipped), color = colors.system)
        } else {
            Text(stringResource(R.string.status_achievement_locked), style = type.caption, color = colors.fog)
            achievementConditionRes(entry.id)?.let { Text(stringResource(it), style = type.caption, color = colors.mist) }
        }
    }
}

@Composable
private fun ChaptersWindow(rank: Rank, chapters: List<ChapterEntry>, onEvent: (StatusEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.status_window_chapters), accent = colors.rank(rank), reveal = false) {
        chapters.forEachIndexed { index, entry ->
            val number = stringResource(R.string.status_chapter_number, index + 1)
            val readLabel = stringResource(R.string.action_read_chapter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AwakenTheme.dimens.touchTarget)
                    .then(
                        if (entry.unlocked) {
                            Modifier.clickable(onClickLabel = readLabel, role = Role.Button) {
                                onEvent(StatusEvent.OpenChapter(entry.chapter.id))
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(number, style = type.caption, color = colors.mist)
                if (entry.unlocked) {
                    Text(entry.chapter.title, style = type.label, color = colors.bone, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        stringResource(R.string.status_chapter_locked, entry.chapter.unlockLevel),
                        style = type.label,
                        color = colors.fog,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyWindow(state: StatusUiState, onEvent: (StatusEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val report = state.weeklyReport ?: return
    SystemWindow(title = stringResource(R.string.status_window_weekly), accent = colors.rank(state.rank), reveal = false) {
        Text(
            stringResource(R.string.status_weekly_range, formatDate(report.week.monday), formatDate(report.week.sunday)),
            style = type.caption,
            color = colors.mist,
        )
        if (!state.weeklyOpen) {
            Text(stringResource(R.string.status_weekly_available), style = type.body, color = colors.mist)
        } else {
            val none = stringResource(R.string.status_weekly_none)
            ReportLine(stringResource(R.string.status_weekly_xp), formatCount(report.totalXp))
            ReportLine(stringResource(R.string.status_weekly_quests), formatCount(report.questsCompleted))
            Stat.entries.forEach { stat -> ReportLine(stat.label(), "+${formatCount(report.statXpGained.getValue(stat))}") }
            ReportLine(stringResource(R.string.status_weekly_best_day), report.bestDay?.let { formatDate(it) } ?: none)
            ReportLine(stringResource(R.string.status_weekly_neglected), report.mostNeglected.label())
            Text(stringResource(R.string.status_weekly_recal_title), style = type.label, color = colors.bone)
            if (state.projectedRecalibration.isEmpty()) {
                Text(stringResource(R.string.status_weekly_recal_none), style = type.caption, color = colors.mist)
            } else {
                state.projectedRecalibration.forEach { (stat, change) ->
                    val res = if (change == DifficultyAdapter.Change.UP) R.string.status_weekly_recal_harder else R.string.status_weekly_recal_easier
                    Text(stringResource(res, stat.label()), style = type.caption, color = colors.mist)
                }
            }
            SecondaryButton(
                text = stringResource(R.string.action_share_report),
                onClick = { onEvent(StatusEvent.Share) },
                enabled = !state.capturing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (DebugEntry.ENABLED) {
            QuietButton(text = stringResource(R.string.debug_show_weekly), onClick = { onEvent(StatusEvent.ToggleDebugWeekly) })
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    val colors = AwakenTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AwakenTheme.type.body, color = colors.mist, modifier = Modifier.weight(1f))
        Text(value, style = AwakenTheme.type.numericSmall, color = colors.bone)
    }
}

@Composable
private fun shareCardModel(state: StatusUiState): ShareCardModel {
    val report = state.weeklyReport
    return ShareCardModel(
        name = state.name,
        rank = state.rank,
        level = state.level,
        statLevels = state.radarNow,
        radarMax = state.radarMax,
        weekStats = listOf(
            stringResource(R.string.status_weekly_xp) to formatCount(report?.totalXp ?: 0L),
            stringResource(R.string.status_weekly_quests) to formatCount(report?.questsCompleted ?: 0),
            stringResource(R.string.share_streak) to formatCount(state.streak),
        ),
    )
}

/**
 * Lays the share card out at 360×450dp with density 3 (exactly 1080×1350px, DESIGN_SYSTEM §3) and records it into a
 * graphics layer without drawing it on screen, then hands back the bitmap.
 */
@Composable
private fun ShareCapture(model: ShareCardModel, onCaptured: (Bitmap) -> Unit, onFailed: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val captured by rememberUpdatedState(onCaptured)
    val failed by rememberUpdatedState(onFailed)
    Box(modifier = Modifier.size(0.dp).clearAndSetSemantics { }) {
        CompositionLocalProvider(LocalDensity provides Density(SHARE_DENSITY, fontScale = 1f)) {
            ShareCard(
                model = model,
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
            )
        }
    }
    LaunchedEffect(Unit) {
        try {
            // Two frames: one to lay out and record, one so the recording is complete.
            withFrameNanos { }
            withFrameNanos { }
            captured(layer.toImageBitmap().asAndroidBitmap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed()
        }
    }
}

@Composable
private fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}

private const val GRID_COLUMNS = 2
private const val SHARE_DENSITY = 3f
private const val PNG_MIME = "image/png"

internal fun previewStatusState(locked: Boolean = false): StatusUiState {
    val today = LocalDate.parse("2026-10-25")
    val week = GameWeek.of(today)
    val statXp = if (locked) emptyMap() else mapOf(Stat.STR to 5_200L, Stat.INT to 3_240L, Stat.WIL to 1_440L, Stat.VIT to 2_600L, Stat.CHA to 640L)
    val levels = Stat.entries.associateWith { StatLevel.level(statXp[it] ?: 0L) }
    val stats = Stat.entries.map { stat ->
        val xp = statXp[stat] ?: 0L
        val level = levels.getValue(stat)
        StatProgress(stat, level, xp, xp - StatLevel.xpForLevel(level), StatLevel.xpForLevel(level + 1) - StatLevel.xpForLevel(level))
    }
    val start = StatusMath.heatmapStart(today)
    val heatmap = (0 until StatusMath.HEATMAP_WEEKS * 7).map { offset ->
        val date = start.plusDays(offset.toLong())
        val count = if (locked || date > today) 0 else (offset * 7 % 6)
        HeatmapEntry(date, count, StatusMath.heatmapIntensity(count), restDay = offset % 13 == 0)
    }
    val unlocked = if (locked) setOf("awakened") else setOf("awakened", "first_blood", "lvl5", "lvl10", "streak3", "streak7", "crit1")
    return StatusUiState(
        loading = false,
        header = HunterHeaderModel(
            name = "Assis", title = null, level = if (locked) 1 else 14, rank = Rank.D,
            xpIntoLevel = 420, xpForLevel = 712, streak = 9, gold = 1_240,
        ),
        equippedTitleId = if (locked) null else "rising_hunter",
        rank = Rank.D,
        name = "Assis",
        level = 14,
        streak = 9,
        radarNow = levels.mapValues { it.value.toFloat() },
        radarLastWeek = levels.mapValues { (it.value - 1).coerceAtLeast(1).toFloat() },
        radarMax = StatusMath.radarMax(levels.values).toFloat(),
        stats = stats,
        heatmap = heatmap.map { HeatmapDay(it.date, it.intensity, it.restDay) },
        achievements = AchievementEvaluator.all.map { AchievementEntry(it.id, it.gold, it.titleId, it.id in unlocked) },
        chapters = listOf(1, 5, 10, 20, 35, 50).mapIndexed { i, level ->
            ChapterEntry(Chapter("ch$i", level, "Chapter title $i", "Body"), unlocked = !locked && level <= 14 || level == 1)
        },
        weeklyReport = StatusMath.weeklyReport(
            week,
            statXp.mapValues { it.value / 10 },
            mapOf(week.monday to 180L, week.monday.plusDays(3) to 240L),
            questsCompleted = 23,
        ),
        weeklyOpen = !locked,
        projectedRecalibration = if (locked) emptyMap() else mapOf(Stat.STR to DifficultyAdapter.Change.UP),
    )
}

@PhonePreviews
@Composable
private fun StatusScreenPreview() {
    AwakenTheme(reduceMotion = true) { StatusScreen(state = previewStatusState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun StatusScreenNewHunterPreview() {
    AwakenTheme(reduceMotion = true) { StatusScreen(state = previewStatusState(locked = true), onEvent = {}) }
}
