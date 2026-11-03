package com.assismohanty.awaken.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.ObserveVaultUseCase
import com.assismohanty.awaken.core.domain.PurchaseItemUseCase
import com.assismohanty.awaken.core.domain.PurchaseResult
import com.assismohanty.awaken.core.domain.UserMessage
import com.assismohanty.awaken.core.domain.VaultItemState
import com.assismohanty.awaken.core.domain.VaultModel
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.StreakRules
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
    val shields: Int = 0,
    val shieldMax: Int = StreakRules.MAX_SHIELDS,
    val shield: VaultItemState? = null,
    val pending: PendingPurchase? = null,
    /** Set while a purchase runs, so a double tap can't start a second one. */
    val busy: Boolean = false,
)

sealed interface VaultEvent {
    data class Buy(val itemId: String) : VaultEvent
    data object ConfirmBuy : VaultEvent
    data object DismissConfirm : VaultEvent
}

private data class LocalState(val pending: PendingPurchase? = null, val busy: Boolean = false)

@HiltViewModel
class VaultViewModel @Inject constructor(
    observeVault: ObserveVaultUseCase,
    private val purchaseItem: PurchaseItemUseCase,
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun VaultModel.toUiState(local: LocalState): VaultUiState = VaultUiState(
    loading = false,
    gold = gold,
    shields = shields,
    shield = items.firstOrNull { it.item.kind == ShopKind.SHIELD },
    pending = local.pending,
    busy = local.busy,
)
