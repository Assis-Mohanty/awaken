package com.assismohanty.awaken.engine

enum class ShopKind { SHIELD, LOCKDOWN_SHIELD }

/**
 * One Store item (GD §9.2). [id] is a stable key; display text lives in the app's strings.xml.
 * The Store sells only functional items: the streak freeze, the Lockdown shield (this catalogue) and extra rerolls (bought on the quest).
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

    /** The shield that lets the hunter leave a Lockdown once a day (GD §19.2 LD3). Price decided by the user 2026-11-10. */
    const val LOCKDOWN_SHIELD_ID = "lockdown_shield"
    const val LOCKDOWN_SHIELD_PRICE = 1_000

    /** Shields a debug build hands out in one tap, so the Lockdown can be tried. */
    const val DEBUG_LOCKDOWN_SHIELDS = 5

    val catalog: List<ShopItem> = listOf(
        ShopItem(SHIELD_ID, ShopKind.SHIELD, SHIELD_PRICE),
        ShopItem(LOCKDOWN_SHIELD_ID, ShopKind.LOCKDOWN_SHIELD, LOCKDOWN_SHIELD_PRICE),
    )

    val byId: Map<String, ShopItem> = catalog.associateBy { it.id }

    /**
     * Whether [item] can be bought now. Order of checks: shield limit → gold, so the message names the first thing to fix.
     * [shields] is the streak shields held; Lockdown shields have no limit.
     */
    fun canBuy(item: ShopItem, gold: Long, shields: Int): BuyCheck = when {
        item.kind == ShopKind.SHIELD && !StreakRules.canBuyShield(shields) -> BuyCheck.ShieldLimit
        gold < item.price -> BuyCheck.InsufficientGold(item.price - gold)
        else -> BuyCheck.Buyable
    }
}
