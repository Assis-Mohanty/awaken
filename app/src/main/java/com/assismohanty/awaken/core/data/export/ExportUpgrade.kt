package com.assismohanty.awaken.core.data.export

/**
 * Brings an export written by an earlier schema up to the current one before validation (ENGINE_DECISIONS #48: "add a
 * document-upgrade step beside the Room migration"). Each step mirrors one Room migration, so a backup taken before an
 * update still imports after it.
 */
object ExportUpgrade {
    /** v4 → v5 (Phase 15): the Path tables did not exist, so the old file simply has none of their rows. */
    private val PATH_TABLES = listOf("path", "path_milestone", "path_week", "path_slot", "ai_report")
    private const val APP_RULE_TABLE = "app_rule"
    private const val CAMPAIGN_TABLE = "campaign"

    fun toCurrent(doc: ExportDocument): ExportDocument {
        var current = doc
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 4) {
            current = current.copy(
                schemaVersion = 5,
                tables = current.tables + PATH_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v5 → v6 (set counter): the new quest column has a default, so an older file simply leaves it out.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 5) {
            current = current.copy(schemaVersion = 6)
        }
        // v6 → v7 (Screen Discipline): no app rules existed, and the new profile column is nullable (= off).
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 6) {
            current = current.copy(
                schemaVersion = 7,
                tables = current.tables + (APP_RULE_TABLE to current.tables[APP_RULE_TABLE].orEmpty()),
            )
        }
        // v7 → v8 (45-day Campaign): no campaigns existed, and the new habit columns are nullable (= plain habits).
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 7) {
            current = current.copy(
                schemaVersion = 8,
                tables = current.tables + (CAMPAIGN_TABLE to current.tables[CAMPAIGN_TABLE].orEmpty()),
            )
        }
        return current
    }
}
