package com.assismohanty.awaken.engine

enum class ShopKind { SHIELD }

/**
 * One Store item (GD §9.2). [id] is a stable key; display text lives in the app's strings.xml.
 * The Store sells only functional items: the streak freeze (this catalogue) and extra rerolls (bought on the quest).
 */
data class ShopItem(
    val id: String,
    val kind: ShopKind,
    val price: Int,
)

sealed interface BuyCheck {
    data object Buyable : BuyCheck
    data class InsufficientGold(val missing: Long) : BuyCheck
    data object ShieldLimit : BuyCheck
}

/** The Store catalogue and purchase rules (GD §9.2, ENGINE_DECISIONS #42–#45). */
object ShopRules {
    const val SHIELD_ID = "streak_shield"
    const val SHIELD_PRICE = 300

    val catalog: List<ShopItem> = listOf(ShopItem(SHIELD_ID, ShopKind.SHIELD, SHIELD_PRICE))

    val byId: Map<String, ShopItem> = catalog.associateBy { it.id }

    /** Whether [item] can be bought now. Order of checks: shield limit → gold, so the message names the first thing to fix. */
    fun canBuy(item: ShopItem, gold: Long, shields: Int): BuyCheck = when {
        item.kind == ShopKind.SHIELD && !StreakRules.canBuyShield(shields) -> BuyCheck.ShieldLimit
        gold < item.price -> BuyCheck.InsufficientGold(item.price - gold)
        else -> BuyCheck.Buyable
    }
}
