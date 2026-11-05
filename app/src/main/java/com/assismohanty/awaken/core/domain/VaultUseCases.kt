package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopItem
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface PurchaseResult {
    data class Purchased(val item: ShopItem, val goldAfter: Long) : PurchaseResult
    data class Rejected(val check: BuyCheck) : PurchaseResult
    /** [purchaseId] was already applied (a retry): nothing changed. */
    data object Duplicate : PurchaseResult
    data object UnknownItem : PurchaseResult
    data object NoHunter : PurchaseResult
}

/**
 * Buys a Store item (GD §9.2). One transaction: the GOLD debit and the streak freeze ITEM row commit together.
 * [purchaseId] is the client-generated idempotency key, so a retried tap never double-charges.
 */
class PurchaseItemUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(itemId: String, purchaseId: String): PurchaseResult {
        val item = ShopRules.byId[itemId] ?: return PurchaseResult.UnknownItem
        return transactions.run {
            val profile = hunterDao.getProfile() ?: return@run PurchaseResult.NoHunter
            val balances = ledger.balances()
            val check = ShopRules.canBuy(
                item = item,
                gold = balances.gold,
                shields = balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID).toInt(),
            )
            if (check != BuyCheck.Buyable) return@run PurchaseResult.Rejected(check)

            val now = clock.now()
            val today = clock.gameDate(profile.dayStartHour)
            val debit = ledger.insertIdempotent(
                listOf(row(LedgerKind.GOLD, -item.price.toLong(), SourceType.SHOP, purchaseId, today, now)),
            )
            if (debit.isEmpty()) return@run PurchaseResult.Duplicate
            ledger.insertIdempotent(
                listOf(row(LedgerKind.ITEM, 1, SourceType.SHOP, purchaseId, today, now, itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID)),
            )
            PurchaseResult.Purchased(item, goldAfter = balances.gold - item.price)
        }
    }
}

/** One Store row: the item and whether the hunter can buy it right now. */
data class VaultItemState(val item: ShopItem, val check: BuyCheck)

data class VaultModel(
    val gold: Long,
    val shields: Int,
    val items: List<VaultItemState>,
)

class ObserveVaultUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<VaultModel> = ledger.observeBalances().map { balances ->
        val shields = balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID).toInt()
        VaultModel(
            gold = balances.gold,
            shields = shields,
            items = ShopRules.catalog.map { item -> VaultItemState(item, ShopRules.canBuy(item, balances.gold, shields)) },
        )
    }
}

private fun row(
    kind: LedgerKind,
    delta: Long,
    sourceType: SourceType,
    sourceId: String,
    gameDate: java.time.LocalDate,
    createdAt: java.time.Instant,
    itemId: String = "",
) = LedgerEntryEntity(
    kind = kind, itemId = itemId, delta = delta, sourceType = sourceType, sourceId = sourceId,
    gameDate = gameDate, createdAt = createdAt,
)
