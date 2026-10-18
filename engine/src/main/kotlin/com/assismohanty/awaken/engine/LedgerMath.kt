package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Balances
import com.assismohanty.awaken.engine.model.LedgerEntry
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestReward
import com.assismohanty.awaken.engine.model.SourceType

/** Pure ledger semantics (GD §9.3): idempotent apply, reversing undo, balances = SUM(delta). The app mirrors this in Room. */
object LedgerMath {

    /** Ledger rows for a quest reward. Zero deltas are omitted (nothing to record). */
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

    /** The reversing row for [original]; [originalRowId] is the original row's primary key (GD §9.3). */
    fun reversal(original: LedgerEntry, originalRowId: String): LedgerEntry = original.copy(
        delta = -original.delta,
        sourceType = SourceType.UNDO,
        sourceId = originalRowId,
    )

    /** Balances = SUM(delta) per balance key. Duplicate keys are counted once, matching the UNIQUE constraint. */
    fun balances(ledger: List<LedgerEntry>): Balances {
        val seen = HashSet<LedgerEntry.Key>()
        val values = HashMap<String, Long>()
        for (row in ledger) {
            if (!seen.add(row.key)) continue
            val key = when (row.kind) {
                LedgerKind.XP_TOTAL -> Balances.XP_TOTAL
                LedgerKind.XP_STAT -> "xp_${row.stat}"
                LedgerKind.GOLD -> Balances.GOLD
                LedgerKind.ITEM -> Balances.itemKey(row.itemId)
                LedgerKind.FRAGMENT -> Balances.FRAGMENTS
            }
            values[key] = (values[key] ?: 0L) + row.delta
        }
        return Balances(values)
    }
}
