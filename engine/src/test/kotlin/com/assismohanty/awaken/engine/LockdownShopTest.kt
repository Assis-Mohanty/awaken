package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LockdownShopTest {
    private val item = ShopRules.byId.getValue(ShopRules.LOCKDOWN_SHIELD_ID)

    @Test
    fun `the Lockdown shield is in the store for 1000 gold`() {
        assertThat(item.kind).isEqualTo(ShopKind.LOCKDOWN_SHIELD)
        assertThat(item.price).isEqualTo(1_000)
        assertThat(ShopRules.catalog.map { it.id }).containsExactly(ShopRules.SHIELD_ID, ShopRules.LOCKDOWN_SHIELD_ID).inOrder()
    }

    @Test
    fun `it can be bought with enough gold and says how much is missing otherwise`() {
        assertThat(ShopRules.canBuy(item, gold = 1_000, shields = 0)).isEqualTo(BuyCheck.Buyable)
        assertThat(ShopRules.canBuy(item, gold = 999, shields = 0)).isEqualTo(BuyCheck.InsufficientGold(1))
    }

    @Test
    fun `holding the most streak shields does not stop buying a Lockdown shield`() {
        val streak = ShopRules.byId.getValue(ShopRules.SHIELD_ID)
        assertThat(ShopRules.canBuy(streak, gold = 5_000, shields = StreakRules.MAX_SHIELDS)).isEqualTo(BuyCheck.ShieldLimit)
        assertThat(ShopRules.canBuy(item, gold = 5_000, shields = StreakRules.MAX_SHIELDS)).isEqualTo(BuyCheck.Buyable)
    }

    @Test
    fun `a debug build hands out five`() {
        assertThat(ShopRules.DEBUG_LOCKDOWN_SHIELDS).isEqualTo(5)
    }
}
