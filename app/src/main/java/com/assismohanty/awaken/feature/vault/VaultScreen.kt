package com.assismohanty.awaken.feature.vault

import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.engine.ShopRules
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import com.assismohanty.awaken.core.designsystem.components.AwakenBottomSheet
import com.assismohanty.awaken.core.designsystem.components.AwakenCard
import com.assismohanty.awaken.core.designsystem.components.BigValue
import com.assismohanty.awaken.core.designsystem.components.LargeTitleScaffold
import com.assismohanty.awaken.core.designsystem.components.ValueRow
import com.assismohanty.awaken.core.designsystem.components.SkeletonCard
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
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
        BalanceHero(state)
        ItemCard(state.shield, R.string.vault_item_blurb, state.busy, onEvent)
        ItemCard(state.lockdownShield, R.string.vault_lockdown_blurb, state.busy, onEvent)
        Text(lexString(R.string.vault_reroll_body), style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
    state.pending?.let { pending ->
        ConfirmSheet(pending.state.item, gold = state.gold, busy = state.busy, onEvent = onEvent)
    }
}

/** The balance is the one big number in the Store, straight on the page. */
@Composable
private fun BalanceHero(state: VaultUiState) {
    val shields = lexString(R.string.vault_shields, state.shields, state.shieldMax)
    val lockdown = lexString(R.string.vault_lockdown_held, state.lockdownShields)
    val description = lexString(R.string.vault_gold, formatCount(state.gold)) + ". " + shields + ". " + lockdown
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BigValue(formatCount(state.gold), lexString(R.string.unit_gold), large = true)
        Text(shields, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
        Text(lockdown, style = AwakenTheme.type.caption, color = AwakenTheme.colors.textSecondary)
    }
}

/** The streak freeze: its name and what it does, with the price on the Buy button. */
@Composable
private fun ItemCard(item: VaultItemState?, blurb: Int, busy: Boolean, onEvent: (VaultEvent) -> Unit) {
    if (item == null) return
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val name = shopItemNameRes(item.item.id)?.let { lexString(it) } ?: item.item.id
    val action = lexString(R.string.action_buy)
    val label = lexString(R.string.cd_vault_action, action, name)
    AwakenCard(reveal = false, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AwakenTheme.dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(name, style = type.label, color = colors.textPrimary)
                Text(lexString(blurb), style = type.caption, color = colors.textSecondary)
                blockReason(item.check)?.let { Text(it, style = type.caption, color = colors.textSecondary) }
            }
            PrimaryButton(
                text = lexString(R.string.vault_buy_price, formatCount(item.item.price)),
                onClick = { onEvent(VaultEvent.Buy(item.item.id)) },
                enabled = item.check == BuyCheck.Buyable && !busy,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun blockReason(check: BuyCheck): String? = when (check) {
    BuyCheck.Buyable -> null
    is BuyCheck.InsufficientGold -> lexString(R.string.vault_missing_gold, formatCount(check.missing))
    BuyCheck.ShieldLimit -> lexString(R.string.vault_shield_limit)
}

@Composable
private fun ConfirmSheet(item: ShopItem, gold: Long, busy: Boolean, onEvent: (VaultEvent) -> Unit) {
    AwakenBottomSheet(onDismiss = { onEvent(VaultEvent.DismissConfirm) }, title = lexString(R.string.vault_confirm_title)) {
        ValueRow(lexString(R.string.vault_confirm_price), lexString(R.string.vault_gold, formatCount(item.price)))
        ValueRow(lexString(R.string.vault_confirm_after), lexString(R.string.vault_gold, formatCount(gold - item.price)))
        PrimaryButton(
            text = lexString(R.string.action_buy),
            onClick = { onEvent(VaultEvent.ConfirmBuy) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(text = lexString(R.string.action_cancel), onClick = { onEvent(VaultEvent.DismissConfirm) }, modifier = Modifier.fillMaxWidth())
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
        lockdownShield = ShopRules.byId.getValue(ShopRules.LOCKDOWN_SHIELD_ID).let { VaultItemState(it, ShopRules.canBuy(it, gold, shields = 1)) },
        lockdownShields = 2,
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
