package com.assismohanty.awaken.core.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The export file (PRD F17): every Room table that holds game state, row by row, as the raw column values. Dates and
 * instants stay in their stored form (ISO text / epoch millis), so a round trip is lossless. `balance_cache` is left out
 * because import rebuilds it from the ledger.
 */
@Serializable
data class ExportDocument(
    val app: String,
    val schemaVersion: Int,
    val exportedAt: String,
    val tables: Map<String, List<JsonObject>>,
) {
    companion object {
        const val APP_ID = "awaken"

        /** Tables written to the file, in dependency-free order. Kept in sync with the Room schema by a test. */
        val TABLES: List<String> = listOf(
            "hunter_profile", "stat_tier", "habit", "quest_instance", "completion", "ledger_entry", "day_record",
            "gate_run", "trial", "achievement_unlock", "owned_cosmetic", "health_sync_state",
        )

        /** Rebuilt after import. */
        const val CACHE_TABLE = "balance_cache"
    }
}

data class ImportSummary(
    val hunterName: String,
    val level: Int,
    val ledgerRows: Int,
    /** First and last game-date in the ledger as ISO text; null for an empty ledger. */
    val firstDate: String?,
    val lastDate: String?,
)

sealed interface ImportProblem {
    data object Malformed : ImportProblem
    data object NotAwakenFile : ImportProblem
    data class NewerVersion(val version: Int) : ImportProblem
    data class OlderVersion(val version: Int) : ImportProblem
    data class MissingTable(val table: String) : ImportProblem
    data object NoHunter : ImportProblem
    data class InvalidLedgerRow(val index: Int) : ImportProblem
    /** A row didn't fit the schema (unknown column, constraint violation). Nothing was changed. */
    data class InvalidRow(val table: String) : ImportProblem
}

sealed interface ImportResult {
    data class Imported(val summary: ImportSummary) : ImportResult
    data class Rejected(val problem: ImportProblem) : ImportResult
}
