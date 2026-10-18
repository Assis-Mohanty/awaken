package com.assismohanty.awaken.core.data.repo

import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.entity.BalanceCacheEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.engine.LedgerMath
import com.assismohanty.awaken.engine.model.Balances
import com.assismohanty.awaken.engine.model.LedgerEntry
import com.assismohanty.awaken.engine.model.SourceType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only writer of the ledger and `balance_cache` (invariants 1–3). Rows are only ever inserted; the cache is updated
 * in the same transaction and can always be rebuilt from the ledger.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val ledgerDao: LedgerDao,
    private val transactions: TransactionRunner,
) {
    /** Inserts [rows], skipping existing idempotency keys, and returns the rows actually inserted. */
    suspend fun insertIdempotent(rows: List<LedgerEntryEntity>): List<LedgerEntryEntity> = transactions.run {
        val inserted = rows.mapNotNull { row ->
            val id = ledgerDao.insert(row)
            if (id == -1L) null else row.copy(id = id)
        }
        applyToCache(inserted)
        inserted
    }

    /** Reversing rows for [originals] (GD §9.3): same key with sourceType UNDO; game-date and xp_source are kept. */
    fun reversalsOf(originals: List<LedgerEntryEntity>, now: Instant): List<LedgerEntryEntity> = originals.map { row ->
        val reversed = LedgerMath.reversal(row.toEngine())
        row.copy(id = 0, delta = reversed.delta, sourceType = reversed.sourceType, createdAt = now, breakdownJson = null)
    }

    suspend fun balances(): Balances = Balances(ledgerDao.balances().associate { it.key to it.value })

    fun observeBalances(): Flow<Balances> =
        ledgerDao.observeBalances().map { rows -> Balances(rows.associate { it.key to it.value }) }

    /** Recomputes `balance_cache` from scratch (Settings → Advanced, Debug screen). */
    suspend fun rebuildBalancesFromLedger(): Balances = transactions.run {
        val balances = LedgerMath.balances(ledgerDao.all().map { it.toEngine() })
        ledgerDao.clearBalances()
        ledgerDao.upsertBalances(balances.values.map { (key, value) -> BalanceCacheEntity(key, value) })
        balances
    }

    /** What a rebuild would produce, without touching the cache (tests, debug consistency check). */
    suspend fun balancesFromLedger(): Balances = LedgerMath.balances(ledgerDao.all().map { it.toEngine() })

    private suspend fun applyToCache(inserted: List<LedgerEntryEntity>) {
        if (inserted.isEmpty()) return
        val deltas = inserted.groupBy { LedgerMath.balanceKey(it.kind, it.stat, it.itemId) }
            .mapValues { (_, rows) -> rows.sumOf { it.delta } }
        ledgerDao.upsertBalances(
            deltas.map { (key, delta) -> BalanceCacheEntity(key, (ledgerDao.balance(key) ?: 0L) + delta) },
        )
    }

    companion object {
        const val STREAK_SHIELD_ITEM_ID = "streak_shield"
    }
}

fun LedgerEntryEntity.toEngine(): LedgerEntry = LedgerEntry(
    kind = kind,
    delta = delta,
    sourceType = sourceType,
    sourceId = sourceId,
    stat = stat,
    itemId = itemId,
)

/** Sources whose rows a completion undo reverses. */
val COMPLETION_SOURCE_TYPES: List<SourceType> = listOf(SourceType.QUEST, SourceType.GATE)
