package com.assismohanty.awaken.core.data.export

/**
 * Brings an export written by an earlier schema up to the current one before validation (ENGINE_DECISIONS #48: "add a
 * document-upgrade step beside the Room migration"). Each step mirrors one Room migration, so a backup taken before an
 * update still imports after it.
 */
object ExportUpgrade {
    /** v4 → v5 (Phase 15): the Path tables did not exist, so the old file simply has none of their rows. */
    private val PATH_TABLES = listOf("path", "path_milestone", "path_week", "path_slot", "ai_report")

    fun toCurrent(doc: ExportDocument): ExportDocument {
        var current = doc
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 4) {
            current = current.copy(
                schemaVersion = 5,
                tables = current.tables + PATH_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        return current
    }
}
