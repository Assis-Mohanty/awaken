package com.assismohanty.awaken.core.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private val graph = TestGraph()
    private val ledger = graph.ledger
    private val date = LocalDate.of(2026, 10, 18)
    private val now = Instant.parse("2026-10-18T10:00:00Z")

    @After
    fun tearDown() = graph.close()

    private fun row(kind: LedgerKind, delta: Long, sourceId: String, stat: String = "", itemId: String = "") =
        LedgerEntryEntity(
            kind = kind, stat = stat, itemId = itemId, delta = delta, sourceType = SourceType.QUEST, sourceId = sourceId,
            gameDate = date, createdAt = now,
        )

    @Test
    fun sameRowsTwiceInsertOnceAndLeaveBalancesUnchanged() = runTest {
        val rows = listOf(row(LedgerKind.XP_TOTAL, 60, "q#1"), row(LedgerKind.XP_STAT, 60, "q#1", stat = "STR"), row(LedgerKind.GOLD, 30, "q#1"))

        val first = ledger.insertIdempotent(rows)
        val balancesAfterFirst = ledger.balances()
        val second = ledger.insertIdempotent(rows)

        assertThat(first).hasSize(3)
        assertThat(second).isEmpty()
        assertThat(ledger.balances()).isEqualTo(balancesAfterFirst)
        assertThat(balancesAfterFirst.xpTotal).isEqualTo(60)
        assertThat(graph.db.ledgerDao().all()).hasSize(3)
    }

    @Test
    fun emptyStringKeyColumnsKeepUniquenessWhereNullsWouldNot() = runTest {
        // GOLD rows have no stat/item: stored as '' so SQLite's UNIQUE treats them as equal (ARCH §2 NULL bug).
        ledger.insertIdempotent(listOf(row(LedgerKind.GOLD, 30, "q#1")))
        val duplicate = ledger.insertIdempotent(listOf(row(LedgerKind.GOLD, 30, "q#1")))

        assertThat(duplicate).isEmpty()
        assertThat(ledger.balances().gold).isEqualTo(30)
    }

    @Test
    fun undoRowsNetToZeroAndKeepOriginalDateAndSource() = runTest {
        val original = row(LedgerKind.XP_TOTAL, 60, "q#1").copy(xpSource = "SELF")
        ledger.insertIdempotent(listOf(original))
        val reversals = ledger.reversalsOf(graph.db.ledgerDao().all(), now.plusSeconds(30))
        ledger.insertIdempotent(reversals)
        ledger.insertIdempotent(reversals)

        val rows = graph.db.ledgerDao().all()
        assertThat(rows).hasSize(2)
        assertThat(rows.last().sourceType).isEqualTo(SourceType.UNDO)
        assertThat(rows.last().sourceId).isEqualTo("q#1")
        assertThat(rows.last().gameDate).isEqualTo(date)
        assertThat(rows.last().xpSource).isEqualTo("SELF")
        assertThat(ledger.balances().xpTotal).isEqualTo(0)
    }

    @Test
    fun rebuildFromLedgerEqualsIncrementalCache() = runTest {
        ledger.insertIdempotent(listOf(row(LedgerKind.XP_TOTAL, 60, "a#1"), row(LedgerKind.GOLD, 30, "a#1")))
        ledger.insertIdempotent(listOf(row(LedgerKind.XP_STAT, 45, "b#1", stat = "INT"), row(LedgerKind.ITEM, 1, "b#1", itemId = "streak_shield")))
        ledger.insertIdempotent(ledger.reversalsOf(graph.db.ledgerDao().bySource(listOf(SourceType.QUEST), "a#1"), now))

        val cached = ledger.balances()
        assertThat(ledger.balancesFromLedger()).isEqualTo(cached)
        assertThat(ledger.rebuildBalancesFromLedger()).isEqualTo(cached)
        assertThat(ledger.balances()).isEqualTo(cached)
    }
}
