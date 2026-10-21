package com.assismohanty.awaken.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.HunterState
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.Instant
import java.time.LocalDate

@Composable
fun DebugRoute(onBack: () -> Unit, onOpenCatalog: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DebugScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onOpenCatalog = onOpenCatalog)
}

/** Developer tool (debug builds only). Deliberately plain: the real UI arrives with the design system in Phase 5. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(
    state: DebugUiState,
    onEvent: (DebugEvent) -> Unit,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.debug_back)) }
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineMedium)
        }

        item { SectionTitle(R.string.debug_section_hunter) }
        item {
            val hunter = state.hunter
            if (hunter == null) {
                Text(stringResource(R.string.debug_no_hunter))
            } else {
                Mono(
                    "${hunter.name}  LV ${hunter.level} (${hunter.xpIntoLevel}/${hunter.xpForLevel})  " +
                        "rank ${hunter.rank} band ${hunter.band}\n" +
                        "XP ${hunter.totalXp}  gold ${hunter.gold}  fragments ${hunter.fragments}  shields ${hunter.shields}\n" +
                        "streak ${hunter.streak} (best ${hunter.bestStreak})  today cleared ${hunter.todayCleared}\n" +
                        hunter.statLevels.entries.joinToString("  ") { (stat, level) -> "$stat $level" },
                )
            }
        }

        item { SectionTitle(R.string.debug_section_clock) }
        item { Mono("now ${state.now}\ngame date ${state.gameDate}  offset +${state.offsetDays} d") }

        item { SectionTitle(R.string.debug_section_actions) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Button(onClick = onOpenCatalog) { Text(stringResource(R.string.debug_open_catalog)) }
                Button(onClick = { onEvent(DebugEvent.SeedHunter) }) { Text(stringResource(R.string.debug_seed_hunter)) }
                Button(onClick = { onEvent(DebugEvent.AddDay) }) { Text(stringResource(R.string.debug_add_day)) }
                Button(onClick = { onEvent(DebugEvent.ForceRollover) }) { Text(stringResource(R.string.debug_rollover)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.ResetDays) }) { Text(stringResource(R.string.debug_reset_days)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.RebuildBalances) }) { Text(stringResource(R.string.debug_rebuild)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.GrantShield) }) { Text(stringResource(R.string.debug_grant_shield)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.GrantGold) }) { Text(stringResource(R.string.debug_grant_gold)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.GrantFragments) }) { Text(stringResource(R.string.debug_grant_fragments)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.SimulateSteps(!state.simulateSteps)) }) {
                    Text(stringResource(if (state.simulateSteps) R.string.debug_simulate_steps_off else R.string.debug_simulate_steps_on))
                }
                OutlinedButton(onClick = { onEvent(DebugEvent.SyncHealth) }) { Text(stringResource(R.string.debug_health_sync)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.GrantLevel) }) { Text(stringResource(R.string.debug_grant_level)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.SetUseFakeOracle(!state.useFakeOracle)) }) {
                    Text(stringResource(if (state.useFakeOracle) R.string.debug_oracle_use_real else R.string.debug_oracle_use_fake))
                }
                OutlinedButton(onClick = { onEvent(DebugEvent.CycleOracleFailure) }) {
                    Text(stringResource(R.string.debug_oracle_failure, state.oracleFailure.name))
                }
                OutlinedButton(onClick = { onEvent(DebugEvent.SetAiConsent(!state.aiConsent)) }) {
                    Text(stringResource(if (state.aiConsent) R.string.debug_ai_revoke else R.string.debug_ai_grant))
                }
                OutlinedButton(onClick = { onEvent(DebugEvent.SeedPath) }) { Text(stringResource(R.string.debug_seed_path)) }
                OutlinedButton(onClick = { onEvent(DebugEvent.WipeData) }) { Text(stringResource(R.string.debug_wipe)) }
            }
        }
        state.lastResult?.let { result -> item { Mono(result) } }

        item { SectionTitle(R.string.debug_section_quests) }
        items(state.quests, key = { it.id }) { quest -> QuestRow(quest, onEvent) }

        item { SectionTitle(R.string.debug_section_balances) }
        item { Mono(state.balances.entries.joinToString("\n") { (key, value) -> "$key = $value" }) }

        item { SectionTitle(R.string.debug_section_ledger) }
        items(state.ledger, key = { it.id }) { row -> Mono(row.describe()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestRow(quest: QuestInstanceEntity, onEvent: (DebugEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(quest.title, style = MaterialTheme.typography.titleSmall)
        Mono(
            "${quest.type} ${quest.stat} ${quest.difficulty} ${quest.verification} — ${quest.status}\n" +
                "${quest.target} ${quest.unit}  template ${quest.templateId}",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(onClick = { onEvent(DebugEvent.Begin(quest.id)) }) { Text(stringResource(R.string.debug_begin)) }
            TextButton(onClick = { onEvent(DebugEvent.Complete(quest.id)) }) { Text(stringResource(R.string.debug_complete)) }
            TextButton(onClick = { onEvent(DebugEvent.Undo(quest.id)) }) { Text(stringResource(R.string.debug_undo)) }
            TextButton(onClick = { onEvent(DebugEvent.Reroll(quest.id)) }) { Text(stringResource(R.string.debug_reroll)) }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

@Composable
private fun Mono(text: String) {
    Text(text = text, style = AwakenTheme.type.caption, color = AwakenTheme.colors.bone)
}

private fun LedgerEntryEntity.describe(): String =
    "#$id ${gameDate} $kind ${stat.ifEmpty { itemId }} ${if (delta >= 0) "+" else ""}$delta  $sourceType:$sourceId $xpSource"

@PhonePreviews
@Composable
private fun DebugScreenPreview() {
    val today = LocalDate.of(2026, 10, 18)
    val now = Instant.parse("2026-10-18T09:00:00Z")
    AwakenTheme {
        DebugScreen(
            state = DebugUiState(
                hunter = HunterState(
                    name = "Test Hunter", gameDate = today, totalXp = 150, level = 2, xpIntoLevel = 38, xpForLevel = 136,
                    rank = Rank.E, band = Rank.E, gold = 75, fragments = 1, shields = 0,
                    statXp = mapOf(Stat.STR to 150), statLevels = Stat.entries.associateWith { 1 },
                    streak = 1, bestStreak = 1, todayCleared = false,
                ),
                now = now,
                gameDate = today,
                quests = listOf(
                    QuestInstanceEntity(
                        id = "q1", gameDate = today, type = QuestType.MAIN, templateId = "str_pushups", habitId = null,
                        stat = Stat.STR, secondaryStat = Stat.WIL, title = "Iron Arms", description = "Complete 50 push-ups.",
                        unit = "reps", target = 50, difficulty = Difficulty.HARD, verification = Verification.SELF,
                        minDurationSec = 60, status = QuestStatus.ACTIVE, createdAt = now,
                    ),
                ),
                balances = mapOf("gold" to 75L, "xp_total" to 150L),
                ledger = listOf(
                    LedgerEntryEntity(
                        id = 1, kind = LedgerKind.XP_TOTAL, delta = 150, sourceType = SourceType.QUEST,
                        sourceId = "q1#1", gameDate = today, xpSource = "SELF", createdAt = now,
                    ),
                ),
            ),
            onEvent = {},
            onBack = {},
            onOpenCatalog = {},
        )
    }
}
