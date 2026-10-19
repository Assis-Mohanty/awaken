package com.assismohanty.awaken.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.CacheResult
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.EquipCosmeticUseCase
import com.assismohanty.awaken.core.domain.EquipResult
import com.assismohanty.awaken.core.domain.ObserveVaultUseCase
import com.assismohanty.awaken.core.domain.OpenRelicCacheUseCase
import com.assismohanty.awaken.core.domain.PurchaseItemUseCase
import com.assismohanty.awaken.core.domain.PurchaseResult
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.domain.VaultItemState
import com.assismohanty.awaken.core.domain.VaultModel
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopItem
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.StreakRules
import com.assismohanty.awaken.engine.model.Rank
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** A purchase waiting for confirmation. [purchaseId] is the idempotency key, fixed while the sheet is open. */
data class PendingPurchase(val state: VaultItemState, val purchaseId: String)

data class VaultUiState(
    val loading: Boolean = true,
    val gold: Long = 0,
    val fragments: Long = 0,
    val shields: Int = 0,
    val shieldMax: Int = StreakRules.MAX_SHIELDS,
    val cacheFragments: Int = ShopRules.CACHE_FRAGMENTS,
    val rank: Rank = Rank.E,
    val cacheReady: Boolean = false,
    val cachePoolEmpty: Boolean = false,
    val shield: VaultItemState? = null,
    val themes: List<VaultItemState> = emptyList(),
    val frames: List<VaultItemState> = emptyList(),
    val sigils: List<VaultItemState> = emptyList(),
    val defaultThemeEquipped: Boolean = true,
    val pending: PendingPurchase? = null,
    /** Set while a purchase or cache open runs, so a double tap can't start a second one. */
    val busy: Boolean = false,
    val cacheDrop: ShopItem? = null,
)

sealed interface VaultEvent {
    data class Buy(val itemId: String) : VaultEvent
    data object ConfirmBuy : VaultEvent
    data object DismissConfirm : VaultEvent
    data class Equip(val kind: ShopKind, val itemId: String?) : VaultEvent
    data object OpenCache : VaultEvent
    data object DismissCacheDrop : VaultEvent
}

private data class LocalState(val pending: PendingPurchase? = null, val busy: Boolean = false, val cacheDrop: ShopItem? = null)

@HiltViewModel
class VaultViewModel @Inject constructor(
    observeVault: ObserveVaultUseCase,
    private val purchaseItem: PurchaseItemUseCase,
    private val openRelicCache: OpenRelicCacheUseCase,
    private val equipCosmetic: EquipCosmeticUseCase,
    private val completionCenter: CompletionCenter,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    private val model: StateFlow<VaultModel?> = observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<VaultUiState> = combine(model, local) { vault, local ->
        vault?.toUiState(local) ?: VaultUiState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultUiState())

    fun onEvent(event: VaultEvent) {
        when (event) {
            is VaultEvent.Buy -> {
                val item = model.value?.items?.firstOrNull { it.item.id == event.itemId } ?: return
                if (item.check == BuyCheck.Buyable) {
                    local.update { it.copy(pending = PendingPurchase(item, UUID.randomUUID().toString())) }
                }
            }
            VaultEvent.DismissConfirm -> local.update { it.copy(pending = null) }
            VaultEvent.ConfirmBuy -> confirm()
            is VaultEvent.Equip -> launchAction {
                when (equipCosmetic(event.kind, event.itemId)) {
                    EquipResult.Equipped -> event.itemId?.let { completionCenter.message(UserMessage.VaultEquipped(it)) }
                    EquipResult.Unequipped -> Unit
                    EquipResult.NotOwned -> completionCenter.message(UserMessage.Error)
                }
            }
            VaultEvent.OpenCache -> openCache()
            VaultEvent.DismissCacheDrop -> local.update { it.copy(cacheDrop = null) }
        }
    }

    private fun confirm() {
        val pending = local.value.pending ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (val result = purchaseItem(pending.state.item.id, pending.purchaseId)) {
                    is PurchaseResult.Purchased -> completionCenter.message(UserMessage.VaultBought(result.item.id))
                    PurchaseResult.Duplicate -> completionCenter.message(UserMessage.VaultDuplicate)
                    // The button can be enabled from a stale state (gold spent elsewhere); say exactly why it failed.
                    is PurchaseResult.Rejected -> completionCenter.message(UserMessage.VaultRejected(result.check))
                    PurchaseResult.UnknownItem, PurchaseResult.NoHunter ->
                        completionCenter.message(UserMessage.Error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Purchase failed")
                completionCenter.message(UserMessage.Error)
            } finally {
                local.update { it.copy(pending = null, busy = false) }
            }
        }
    }

    private fun openCache() {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (val result = openRelicCache(UUID.randomUUID().toString())) {
                    is CacheResult.Opened -> local.update { it.copy(cacheDrop = result.item) }
                    is CacheResult.NotEnoughFragments -> completionCenter.message(UserMessage.VaultNoFragments)
                    CacheResult.PoolEmpty -> completionCenter.message(UserMessage.VaultPoolEmpty)
                    CacheResult.Duplicate, CacheResult.NoHunter -> completionCenter.message(UserMessage.Error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Opening the cache failed")
                completionCenter.message(UserMessage.Error)
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Vault action failed")
                completionCenter.message(UserMessage.Error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun VaultModel.toUiState(local: LocalState): VaultUiState = VaultUiState(
    loading = false,
    gold = gold,
    fragments = fragments,
    shields = shields,
    rank = rank,
    cacheReady = cacheReady,
    cachePoolEmpty = cachePoolEmpty,
    shield = items.firstOrNull { it.item.kind == ShopKind.SHIELD },
    themes = items.filter { it.item.kind == ShopKind.THEME },
    frames = items.filter { it.item.kind == ShopKind.FRAME },
    sigils = items.filter { it.item.kind == ShopKind.SIGIL },
    defaultThemeEquipped = themeId == ShopRules.DEFAULT_THEME_ID,
    pending = local.pending,
    busy = local.busy,
    cacheDrop = local.cacheDrop,
)
