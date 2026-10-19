package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Rank

enum class ShopKind { SHIELD, THEME, FRAME, SIGIL }

/**
 * One Vault item (GD §9.2). [id] is a stable key; display text lives in the app's strings.xml.
 * @param price gold cost. [requiresRank] is the displayed rank (highest passed Rank Trial) needed to buy.
 * @param inCache whether the Relic Cache can drop it (GD §9.2: sigils and frames only).
 */
data class ShopItem(
    val id: String,
    val kind: ShopKind,
    val price: Int,
    val requiresRank: Rank? = null,
) {
    val inCache: Boolean get() = kind == ShopKind.FRAME || kind == ShopKind.SIGIL
}

sealed interface BuyCheck {
    data object Buyable : BuyCheck
    data class InsufficientGold(val missing: Long) : BuyCheck
    data object AlreadyOwned : BuyCheck
    data object ShieldLimit : BuyCheck
    data class RankRequired(val rank: Rank) : BuyCheck
}

/** The Vault catalogue and purchase rules (GD §9.2, ENGINE_DECISIONS #42–#45). */
object ShopRules {
    const val DEFAULT_THEME_ID = "default"
    const val SHIELD_ID = "streak_shield"
    const val SHIELD_PRICE = 300
    const val THEME_PRICE = 1_500
    const val SOVEREIGN_PRICE = 3_000
    const val SIGIL_PRICE = 400
    const val CACHE_FRAGMENTS = 10

    /** The four title frames, cheapest first (prices: ENGINE_DECISIONS #42). */
    private val framePrices = listOf(600, 800, 1_000, 1_200)
    private val frameIds = listOf("frame_iron", "frame_ember", "frame_umbral", "frame_regal")
    private val sigilIds = listOf(
        "sigil_blade", "sigil_flame", "sigil_eye", "sigil_wolf",
        "sigil_crown", "sigil_hourglass", "sigil_star", "sigil_gate",
    )

    val catalog: List<ShopItem> = buildList {
        add(ShopItem(SHIELD_ID, ShopKind.SHIELD, SHIELD_PRICE))
        add(ShopItem("theme_crimson_gate", ShopKind.THEME, THEME_PRICE))
        add(ShopItem("theme_abyssal_violet", ShopKind.THEME, THEME_PRICE))
        add(ShopItem("theme_sovereign_gold", ShopKind.THEME, SOVEREIGN_PRICE, requiresRank = Rank.S))
        frameIds.forEachIndexed { i, id -> add(ShopItem(id, ShopKind.FRAME, framePrices[i])) }
        sigilIds.forEach { add(ShopItem(it, ShopKind.SIGIL, SIGIL_PRICE)) }
    }

    val byId: Map<String, ShopItem> = catalog.associateBy { it.id }

    /** Theme ids the hunter can equip: the default plus every theme in the catalogue. */
    fun isEquippableTheme(themeId: String): Boolean =
        themeId == DEFAULT_THEME_ID || byId[themeId]?.kind == ShopKind.THEME

    /**
     * Whether [item] can be bought now. Order of checks: already owned (cosmetics) → shield limit → rank → gold, so
     * the message always names the first thing the hunter has to fix.
     * @param owned owned cosmetic ids; @param shields shields currently held.
     */
    fun canBuy(item: ShopItem, gold: Long, owned: Set<String>, shields: Int, rank: Rank): BuyCheck = when {
        item.kind == ShopKind.SHIELD && !StreakRules.canBuyShield(shields) -> BuyCheck.ShieldLimit
        item.kind != ShopKind.SHIELD && item.id in owned -> BuyCheck.AlreadyOwned
        item.requiresRank != null && rank < item.requiresRank -> BuyCheck.RankRequired(item.requiresRank)
        gold < item.price -> BuyCheck.InsufficientGold(item.price - gold)
        else -> BuyCheck.Buyable
    }

    /** Cosmetics a cache can still drop: sigils and frames not yet owned, in catalogue order. */
    fun cachePool(owned: Set<String>): List<ShopItem> = catalog.filter { it.inCache && it.id !in owned }

    /**
     * Deterministic cache drop (GD §9.2, Phase 10 prompt): the same [openId] over the same pool always yields the same
     * item. Null when the pool is empty.
     */
    fun openCache(pool: List<ShopItem>, openId: String): ShopItem? {
        if (pool.isEmpty()) return null
        val index = java.lang.Long.remainderUnsigned(Fnv1a.hash64("$openId|relic_cache"), pool.size.toLong()).toInt()
        return pool[index]
    }
}
