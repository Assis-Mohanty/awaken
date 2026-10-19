package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Rank
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ShopRulesTest {

    private fun item(id: String) = ShopRules.byId.getValue(id)

    private fun check(id: String, gold: Long = 10_000, owned: Set<String> = emptySet(), shields: Int = 0, rank: Rank = Rank.E) =
        ShopRules.canBuy(item(id), gold, owned, shields, rank)

    @Test
    fun `catalogue matches GD 9_2`() {
        assertThat(item("streak_shield").price).isEqualTo(300)
        assertThat(item("theme_crimson_gate").price).isEqualTo(1_500)
        assertThat(item("theme_abyssal_violet").price).isEqualTo(1_500)
        assertThat(item("theme_sovereign_gold").price).isEqualTo(3_000)
        assertThat(item("theme_sovereign_gold").requiresRank).isEqualTo(Rank.S)
        assertThat(ShopRules.catalog.filter { it.kind == ShopKind.FRAME }.map { it.price }).containsExactly(600, 800, 1_000, 1_200).inOrder()
        val sigils = ShopRules.catalog.filter { it.kind == ShopKind.SIGIL }
        assertThat(sigils).hasSize(8)
        assertThat(sigils.map { it.price }.toSet()).containsExactly(400)
        assertThat(ShopRules.catalog.map { it.id }.toSet()).hasSize(ShopRules.catalog.size)
    }

    @Test
    fun `gold is checked last and reports what is missing`() {
        assertThat(check("theme_crimson_gate", gold = 1_500)).isEqualTo(BuyCheck.Buyable)
        assertThat(check("theme_crimson_gate", gold = 1_499)).isEqualTo(BuyCheck.InsufficientGold(1))
    }

    @Test
    fun `cosmetics are bought once`() {
        assertThat(check("sigil_wolf", owned = setOf("sigil_wolf"))).isEqualTo(BuyCheck.AlreadyOwned)
        assertThat(check("sigil_wolf", owned = setOf("sigil_eye"))).isEqualTo(BuyCheck.Buyable)
    }

    @Test
    fun `shields stack to the limit and the owned set is irrelevant to them`() {
        assertThat(check("streak_shield", shields = 1)).isEqualTo(BuyCheck.Buyable)
        assertThat(check("streak_shield", shields = 2)).isEqualTo(BuyCheck.ShieldLimit)
        assertThat(check("streak_shield", owned = setOf("streak_shield"), shields = 0)).isEqualTo(BuyCheck.Buyable)
    }

    @Test
    fun `sovereign gold needs rank S even with the gold`() {
        assertThat(check("theme_sovereign_gold", rank = Rank.A)).isEqualTo(BuyCheck.RankRequired(Rank.S))
        assertThat(check("theme_sovereign_gold", rank = Rank.S)).isEqualTo(BuyCheck.Buyable)
        assertThat(check("theme_sovereign_gold", gold = 0, rank = Rank.A)).isEqualTo(BuyCheck.RankRequired(Rank.S))
    }

    @Test
    fun `cache pool is unowned sigils and frames only`() {
        val all = ShopRules.cachePool(emptySet())
        assertThat(all).hasSize(12)
        assertThat(all.map { it.kind }.toSet()).containsExactly(ShopKind.FRAME, ShopKind.SIGIL)
        val pool = ShopRules.cachePool(setOf("frame_iron", "sigil_star", "theme_crimson_gate"))
        assertThat(pool.map { it.id }).containsNoneOf("frame_iron", "sigil_star")
        assertThat(pool).hasSize(10)
    }

    @Test
    fun `cache drop is deterministic per open id and stays inside the pool`() {
        val pool = ShopRules.cachePool(emptySet())
        val first = ShopRules.openCache(pool, "open-1")
        assertThat(ShopRules.openCache(pool, "open-1")).isEqualTo(first)
        assertThat(pool).contains(first)
        val drops = (1..60).map { ShopRules.openCache(pool, "open-$it")?.id }.toSet()
        assertThat(drops.size).isGreaterThan(5)
    }

    @Test
    fun `an empty pool drops nothing`() {
        assertThat(ShopRules.openCache(emptyList(), "open-1")).isNull()
        val everything = ShopRules.catalog.filter { it.inCache }.map { it.id }.toSet()
        assertThat(ShopRules.cachePool(everything)).isEmpty()
    }

    @Test
    fun `only the default and catalogue themes are equippable`() {
        assertThat(ShopRules.isEquippableTheme("default")).isTrue()
        assertThat(ShopRules.isEquippableTheme("theme_abyssal_violet")).isTrue()
        assertThat(ShopRules.isEquippableTheme("frame_iron")).isFalse()
        assertThat(ShopRules.isEquippableTheme("nope")).isFalse()
    }
}
