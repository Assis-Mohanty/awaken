package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.designsystem.charts.ComparisonRow
import com.assismohanty.awaken.core.designsystem.charts.ChartSpec
import com.assismohanty.awaken.core.designsystem.charts.Chart
import com.assismohanty.awaken.core.designsystem.components.BlockState
import com.assismohanty.awaken.core.designsystem.components.TimeBlock
import com.assismohanty.awaken.core.designsystem.components.TimeBlockHeader
import com.assismohanty.awaken.core.designsystem.components.StatTintCard
import com.assismohanty.awaken.core.designsystem.components.Trend
import com.assismohanty.awaken.core.designsystem.components.TrendPill
import com.assismohanty.awaken.core.designsystem.components.MetricLabel
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.InsightCard
import com.assismohanty.awaken.core.designsystem.components.StreakModule
import com.assismohanty.awaken.core.designsystem.components.LevelModule
import com.assismohanty.awaken.core.designsystem.components.RingCard
import com.assismohanty.awaken.core.designsystem.components.RingSpec
import com.assismohanty.awaken.core.designsystem.components.MetricCard
import com.assismohanty.awaken.core.designsystem.components.Spark
import com.assismohanty.awaken.core.designsystem.components.DeltaPill
import com.assismohanty.awaken.core.designsystem.components.FloatingNavBar
import com.assismohanty.awaken.core.designsystem.components.NavBarItem
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.SegmentedControl
import com.assismohanty.awaken.core.designsystem.components.IconCircle
import com.assismohanty.awaken.core.designsystem.charts.TrendChart
import com.assismohanty.awaken.core.designsystem.charts.TrendSeries
import com.assismohanty.awaken.core.designsystem.components.HeaderIconButton
import com.assismohanty.awaken.core.designsystem.components.GreetingHeader
import com.assismohanty.awaken.core.designsystem.components.SegmentedTabs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.DifficultyChip
import com.assismohanty.awaken.core.designsystem.components.EmptyState
import com.assismohanty.awaken.core.designsystem.components.GateBackground
import com.assismohanty.awaken.core.designsystem.components.GateDial
import com.assismohanty.awaken.core.designsystem.components.Heatmap
import com.assismohanty.awaken.core.designsystem.components.HeatmapDay
import com.assismohanty.awaken.core.designsystem.components.HunterHeader
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuestRow
import com.assismohanty.awaken.core.designsystem.components.QuestRowState
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.RadarChart
import com.assismohanty.awaken.core.designsystem.components.RankCrest
import com.assismohanty.awaken.core.designsystem.components.RewardModel
import com.assismohanty.awaken.core.designsystem.components.RewardSheet
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.ShareCard
import com.assismohanty.awaken.core.designsystem.components.ShareCardModel
import com.assismohanty.awaken.core.designsystem.components.StatChip
import com.assismohanty.awaken.core.designsystem.components.SystemToast
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.VerifiedBadge
import com.assismohanty.awaken.core.designsystem.components.WEEKS
import com.assismohanty.awaken.core.designsystem.components.XpBar
import com.assismohanty.awaken.core.designsystem.components.previewHunter
import com.assismohanty.awaken.core.designsystem.components.previewQuests
import com.assismohanty.awaken.core.designsystem.components.previewReward
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.engine.StatLevel
import com.assismohanty.awaken.engine.model.BreakdownItem
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * Every design-system component on one screen for on-device review (Phase 5). Toggles: rank tint, reduce motion.
 * Buttons replay the animated moments (window reveal, reward sheet, level-up ceremony, toast).
 */
@Composable
fun DesignSystemCatalog(onBack: () -> Unit) {
    var rank by rememberSaveable { mutableStateOf(Rank.C) }
    var reduceMotion by rememberSaveable { mutableStateOf(false) }
    var look by rememberSaveable { mutableStateOf(CatalogLook.LIGHT) }
    AwakenTheme(
        darkTheme = look == CatalogLook.DARK,
        reduceMotion = reduceMotion,
    ) {
        CatalogContent(
            rank = rank,
            reduceMotion = reduceMotion,
            onRank = { rank = it },
            onReduceMotion = { reduceMotion = it },
            onBack = onBack,
            look = look,
            onLook = { look = it },
        )
    }
}

/** The two looks to compare on device (DESIGN_SYSTEM §2.1). */
private enum class CatalogLook { LIGHT, DARK }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogContent(
    rank: Rank,
    reduceMotion: Boolean,
    onRank: (Rank) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onBack: () -> Unit,
    look: CatalogLook = CatalogLook.LIGHT,
    onLook: (CatalogLook) -> Unit = {},
) {
    val colors = AwakenTheme.palette
    var period by remember { mutableIntStateOf(1) }
    var morningOpen by remember { mutableStateOf(false) }
    val type = AwakenTheme.type
    val rankColor = colors.rank(rank)
    var revealKey by remember { mutableIntStateOf(0) }
    var reward by remember { mutableStateOf<RewardModel?>(null) }
    var toast by remember { mutableStateOf(false) }
    var xp by remember { mutableLongStateOf(1_240L) }
    val quests = remember { mutableStateListOf(*previewQuests.toTypedArray()) }

    LaunchedEffect(toast) {
        if (toast) {
            delay(TOAST_MS)
            toast = false
        }
    }

    GateBackground(rank = rank) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    QuietButton(stringResource(R.string.debug_back), onClick = onBack)
                    Text(stringResource(R.string.catalog_title), style = type.metricL, color = colors.bone)
                }
            }

            item {
                Section(R.string.catalog_controls) {
                    SegmentedTabs(
                        options = listOf("Light", "Dark"),
                        selected = look.ordinal,
                        onSelect = { onLook(CatalogLook.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Rank.entries.forEach { r ->
                            if (r == rank) PrimaryButton(r.name, onClick = { onRank(r) })
                            else SecondaryButton(r.name, onClick = { onRank(r) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Switch(
                            checked = reduceMotion,
                            onCheckedChange = onReduceMotion,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.system, checkedThumbColor = colors.void),
                        )
                        Text(stringResource(R.string.catalog_reduce_motion), style = type.body, color = colors.bone)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SecondaryButton(stringResource(R.string.catalog_replay_reveal), onClick = { revealKey++ })
                        SecondaryButton(stringResource(R.string.catalog_reward), onClick = { reward = previewReward })
                        SecondaryButton(stringResource(R.string.catalog_reward_level), onClick = { reward = levelReward })
                        SecondaryButton(stringResource(R.string.catalog_toast), onClick = { toast = true })
                        SecondaryButton(stringResource(R.string.catalog_add_xp), onClick = { xp = (xp + XP_STEP) % previewHunter.xpForLevel })
                    }
                }
            }

            item {
                Section(R.string.catalog_hero) {
                    GreetingHeader(greeting = "Good afternoon, Assis", subtitle = "Tuesday, 27 October 2026") {
                        HeaderIconButton(AwakenGlyphs.Portal, "Open focus sessions", onClick = {})
                        HeaderIconButton(AwakenGlyphs.Vault, "Open shop", onClick = {})
                    }
                    RingCard(
                        label = "Today", value = "3", unit = "of 10", secondary = "done today",
                        rings = listOf(RingSpec(0.3f, AwakenTheme.colors.positive)), description = "3 of 10 done today",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                        LevelModule(level = 24, rank = rank, xpIntoLevel = xp, xpForLevel = previewHunter.xpForLevel, modifier = Modifier.weight(1f))
                        StreakModule(streak = 12, best = 30, modifier = Modifier.weight(1f))
                    }
                    InsightCard("On days you sleep 7 h or more, you complete 38% more quests.", onClick = {})
                    MetricCard(
                        label = "Steps", value = "8,412", timestamp = "10:50", secondary = "84% of goal",
                        icon = AwakenGlyphs.Vitality, iconTint = AwakenTheme.colors.stat(Stat.VIT),
                        spark = Spark(listOf(2f, 5f, 3f, 6f, 4f, 7f, 8f), bars = true), description = "Steps 8,412", onClick = {},
                    )
                    MetricCard(
                        label = "Body weight", value = "82.0", unit = "kg", timestamp = "03:52",
                        icon = AwakenGlyphs.Strength, iconTint = AwakenTheme.colors.stat(Stat.STR),
                        delta = { DeltaPill("+0.0 kg", Trend.FLAT) }, spark = Spark(listOf(82.4f, 82.1f, 82.3f, 82f, 82f)),
                        description = "Body weight 82.0 kg", onClick = {},
                    )
                    FloatingNavBar(
                        items = listOf(
                            NavBarItem("Today", AwakenGlyphs.Home, AwakenGlyphs.HomeSolid),
                            NavBarItem("Gym", AwakenGlyphs.Dumbbell, AwakenGlyphs.DumbbellSolid),
                            NavBarItem("Diet", AwakenGlyphs.Bowl, AwakenGlyphs.BowlSolid),
                            NavBarItem("Progress", AwakenGlyphs.Bars, AwakenGlyphs.BarsSolid),
                        ),
                        selectedIndex = period % 4, onSelect = { period = it },
                    )
                    SkeletonCard()
                }
            }

            item {
                Section(R.string.catalog_metrics) {
                    SegmentedControl(listOf("Week", "Month", "Year"), selected = period, onSelect = { period = it }, modifier = Modifier.fillMaxWidth())
                    AwakenCard(title = "Quests per day", reveal = false) {
                        TrendChart(
                            periods = listOf("Week", "Month", "Year"),
                            series = List(3) { k ->
                                val values = listOf(2.1f, 2.6f, 2.4f, 3.0f, 2.8f, 3.3f, 3.4f).map { it + k * 0.3f }
                                TrendSeries(values, listOf("Mon", "Thu", "Sun"), values.map { "%.1f".format(it) }, values.average().toFloat(), "Average")
                            },
                            selected = period.coerceIn(0, 2),
                            onSelect = { period = it },
                            description = "Quests per day",
                            color = AwakenTheme.colors.chartSeries2,
                        )
                    }
                    AwakenCard(title = "Quests per day", reveal = false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.Bottom) {
                            MetricLabel("Result", "3.4", large = true)
                            MetricLabel("Trend", "+12%")
                            TrendPill("+0.4", Trend.UP)
                        }
                        Chart(
                            ChartSpec.TrendLine(
                                values = listOf(2.1f, 2.6f, 2.4f, 3.0f, 2.8f, 3.3f, 3.4f).let { if (period == 0) it else it.reversed() + it },
                                labels = listOf("Mon 5", "Mon 12", "Mon 19", "Mon 26"),
                                description = "Quests per day, rising from 2.1 to 3.4",
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTintCard(Stat.STR, "Strength", "+2 levels", modifier = Modifier.weight(1f))
                        StatTintCard(Stat.INT, "Intellect", "Stable", modifier = Modifier.weight(1f))
                        StatTintCard(Stat.CHA, "Charisma", "1 of 9", modifier = Modifier.weight(1f))
                    }
                    AwakenCard(title = "Daily steps", reveal = false) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("2,503", style = type.metricL, color = colors.bone, modifier = Modifier.weight(1f))
                            Chart(
                                ChartSpec.Sparkline(listOf(3f, 5f, 4f, 7f, 6f, 9f, 8f), description = "Steps over 7 days"),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Chart(
                        ChartSpec.Comparison(
                            rows = listOf(
                                ComparisonRow("After 7 h+ sleep", "3.3 / day", 1f, colors.statVit),
                                ComparisonRow("After less sleep", "2.4 / day", 0.73f, colors.statWil),
                            ),
                            description = "3.3 quests a day after 7 hours of sleep or more, 2.4 after less",
                        ),
                    )
                }
            }

            item {
                Section(R.string.catalog_timeline) {
                    TimeBlockHeader(TimeBlock.MORNING, done = 1, total = 1, state = BlockState.PAST, expanded = morningOpen, onToggle = { morningOpen = !morningOpen })
                    if (morningOpen) AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero) { QuestRow(previewQuests[1], onComplete = {}, onOpen = {}, showDivider = false) }
                    TimeBlockHeader(TimeBlock.DAY, done = 0, total = 2, state = BlockState.CURRENT, expanded = true, onToggle = {})
                    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero) {
                        QuestRow(previewQuests[0].copy(id = "c", title = "Take a short walk", isMain = false, comeback = true), onComplete = {}, onOpen = {}, showDivider = false)
                        QuestRow(previewQuests[0], onComplete = {}, onOpen = {})
                        QuestRow(previewQuests[2], onComplete = {}, onOpen = {})
                    }
                    TimeBlockHeader(TimeBlock.ANYTIME, done = 0, total = 1, state = BlockState.UPCOMING, expanded = true, onToggle = {})
                    AwakenCard(reveal = false, contentPadding = Spacing.listCard, spacing = Spacing.zero) { QuestRow(previewQuests[4], onComplete = {}, onOpen = {}, showDivider = false) }
                }
            }

            item {
                Section(R.string.catalog_type) {
                    Text("61", style = type.metricXL, color = colors.bone)
                    Text("2,503", style = type.metricL, color = colors.bone)
                    Text("Good afternoon, Assis", style = type.headline, color = colors.bone)
                    Text("Latest measurements", style = type.section, color = colors.bone)
                    Text("Result", style = type.overline, color = colors.mist)
                    Text("Today's plan", style = type.windowTitle, color = colors.bone)
                    Text("+144 XP · 1,920", style = type.numeric, color = colors.bone)
                    Text("Do push-ups", style = type.title, color = colors.bone)
                    Text("Complete 80 push-ups. Sets of any size.", style = type.body, color = colors.bone)
                    Text("80 reps · Hard", style = type.label, color = colors.mist)
                    Text("Self-report limit reached — verified quests still count fully.", style = type.caption, color = colors.mist)
                }
            }

            item {
                Section(R.string.catalog_colours) {
                    val swatches = listOf(
                        colors.void, colors.depth, colors.panel, colors.panelRaised, colors.edge, colors.bone, colors.mist, colors.fog,
                        colors.ember, colors.crit, colors.verified,
                    ) + Stat.entries.flatMap { listOf(colors.stat(it), colors.statTint(it)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        swatches.forEach { Box(Modifier.size(AwakenTheme.dimens.icon).background(it)) }
                    }
                }
            }

            item {
                Section(R.string.catalog_glyphs) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AwakenGlyphs.all.forEach { Icon(it, it.name, tint = colors.bone, modifier = Modifier.size(AwakenTheme.dimens.icon)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Stat.entries.forEach { IconCircle(AwakenGlyphs.stat(it), colors.stat(it)) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { Rank.entries.forEach { RankCrest(it, AwakenTheme.dimens.crestSmall) } }
                }
            }

            item {
                Section(R.string.catalog_buttons_chips) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PrimaryButton("Complete quest", onClick = {})
                        PrimaryButton("Complete quest", onClick = {}, enabled = false)
                        PrimaryButton("Abandon gate", onClick = {}, color = colors.ember)
                        SecondaryButton("Reroll", onClick = {})
                        QuietButton("Not now", onClick = {})
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Stat.entries.forEach { StatChip(it) }
                        Difficulty.entries.forEach { DifficultyChip(it) }
                        VerifiedBadge(VerificationSource.HEALTH)
                        VerifiedBadge(VerificationSource.TIMER)
                        Pill(stringResource(R.string.chip_main), colors.bone, background = colors.panelRaised)
                        Pill(stringResource(R.string.chip_comeback), colors.ember)
                    }
                }
            }

            item {
                Section(R.string.catalog_header) {
                    HunterHeader(previewHunter.copy(rank = rank, xpIntoLevel = xp))
                    XpBar(level = 1, xpIntoLevel = xp % 112, xpForLevel = 112)
                }
            }

            item(key = "directive-$revealKey") {
                SystemWindow(title = stringResource(R.string.system_title_daily_directive), accent = rankColor) {
                    quests.forEachIndexed { index, quest ->
                        QuestRow(
                            model = quest,
                            onComplete = {
                                quests[index] = quest.copy(state = QuestRowState.DONE)
                                reward = previewReward
                            },
                            onOpen = {},
                            showDivider = index > 0,
                        )
                    }
                    QuietButton(stringResource(R.string.catalog_reset_quests), onClick = {
                        quests.clear()
                        quests.addAll(previewQuests)
                    })
                }
            }

            item(key = "penalty-$revealKey") {
                SystemWindow(title = stringResource(R.string.system_title_system_warning), accent = colors.ember) {
                    Text("You missed yesterday. One quest stands between you and your comeback.", style = type.body, color = colors.bone)
                    QuestRow(previewQuests.first().copy(id = "penalty", title = "The Way Back", isMain = false), onComplete = {}, onOpen = {}, showDivider = false)
                }
            }

            item {
                Section(R.string.catalog_charts) {
                    val levels = mapOf(Stat.STR to 12f, Stat.INT to 9f, Stat.WIL to 6f, Stat.VIT to 10f, Stat.CHA to 4f)
                    RadarChart(
                        values = levels,
                        previous = mapOf(Stat.STR to 10f, Stat.INT to 9f, Stat.WIL to 5f, Stat.VIT to 7f, Stat.CHA to 4f),
                        maxValue = StatLevel.radarMax(12).toFloat(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Heatmap(days = sampleHeatmap, onDayClick = {})
                }
            }

            item {
                Section(R.string.catalog_gates) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        GateDial(GateRank.D, remaining = Duration.ofMinutes(17).plusSeconds(42), total = Duration.ofMinutes(25), breachUsed = Duration.ofSeconds(20))
                        GateDial(GateRank.B, remaining = Duration.ofMinutes(61), total = Duration.ofMinutes(90), breachUsed = Duration.ofSeconds(80))
                    }
                }
            }

            item {
                Section(R.string.catalog_misc) {
                    EmptyState(line = "No habits yet. Add one the System should track.", actionLabel = "Add habit", onAction = {})
                    ShareCard(sampleShareCard.copy(rank = rank), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        SystemToast(
            visible = toast,
            title = stringResource(R.string.notice_shield_title),
            message = stringResource(R.string.notice_shield_body),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Spacing.md),
        )
    }
    reward?.let { RewardSheet(model = it, onDone = { reward = null }) }
}

@Composable
private fun Section(titleRes: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(titleRes), style = AwakenTheme.type.section, color = AwakenTheme.palette.bone)
        content()
    }
}

private val levelReward = previewReward.copy(levelReached = 10)

private val sampleHeatmap: List<HeatmapDay> = LocalDate.of(2026, 6, 15).let { monday ->
    (0 until WEEKS * 7).map { i -> HeatmapDay(monday.plusDays(i.toLong()), intensity = (i * 7 + i / 3) % 5, restDay = i % 13 == 6) }
}

private val sampleShareCard = ShareCardModel(
    name = previewHunter.name,
    rank = Rank.C,
    level = previewHunter.level,
    statLevels = mapOf(Stat.STR to 12f, Stat.INT to 9f, Stat.WIL to 6f, Stat.VIT to 10f, Stat.CHA to 4f),
    radarMax = 20f,
    weekStats = listOf("Quests" to "23", "Days cleared" to "6", "Best stat" to "STR"),
)

private const val TOAST_MS = 3_000L
private const val XP_STEP = 150L

@PhonePreviews
@Composable
private fun DesignSystemCatalogPreview() {
    AwakenTheme(reduceMotion = true) {
        CatalogContent(rank = Rank.C, reduceMotion = true, onRank = {}, onReduceMotion = {}, onBack = {})
    }
}
