package com.assismohanty.awaken.engine.model

/**
 * One append-only ledger row (GD §9.3). [key] is the idempotency key; `stat`/`itemId` use `""` rather than null so the
 * UNIQUE constraint holds in SQLite (ARCHITECTURE §2).
 */
data class LedgerEntry(
    val kind: LedgerKind,
    val delta: Long,
    val sourceType: SourceType,
    val sourceId: String,
    val stat: String = "",
    val itemId: String = "",
) {
    val key: Key get() = Key(sourceType, sourceId, kind, stat, itemId)

    data class Key(
        val sourceType: SourceType,
        val sourceId: String,
        val kind: LedgerKind,
        val stat: String,
        val itemId: String,
    )
}

/** Balances derived from the ledger. Keyed like `balance_cache`: `xp_total`, `gold`, `xp_STR`, `item_<id>`, `fragments`. */
data class Balances(val values: Map<String, Long> = emptyMap()) {
    operator fun get(key: String): Long = values[key] ?: 0L

    val xpTotal: Long get() = this[XP_TOTAL]
    val gold: Long get() = this[GOLD]
    val fragments: Long get() = this[FRAGMENTS]
    fun statXp(stat: Stat): Long = this[statKey(stat)]
    fun item(itemId: String): Long = this[itemKey(itemId)]

    companion object {
        const val XP_TOTAL = "xp_total"
        const val GOLD = "gold"
        const val FRAGMENTS = "fragments"
        fun statKey(stat: Stat): String = "xp_${stat.name}"
        fun itemKey(itemId: String): String = "item_$itemId"
    }
}
