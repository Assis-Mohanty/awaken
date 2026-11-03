package com.assismohanty.awaken.feature.vault

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.core.designsystem.components.MetricCard
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.shopItemNameRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.VaultItemState
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopItem

@Composable
fun VaultRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VaultScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Store (DESIGN_SYSTEM §4.6): the gold balance, then the streak freeze and a note about rerolls. */
@Composable
fun VaultScreen(state: VaultUiState, onEvent: (VaultEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LargeTitleScaffold(title = lexString(R.string.nav_vault), onBack = onBack, modifier = modifier) {
        if (state.loading) {
            SkeletonCard()
            return@LargeTitleScaffold
        }
        BalanceWindow(state)
        ShieldWindow(state, onEvent)
    }
    state.pending?.let { pending ->
        ConfirmSheet(pending.state.item, gold = state.gold, busy = state.busy, onEvent = onEvent)
    }
}

@Composable
private fun BalanceWindow(state: VaultUiState) {
    // The balance is the one big number in the Store.
    val shields = lexString(R.string.vault_shields, state.shields, state.shieldMax)
    MetricCard(
        label = lexString(R.string.vault_window_balance),
        value = formatCount(state.gold),
        unit = lexString(R.string.unit_gold),
        secondary = shields,
        icon = AwakenGlyphs.Coin,
        iconTint = AwakenTheme.colors.reward,
        large = true,
        description = lexString(R.string.vault_gold, formatCount(state.gold)) + ". " + shields,
    )
}

@Composable
private fun ShieldWindow(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = lexString(R.string.vault_window_shield), accent = AwakenTheme.rankAccent(Rank.E), reveal = false) {
        Text(lexString(R.string.vault_shield_body), style = AwakenTheme.type.caption, color = colors.textSecondary)
        state.shield?.let { ItemRow(it, state, onEvent) }
        Text(lexString(R.string.vault_reroll_body), style = AwakenTheme.type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ItemRow(item: VaultItemState, state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val name = shopItemNameRes(item.item.id)?.let { lexString(it) } ?: item.item.id
    val action = lexString(R.string.action_buy)
    val label = lexString(R.string.cd_vault_action, action, name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(AwakenGlyphs.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(AwakenTheme.dimens.sigil))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(name, style = type.label, color = colors.textPrimary)
            Text(lexString(R.string.vault_price, formatCount(item.item.price)), style = type.numericSmall, color = colors.reward)
            blockReason(item.check)?.let { Text(it, style = type.caption, color = colors.textSecondary) }
        }
        PrimaryButton(
            text = action,
            onClick = { onEvent(VaultEvent.Buy(item.item.id)) },
            enabled = item.check == BuyCheck.Buyable && !state.busy,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun blockReason(check: BuyCheck): String? = when (check) {
    BuyCheck.Buyable -> null
    is BuyCheck.InsufficientGold -> lexString(R.string.vault_missing_gold, formatCount(check.missing))
    BuyCheck.ShieldLimit -> lexString(R.string.vault_shield_limit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(item: ShopItem, gold: Long, busy: Boolean, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val name = shopItemNameRes(item.id)?.let { lexString(it) } ?: item.id
    ModalBottomSheet(
        onDismissRequest = { onEvent(VaultEvent.DismissConfirm) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceOverlay,
        shape = AwakenTheme.shapes.window,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(lexString(R.string.vault_confirm_title), style = AwakenTheme.type.windowTitle, color = colors.accent)
            Text(
                lexString(R.string.vault_confirm_body, name, formatCount(item.price), formatCount(gold - item.price)),
                style = AwakenTheme.type.body,
                color = colors.textPrimary,
            )
            PrimaryButton(
                text = lexString(R.string.action_confirm_buy, formatCount(item.price)),
                onClick = { onEvent(VaultEvent.ConfirmBuy) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(VaultEvent.DismissConfirm) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun previewVaultState(rich: Boolean = true): VaultUiState {
    val gold = if (rich) 1_240L else 120L
    val item = ShopRules.byId.getValue(ShopRules.SHIELD_ID)
    return VaultUiState(
        loading = false,
        gold = gold,
        shields = 1,
        shield = VaultItemState(item, ShopRules.canBuy(item, gold, shields = 1)),
    )
}

@PhonePreviews
@Composable
private fun VaultScreenPreview() {
    AwakenTheme(reduceMotion = true) { VaultScreen(state = previewVaultState(), onEvent = {}, onBack = {}) }
}

@PhonePreviews
@Composable
private fun VaultScreenPoorPreview() {
    AwakenTheme(reduceMotion = true) { VaultScreen(state = previewVaultState(rich = false), onEvent = {}, onBack = {}) }
}
