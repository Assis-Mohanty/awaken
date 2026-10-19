package com.assismohanty.awaken.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.designsystem.components.AwakenGlyphs
import com.assismohanty.awaken.core.designsystem.components.Pill
import com.assismohanty.awaken.core.designsystem.components.PrimaryButton
import com.assismohanty.awaken.core.designsystem.components.QuietButton
import com.assismohanty.awaken.core.designsystem.components.SecondaryButton
import com.assismohanty.awaken.core.designsystem.components.SystemWindow
import com.assismohanty.awaken.core.designsystem.components.formatCount
import com.assismohanty.awaken.core.designsystem.components.shopItemNameRes
import com.assismohanty.awaken.core.designsystem.preview.PhonePreviews
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.designsystem.theme.Spacing
import com.assismohanty.awaken.core.domain.VaultItemState
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopItem
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.model.Rank

@Composable
fun VaultRoute(modifier: Modifier = Modifier, viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VaultScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Vault (DESIGN_SYSTEM §4.6): balances, Relic Cache, then the shield, themes, frames and sigils. */
@Composable
fun VaultScreen(state: VaultUiState, onEvent: (VaultEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loading) return@Column
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            BalanceWindow(state)
            CacheWindow(state, onEvent)
            ShieldWindow(state, onEvent)
            ThemesWindow(state, onEvent)
            ItemsWindow(R.string.vault_window_frames, state.frames, state, onEvent)
            ItemsWindow(R.string.vault_window_sigils, state.sigils, state, onEvent)
        }
    }
    state.pending?.let { pending ->
        ConfirmSheet(pending.state.item, gold = state.gold, busy = state.busy, onEvent = onEvent)
    }
    state.cacheDrop?.let { drop -> CacheDropDialog(drop, onDismiss = { onEvent(VaultEvent.DismissCacheDrop) }) }
}

@Composable
private fun BalanceWindow(state: VaultUiState) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.vault_window_balance), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(AwakenGlyphs.Coin, contentDescription = null, tint = colors.crit, modifier = Modifier.size(AwakenTheme.dimens.icon))
            Text(stringResource(R.string.vault_gold, formatCount(state.gold)), style = type.numeric, color = colors.bone)
        }
        Text(stringResource(R.string.vault_shields, state.shields, state.shieldMax), style = type.label, color = colors.mist)
    }
}

@Composable
private fun CacheWindow(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    SystemWindow(title = stringResource(R.string.vault_window_cache), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(AwakenGlyphs.Fragment, contentDescription = null, tint = colors.system, modifier = Modifier.size(AwakenTheme.dimens.icon))
            Text(
                stringResource(R.string.vault_fragments, state.fragments.toInt(), state.cacheFragments),
                style = type.label,
                color = colors.bone,
            )
        }
        Text(
            stringResource(if (state.cachePoolEmpty) R.string.vault_cache_empty else R.string.vault_cache_body),
            style = type.caption,
            color = colors.mist,
        )
        PrimaryButton(
            text = stringResource(R.string.action_open_cache),
            onClick = { onEvent(VaultEvent.OpenCache) },
            enabled = state.cacheReady && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShieldWindow(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.vault_window_shield), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Text(stringResource(R.string.vault_shield_body), style = AwakenTheme.type.caption, color = colors.mist)
        state.shield?.let { ItemRow(it, state, onEvent) }
        Text(stringResource(R.string.vault_reroll_body), style = AwakenTheme.type.caption, color = colors.mist)
    }
}

@Composable
private fun ThemesWindow(state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    SystemWindow(title = stringResource(R.string.vault_window_themes), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AwakenTheme.dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.shop_theme_default), style = AwakenTheme.type.label, color = colors.bone, modifier = Modifier.weight(1f))
            if (state.defaultThemeEquipped) {
                Pill(text = stringResource(R.string.action_equipped), color = colors.system)
            } else {
                SecondaryButton(
                    text = stringResource(R.string.action_equip),
                    onClick = { onEvent(VaultEvent.Equip(ShopKind.THEME, ShopRules.DEFAULT_THEME_ID)) },
                )
            }
        }
        state.themes.forEach { ItemRow(it, state, onEvent) }
    }
}

@Composable
private fun ItemsWindow(titleRes: Int, items: List<VaultItemState>, state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    SystemWindow(title = stringResource(titleRes), accent = AwakenTheme.rankAccent(state.rank), reveal = false) {
        items.forEach { ItemRow(it, state, onEvent) }
    }
}

@Composable
private fun ItemRow(item: VaultItemState, state: VaultUiState, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val type = AwakenTheme.type
    val name = shopItemNameRes(item.item.id)?.let { stringResource(it) } ?: item.item.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AwakenTheme.dimens.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ItemMark(item.item)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(name, style = type.label, color = colors.bone)
            when {
                item.equipped -> Pill(text = stringResource(R.string.action_equipped), color = colors.system)
                item.owned -> Text(stringResource(R.string.vault_owned), style = type.caption, color = colors.mist)
                else -> {
                    Text(stringResource(R.string.vault_price, formatCount(item.item.price)), style = type.numericSmall, color = colors.crit)
                    blockReason(item.check)?.let { Text(it, style = type.caption, color = colors.mist) }
                }
            }
        }
        val equippable = item.item.kind != ShopKind.SHIELD
        when {
            item.equipped -> {
                val action = stringResource(R.string.action_unequip)
                SecondaryButton(
                    text = action,
                    onClick = { onEvent(VaultEvent.Equip(item.item.kind, null)) },
                    modifier = Modifier.actionLabel(action, name),
                )
            }
            item.owned && equippable -> {
                val action = stringResource(R.string.action_equip)
                SecondaryButton(
                    text = action,
                    onClick = { onEvent(VaultEvent.Equip(item.item.kind, item.item.id)) },
                    modifier = Modifier.actionLabel(action, name),
                )
            }
            else -> {
                val action = stringResource(R.string.action_buy)
                PrimaryButton(
                    text = action,
                    onClick = { onEvent(VaultEvent.Buy(item.item.id)) },
                    enabled = item.check == BuyCheck.Buyable && !state.busy,
                    modifier = Modifier.actionLabel(action, name),
                )
            }
        }
    }
}


/** Every row has a "Buy"/"Equip" button, so TalkBack hears which item it acts on: "Buy: Iron Frame". */
@Composable
private fun Modifier.actionLabel(action: String, itemName: String): Modifier {
    val label = stringResource(R.string.cd_vault_action, action, itemName)
    return semantics { contentDescription = label }
}
@Composable
private fun blockReason(check: BuyCheck): String? = when (check) {
    BuyCheck.Buyable, BuyCheck.AlreadyOwned -> null
    is BuyCheck.InsufficientGold -> stringResource(R.string.vault_missing_gold, formatCount(check.missing))
    BuyCheck.ShieldLimit -> stringResource(R.string.vault_shield_limit)
    is BuyCheck.RankRequired -> stringResource(R.string.vault_requires_rank, check.rank.name)
}

/** The item's picture: a sigil glyph, a shield, a framed swatch or a theme colour chip. Decorative; the name is read. */
@Composable
private fun ItemMark(item: ShopItem) {
    val colors = AwakenTheme.colors
    val dimens = AwakenTheme.dimens
    val markSize = dimens.sigil
    when (item.kind) {
        ShopKind.SIGIL -> AwakenGlyphs.sigil(item.id)?.let {
            Icon(it, contentDescription = null, tint = colors.system, modifier = Modifier.size(markSize))
        }
        ShopKind.SHIELD -> Icon(AwakenGlyphs.Shield, contentDescription = null, tint = colors.system, modifier = Modifier.size(markSize))
        ShopKind.FRAME -> Box(Modifier.size(markSize).border(dimens.hairline, colors.frame(item.id), AwakenTheme.shapes.button))
        ShopKind.THEME -> Box(Modifier.size(markSize).background(colors.theme(item.id) ?: colors.edge, AwakenTheme.shapes.button))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(item: ShopItem, gold: Long, busy: Boolean, onEvent: (VaultEvent) -> Unit) {
    val colors = AwakenTheme.colors
    val name = shopItemNameRes(item.id)?.let { stringResource(it) } ?: item.id
    ModalBottomSheet(
        onDismissRequest = { onEvent(VaultEvent.DismissConfirm) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panelRaised,
        shape = AwakenTheme.shapes.window,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.vault_confirm_title), style = AwakenTheme.type.windowTitle, color = colors.system)
            Text(
                stringResource(R.string.vault_confirm_body, name, formatCount(item.price), formatCount(gold - item.price)),
                style = AwakenTheme.type.body,
                color = colors.bone,
            )
            PrimaryButton(
                text = stringResource(R.string.action_confirm_buy, formatCount(item.price)),
                onClick = { onEvent(VaultEvent.ConfirmBuy) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietButton(text = stringResource(R.string.action_cancel), onClick = { onEvent(VaultEvent.DismissConfirm) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CacheDropDialog(drop: ShopItem, onDismiss: () -> Unit) {
    val colors = AwakenTheme.colors
    val name = shopItemNameRes(drop.id)?.let { stringResource(it) } ?: drop.id
    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(title = stringResource(R.string.vault_cache_result_title), accent = colors.crit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ItemMark(drop)
                Text(stringResource(R.string.vault_cache_result_body, name), style = AwakenTheme.type.body, color = colors.bone)
            }
            PrimaryButton(text = stringResource(R.string.action_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun previewVaultState(rich: Boolean = true): VaultUiState {
    fun state(item: ShopItem, owned: Boolean = false, equipped: Boolean = false, check: BuyCheck = BuyCheck.Buyable) =
        VaultItemState(item, check, owned, equipped)
    val catalog = ShopRules.catalog
    val gold = if (rich) 1_240L else 120L
    fun check(item: ShopItem) = ShopRules.canBuy(item, gold, emptySet(), shields = 1, rank = Rank.C)
    return VaultUiState(
        loading = false,
        gold = gold,
        fragments = if (rich) 10 else 3,
        shields = 1,
        rank = Rank.C,
        cacheReady = rich,
        shield = state(catalog.first { it.kind == ShopKind.SHIELD }, check = check(catalog.first { it.kind == ShopKind.SHIELD })),
        themes = catalog.filter { it.kind == ShopKind.THEME }.map { state(it, check = check(it)) },
        frames = catalog.filter { it.kind == ShopKind.FRAME }.mapIndexed { i, it -> state(it, owned = rich && i == 0, equipped = rich && i == 0, check = check(it)) },
        sigils = catalog.filter { it.kind == ShopKind.SIGIL }.mapIndexed { i, it -> state(it, owned = rich && i == 1, check = check(it)) },
    )
}

@PhonePreviews
@Composable
private fun VaultScreenPreview() {
    AwakenTheme(reduceMotion = true) { VaultScreen(state = previewVaultState(), onEvent = {}) }
}

@PhonePreviews
@Composable
private fun VaultScreenPoorPreview() {
    AwakenTheme(reduceMotion = true) { VaultScreen(state = previewVaultState(rich = false), onEvent = {}) }
}
