package com.assismohanty.awaken.debug

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
import com.assismohanty.awaken.core.designsystem.components.LevelUpCeremony
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
    AwakenTheme(reduceMotion = reduceMotion) {
        CatalogContent(
            rank = rank,
            reduceMotion = reduceMotion,
            onRank = { rank = it },
            onReduceMotion = { reduceMotion = it },
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogContent(
    rank: Rank,
    reduceMotion: Boolean,
    onRank: (Rank) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val rankColor = colors.rank(rank)
    var revealKey by remember { mutableIntStateOf(0) }
    var reward by remember { mutableStateOf<RewardModel?>(null) }
    var levelUp by remember { mutableStateOf(false) }
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
                    Text(stringResource(R.string.catalog_title), style = type.display, color = colors.bone)
                }
            }

            item {
                Section(R.string.catalog_controls) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Rank.entries.forEach { r ->
                            if (r == rank) PrimaryButton(r.name, onClick = { onRank(r) }, color = colors.rank(r))
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
                        SecondaryButton(stringResource(R.string.catalog_reward_crit), onClick = { reward = critReward })
                        SecondaryButton(stringResource(R.string.catalog_level_up), onClick = { levelUp = true })
                        SecondaryButton(stringResource(R.string.catalog_toast), onClick = { toast = true })
                        SecondaryButton(stringResource(R.string.catalog_add_xp), onClick = { xp = (xp + XP_STEP) % previewHunter.xpForLevel })
                    }
                }
            }

            item {
                Section(R.string.catalog_type) {
                    Text("LV 24", style = type.displayXL, color = colors.bone)
                    Text("C", style = type.display, color = rankColor)
                    Text(stringResource(R.string.system_title_daily_directive), style = type.windowTitle, color = rankColor)
                    Text("+144 XP · 1,920", style = type.numeric, color = colors.bone)
                    Text("Iron Arms", style = type.title, color = colors.bone)
                    Text("Complete 80 push-ups. Sets of any size.", style = type.body, color = colors.bone)
                    Text("80 reps · Hard", style = type.label, color = colors.mist)
                    Text("Self-report limit reached — verified quests still count fully.", style = type.caption, color = colors.mist)
                }
            }

            item {
                Section(R.string.catalog_colours) {
                    val swatches = listOf(
                        colors.void, colors.depth, colors.panelRaised, colors.edge, colors.bone, colors.mist, colors.fog,
                        colors.system, colors.aether, colors.ember, colors.crit, colors.verified,
                    ) + Rank.entries.map { colors.rank(it) } + Stat.entries.map { colors.stat(it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        swatches.forEach { Box(Modifier.size(AwakenTheme.dimens.icon).background(it)) }
                    }
                }
            }

            item {
                Section(R.string.catalog_glyphs) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Stat.entries.forEach { Icon(AwakenGlyphs.stat(it), it.name, tint = colors.stat(it), modifier = Modifier.size(AwakenTheme.dimens.sigil)) }
                        listOf(AwakenGlyphs.Shield, AwakenGlyphs.Fragment, AwakenGlyphs.Portal, AwakenGlyphs.Flame, AwakenGlyphs.Coin, AwakenGlyphs.VerifiedSeal, AwakenGlyphs.Hourglass)
                            .forEach { Icon(it, it.name, tint = colors.system, modifier = Modifier.size(AwakenTheme.dimens.sigil)) }
                    }
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
                        Pill(stringResource(R.string.chip_main), colors.system)
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
            title = stringResource(R.string.system_title_title_unlocked),
            message = "Unbroken",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Spacing.md),
        )
        if (levelUp) {
            LevelUpCeremony(
                oldLevel = 9,
                newLevel = 10,
                statGains = mapOf(Stat.STR to 94, Stat.WIL to 28),
                onDismiss = { levelUp = false },
            )
        }
    }
    reward?.let { RewardSheet(model = it, onDone = { reward = null }) }
}

@Composable
private fun Section(titleRes: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(titleRes), style = AwakenTheme.type.title, color = AwakenTheme.colors.mist)
        content()
    }
}

private val critReward = previewReward.copy(
    xp = 188,
    crit = true,
    fragments = 1,
    breakdown = previewReward.breakdown + BreakdownItem(BreakdownItem.Kind.CRIT, 2.0),
)

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
