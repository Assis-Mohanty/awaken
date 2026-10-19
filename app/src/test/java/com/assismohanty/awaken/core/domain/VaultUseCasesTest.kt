package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.TrialEntity
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.ShopRules
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 10a: the Vault on a real in-memory database (GD §9.2, §9.3). */
@RunWith(AndroidJUnit4::class)
class VaultUseCasesTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private suspend fun gold(amount: Long) = graph.grant(LedgerKind.GOLD, amount, "gold-$amount")

    private suspend fun fragments(amount: Long, key: String = "frag-$amount") = graph.grant(LedgerKind.FRAGMENT, amount, key)

    private suspend fun balances() = graph.ledger.balances()

    @Test
    fun aShieldPurchaseDebitsGoldAndGrantsTheItemInOneStep() = runTest {
        graph.seedHunter()
        gold(1_000)

        val result = graph.purchaseItem("streak_shield", "buy-1")

        assertThat(result).isInstanceOf(PurchaseResult.Purchased::class.java)
        assertThat((result as PurchaseResult.Purchased).goldAfter).isEqualTo(700)
        assertThat(balances().gold).isEqualTo(700)
        assertThat(balances().item(LedgerRepository.STREAK_SHIELD_ITEM_ID)).isEqualTo(1)
        assertThat(graph.ledger.balances()).isEqualTo(graph.ledger.balancesFromLedger())
    }

    @Test
    fun aRetriedPurchaseIdNeverChargesTwice() = runTest {
        graph.seedHunter()
        gold(1_000)
        graph.purchaseItem("streak_shield", "buy-1")

        assertThat(graph.purchaseItem("streak_shield", "buy-1")).isEqualTo(PurchaseResult.Duplicate)

        assertThat(balances().gold).isEqualTo(700)
        assertThat(balances().item(LedgerRepository.STREAK_SHIELD_ITEM_ID)).isEqualTo(1)
    }

    @Test
    fun insufficientGoldChangesNothing() = runTest {
        graph.seedHunter()
        gold(299)

        val result = graph.purchaseItem("streak_shield", "buy-1")

        assertThat(result).isEqualTo(PurchaseResult.Rejected(BuyCheck.InsufficientGold(1)))
        assertThat(balances().gold).isEqualTo(299)
        assertThat(balances().item(LedgerRepository.STREAK_SHIELD_ITEM_ID)).isEqualTo(0)
    }

    @Test
    fun youCannotHoldMoreThanTwoShields() = runTest {
        graph.seedHunter()
        gold(2_000)
        graph.purchaseItem("streak_shield", "a")
        graph.purchaseItem("streak_shield", "b")

        assertThat(graph.purchaseItem("streak_shield", "c")).isEqualTo(PurchaseResult.Rejected(BuyCheck.ShieldLimit))
        assertThat(balances().gold).isEqualTo(1_400)
    }

    @Test
    fun cosmeticsAreBoughtOnceAndCanThenBeEquipped() = runTest {
        graph.seedHunter()
        gold(2_000)

        assertThat(graph.purchaseItem("frame_ember", "buy-frame")).isInstanceOf(PurchaseResult.Purchased::class.java)
        assertThat(balances().gold).isEqualTo(1_200)
        assertThat(graph.purchaseItem("frame_ember", "buy-frame-2")).isEqualTo(PurchaseResult.Rejected(BuyCheck.AlreadyOwned))
        assertThat(balances().gold).isEqualTo(1_200)

        assertThat(graph.equipCosmetic(ShopKind.FRAME, "frame_ember")).isEqualTo(EquipResult.Equipped)
        assertThat(graph.db.hunterDao().getProfile()?.equippedFrameId).isEqualTo("frame_ember")
        assertThat(graph.equipCosmetic(ShopKind.FRAME, null)).isEqualTo(EquipResult.Unequipped)
        assertThat(graph.db.hunterDao().getProfile()?.equippedFrameId).isNull()
    }

    @Test
    fun onlyOwnedItemsOfTheRightKindCanBeEquipped() = runTest {
        graph.seedHunter()
        gold(2_000)
        graph.purchaseItem("sigil_wolf", "buy-sigil")

        assertThat(graph.equipCosmetic(ShopKind.SIGIL, "sigil_eye")).isEqualTo(EquipResult.NotOwned)
        assertThat(graph.equipCosmetic(ShopKind.FRAME, "sigil_wolf")).isEqualTo(EquipResult.NotOwned)
        assertThat(graph.equipCosmetic(ShopKind.THEME, "theme_crimson_gate")).isEqualTo(EquipResult.NotOwned)
        assertThat(graph.equipCosmetic(ShopKind.SIGIL, "sigil_wolf")).isEqualTo(EquipResult.Equipped)
        assertThat(graph.db.hunterDao().getProfile()?.equippedSigilId).isEqualTo("sigil_wolf")
    }

    @Test
    fun themesEquipAndResetToTheDefault() = runTest {
        graph.seedHunter()
        gold(2_000)
        graph.purchaseItem("theme_crimson_gate", "buy-theme")

        assertThat(graph.equipCosmetic(ShopKind.THEME, "theme_crimson_gate")).isEqualTo(EquipResult.Equipped)
        assertThat(graph.db.hunterDao().getProfile()?.themeId).isEqualTo("theme_crimson_gate")
        assertThat(graph.equipCosmetic(ShopKind.THEME, ShopRules.DEFAULT_THEME_ID)).isEqualTo(EquipResult.Unequipped)
        assertThat(graph.db.hunterDao().getProfile()?.themeId).isEqualTo(ShopRules.DEFAULT_THEME_ID)
    }

    @Test
    fun sovereignGoldNeedsRankS() = runTest {
        graph.seedHunter()
        gold(5_000)

        assertThat(graph.purchaseItem("theme_sovereign_gold", "try-1")).isEqualTo(PurchaseResult.Rejected(BuyCheck.RankRequired(Rank.S)))

        Rank.entries.drop(1).forEach { rank ->
            graph.db.trialDao().upsert(
                TrialEntity("t-$rank", rank, graph.today(), graph.today().plusDays(5), RankRules.TrialStatus.PASSED, graph.clock.now()),
            )
        }
        assertThat(graph.purchaseItem("theme_sovereign_gold", "try-2")).isInstanceOf(PurchaseResult.Purchased::class.java)
        assertThat(balances().gold).isEqualTo(2_000)
    }

    @Test
    fun aCacheSpendsTenFragmentsForAnUnownedCosmetic() = runTest {
        graph.seedHunter()
        fragments(12)

        val result = graph.openRelicCache("open-1")

        val drop = (result as CacheResult.Opened).item
        assertThat(drop.inCache).isTrue()
        assertThat(balances().fragments).isEqualTo(2)
        assertThat(graph.db.ownedCosmeticDao().ownedIds()).containsExactly(drop.id)
        assertThat(graph.db.ledgerDao().cachesOpened()).isEqualTo(1)
        // The same open id is a retry: nothing is spent or granted twice.
        assertThat(graph.openRelicCache("open-1")).isEqualTo(CacheResult.NotEnoughFragments(2))
        assertThat(balances().fragments).isEqualTo(2)
    }

    @Test
    fun theDropMatchesThePureRuleForThatOpenId() = runTest {
        graph.seedHunter()
        fragments(10)
        val expected = ShopRules.openCache(ShopRules.cachePool(emptySet()), "open-seed")

        assertThat((graph.openRelicCache("open-seed") as CacheResult.Opened).item).isEqualTo(expected)
    }

    @Test
    fun aCacheNeedsTenFragments() = runTest {
        graph.seedHunter()
        fragments(9)

        assertThat(graph.openRelicCache("open-1")).isEqualTo(CacheResult.NotEnoughFragments(9))
        assertThat(balances().fragments).isEqualTo(9)
        assertThat(graph.db.ownedCosmeticDao().ownedIds()).isEmpty()
    }

    @Test
    fun anEmptyPoolKeepsTheFragments() = runTest {
        graph.seedHunter()
        gold(100_000)
        ShopRules.catalog.filter { it.inCache }.forEachIndexed { i, item -> graph.purchaseItem(item.id, "buy-$i") }
        fragments(10)

        assertThat(graph.openRelicCache("open-1")).isEqualTo(CacheResult.PoolEmpty)
        assertThat(balances().fragments).isEqualTo(10)
    }

    @Test
    fun theVaultModelReflectsBalancesOwnershipAndEquipment() = runTest {
        graph.seedHunter()
        gold(500)
        fragments(10)
        graph.purchaseItem("sigil_star", "buy-sigil")
        graph.equipCosmetic(ShopKind.SIGIL, "sigil_star")

        val model = graph.observeVault().filterNotNull().first()

        assertThat(model.gold).isEqualTo(100)
        assertThat(model.cacheReady).isTrue()
        assertThat(model.items.first { it.item.id == "sigil_star" }.let { it.owned to it.equipped }).isEqualTo(true to true)
        assertThat(model.items.first { it.item.id == "streak_shield" }.check).isEqualTo(BuyCheck.InsufficientGold(200))
        assertThat(model.items.first { it.item.id == "theme_sovereign_gold" }.check).isEqualTo(BuyCheck.RankRequired(Rank.S))
    }
}
