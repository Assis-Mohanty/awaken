package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.OwnedCosmeticDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.OwnedCosmeticEntity
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.ShopItem
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

sealed interface PurchaseResult {
    data class Purchased(val item: ShopItem, val goldAfter: Long) : PurchaseResult
    data class Rejected(val check: BuyCheck) : PurchaseResult
    /** [purchaseId] was already applied (a retry): nothing changed. */
    data object Duplicate : PurchaseResult
    data object UnknownItem : PurchaseResult
    data object NoHunter : PurchaseResult
}

/**
 * Buys a Vault item (GD §9.2). One transaction: the GOLD debit and the grant (a shield ITEM row or an owned cosmetic)
 * commit together. [purchaseId] is the client-generated idempotency key, so a retried tap never double-charges.
 */
class PurchaseItemUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ownedCosmeticDao: OwnedCosmeticDao,
    private val trialDao: TrialDao,
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
                owned = ownedCosmeticDao.ownedIds().toSet(),
                shields = balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID).toInt(),
                rank = RankRules.currentRank(trialDao.passedRanks().toSet()),
            )
            if (check != BuyCheck.Buyable) return@run PurchaseResult.Rejected(check)

            val now = clock.now()
            val today = clock.gameDate(profile.dayStartHour)
            val debit = ledger.insertIdempotent(
                listOf(row(LedgerKind.GOLD, -item.price.toLong(), SourceType.SHOP, purchaseId, today, now)),
            )
            if (debit.isEmpty()) return@run PurchaseResult.Duplicate
            if (item.kind == ShopKind.SHIELD) {
                ledger.insertIdempotent(
                    listOf(row(LedgerKind.ITEM, 1, SourceType.SHOP, purchaseId, today, now, itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID)),
                )
            } else {
                ownedCosmeticDao.insert(OwnedCosmeticEntity(item.id, now, SOURCE_SHOP))
            }
            PurchaseResult.Purchased(item, goldAfter = balances.gold - item.price)
        }
    }
}

sealed interface CacheResult {
    data class Opened(val item: ShopItem) : CacheResult
    data class NotEnoughFragments(val have: Long) : CacheResult
    /** Every sigil and frame is owned: fragments are kept. */
    data object PoolEmpty : CacheResult
    data object Duplicate : CacheResult
    data object NoHunter : CacheResult
}

/**
 * Opens a Relic Cache (GD §9.2): 10 fragments for one random sigil or frame not yet owned. The drop is a pure function
 * of [openId] and the unowned pool ([ShopRules.openCache]); the fragment debit and the cosmetic commit together.
 */
class OpenRelicCacheUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ownedCosmeticDao: OwnedCosmeticDao,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(openId: String): CacheResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run CacheResult.NoHunter
        val fragments = ledger.balances().fragments
        if (fragments < ShopRules.CACHE_FRAGMENTS) return@run CacheResult.NotEnoughFragments(fragments)
        val drop = ShopRules.openCache(ShopRules.cachePool(ownedCosmeticDao.ownedIds().toSet()), openId)
            ?: return@run CacheResult.PoolEmpty

        val now = clock.now()
        val spent = ledger.insertIdempotent(
            listOf(
                row(
                    LedgerKind.FRAGMENT, -ShopRules.CACHE_FRAGMENTS.toLong(), SourceType.CACHE_OPEN, openId,
                    clock.gameDate(profile.dayStartHour), now,
                ),
            ),
        )
        if (spent.isEmpty()) return@run CacheResult.Duplicate
        ownedCosmeticDao.insert(OwnedCosmeticEntity(drop.id, now, SOURCE_CACHE))
        CacheResult.Opened(drop)
    }
}

sealed interface EquipResult {
    data object Equipped : EquipResult
    data object Unequipped : EquipResult
    data object NotOwned : EquipResult
}

/** Equips an owned theme, frame or sigil. Null unequips a frame or sigil, or returns the theme to the default. */
class EquipCosmeticUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ownedCosmeticDao: OwnedCosmeticDao,
) {
    suspend operator fun invoke(kind: ShopKind, id: String?): EquipResult {
        if (id == null || (kind == ShopKind.THEME && id == ShopRules.DEFAULT_THEME_ID)) {
            when (kind) {
                ShopKind.THEME -> hunterDao.setTheme(ShopRules.DEFAULT_THEME_ID)
                ShopKind.FRAME -> hunterDao.setEquippedFrame(null)
                ShopKind.SIGIL -> hunterDao.setEquippedSigil(null)
                ShopKind.SHIELD -> return EquipResult.NotOwned
            }
            return EquipResult.Unequipped
        }
        val item = ShopRules.byId[id]
        if (item == null || item.kind != kind || id !in ownedCosmeticDao.ownedIds()) return EquipResult.NotOwned
        when (kind) {
            ShopKind.THEME -> hunterDao.setTheme(id)
            ShopKind.FRAME -> hunterDao.setEquippedFrame(id)
            ShopKind.SIGIL -> hunterDao.setEquippedSigil(id)
            ShopKind.SHIELD -> return EquipResult.NotOwned
        }
        return EquipResult.Equipped
    }
}

/** One Vault row: the item, whether the hunter can buy it right now, and whether it is owned or equipped. */
data class VaultItemState(val item: ShopItem, val check: BuyCheck, val owned: Boolean, val equipped: Boolean)

data class VaultModel(
    val gold: Long,
    val fragments: Long,
    val shields: Int,
    val rank: Rank,
    val themeId: String,
    val items: List<VaultItemState>,
    /** The cache can open: enough fragments and something left to drop. */
    val cacheReady: Boolean,
    val cachePoolEmpty: Boolean,
)

class ObserveVaultUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val ownedCosmeticDao: OwnedCosmeticDao,
    private val trialDao: TrialDao,
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<VaultModel?> = hunterDao.observeProfile().flatMapLatest { profile ->
        if (profile == null) {
            flowOf(null)
        } else {
            combine(ledger.observeBalances(), ownedCosmeticDao.observeOwnedIds(), trialDao.observePassedRanks()) { balances, owned, passed ->
                model(profile, balances.gold, balances.fragments, balances.item(LedgerRepository.STREAK_SHIELD_ITEM_ID).toInt(), owned.toSet(), RankRules.currentRank(passed.toSet()))
            }
        }
    }

    private fun model(profile: HunterProfileEntity, gold: Long, fragments: Long, shields: Int, owned: Set<String>, rank: Rank): VaultModel {
        val items = ShopRules.catalog.map { item ->
            val equipped = when (item.kind) {
                ShopKind.THEME -> profile.themeId == item.id
                ShopKind.FRAME -> profile.equippedFrameId == item.id
                ShopKind.SIGIL -> profile.equippedSigilId == item.id
                ShopKind.SHIELD -> false
            }
            VaultItemState(item, ShopRules.canBuy(item, gold, owned, shields, rank), owned = item.id in owned, equipped = equipped)
        }
        val poolEmpty = ShopRules.cachePool(owned).isEmpty()
        return VaultModel(
            gold = gold,
            fragments = fragments,
            shields = shields,
            rank = rank,
            themeId = profile.themeId,
            items = items,
            cacheReady = fragments >= ShopRules.CACHE_FRAGMENTS && !poolEmpty,
            cachePoolEmpty = poolEmpty,
        )
    }
}

private const val SOURCE_SHOP = "SHOP"
private const val SOURCE_CACHE = "CACHE"

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
