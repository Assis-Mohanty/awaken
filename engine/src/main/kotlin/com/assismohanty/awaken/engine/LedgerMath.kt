package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Balances
import com.assismohanty.awaken.engine.model.LedgerEntry
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestReward
import com.assismohanty.awaken.engine.model.SourceType

/** Pure ledger semantics (GD §9.3): idempotent apply, reversing undo, balances = SUM(delta). The app mirrors this in Room. */
object LedgerMath {

    /**
     * Id of one completion attempt of a quest: `questInstanceId#attemptN`, with [attempt] starting at 1. Ledger rows use
     * it as their sourceId so a re-completion after undo gets fresh idempotency keys. The crit seed stays the
     * questInstanceId, so re-completing never re-rolls a crit.
     */
    fun completionId(questInstanceId: String, attempt: Int): String {
        require(attempt >= 1) { "attempt must be >= 1" }
        return "$questInstanceId#$attempt"
    }

    /** Ledger rows for a quest reward; [sourceId] is the [completionId]. Zero deltas are omitted (nothing to record). */
    fun rowsFor(reward: QuestReward, sourceType: SourceType, sourceId: String): List<LedgerEntry> = buildList {
        if (reward.xp != 0) add(LedgerEntry(LedgerKind.XP_TOTAL, reward.xp.toLong(), sourceType, sourceId))
        reward.statXp.forEach { (stat, xp) ->
            if (xp != 0) add(LedgerEntry(LedgerKind.XP_STAT, xp.toLong(), sourceType, sourceId, stat = stat.name))
        }
        if (reward.gold != 0) add(LedgerEntry(LedgerKind.GOLD, reward.gold.toLong(), sourceType, sourceId))
        if (reward.fragments != 0) add(LedgerEntry(LedgerKind.FRAGMENT, reward.fragments.toLong(), sourceType, sourceId))
    }

    /**
     * Appends [incoming] to [ledger], skipping any row whose key already exists (INSERT … ON CONFLICT IGNORE).
     * Returns the new ledger and the rows actually inserted.
     */
    fun append(ledger: List<LedgerEntry>, incoming: List<LedgerEntry>): Pair<List<LedgerEntry>, List<LedgerEntry>> {
        val keys = ledger.mapTo(HashSet()) { it.key }
        val inserted = incoming.filter { keys.add(it.key) }
        return (ledger + inserted) to inserted
    }

    /**
     * The reversing row for [original] (GD §9.3): `sourceType = UNDO`, `sourceId` = the original's completionId, same
     * kind/stat/item. Undoing the same completion twice is therefore a no-op.
     */
    fun reversal(original: LedgerEntry): LedgerEntry {
        require(original.sourceType != SourceType.UNDO) { "cannot reverse an undo row" }
        return original.copy(delta = -original.delta, sourceType = SourceType.UNDO)
    }

    /** The `balance_cache` key a row contributes to. */
    fun balanceKey(kind: LedgerKind, stat: String, itemId: String): String = when (kind) {
        LedgerKind.XP_TOTAL -> Balances.XP_TOTAL
        LedgerKind.XP_STAT -> "xp_$stat"
        LedgerKind.GOLD -> Balances.GOLD
        LedgerKind.ITEM -> Balances.itemKey(itemId)
        LedgerKind.FRAGMENT -> Balances.FRAGMENTS
    }

    /** Balances = SUM(delta) per balance key. Duplicate keys are counted once, matching the UNIQUE constraint. */
    fun balances(ledger: List<LedgerEntry>): Balances {
        val seen = HashSet<LedgerEntry.Key>()
        val values = HashMap<String, Long>()
        for (row in ledger) {
            if (!seen.add(row.key)) continue
            val key = balanceKey(row.kind, row.stat, row.itemId)
            values[key] = (values[key] ?: 0L) + row.delta
        }
        return Balances(values)
    }
}
