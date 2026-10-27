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
    private const val REVIEW_TABLE = "weekly_review"
    private const val RECAP_TABLE = "recap"
    private val BODY_TABLES = listOf(
        "custom_exercise", "routine", "routine_exercise", "workout", "workout_set", "custom_food", "food_log", "nutrition_profile",
    )

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
        // v8 → v9 (Gym and Diet): the tracker tables did not exist, so the old file has none of their rows.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 8) {
            current = current.copy(
                schemaVersion = 9,
                tables = current.tables + BODY_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v9 → v10 (time-of-day blocks): the new quest and habit columns are nullable (= Anytime), so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 9) {
            current = current.copy(schemaVersion = 10)
        }
        // v10 → v11 (identities): the new profile column defaults to empty (= none), so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 10) {
            current = current.copy(schemaVersion = 11)
        }
        // v11 → v12 (insights): only a rebuildable cache table was added, and it is never exported.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 11) {
            current = current.copy(schemaVersion = 12)
        }
        // v12 → v13 (weekly review): no reviews existed, so the old file has none.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 12) {
            current = current.copy(
                schemaVersion = 13,
                tables = current.tables + (REVIEW_TABLE to current.tables[REVIEW_TABLE].orEmpty()),
            )
        }
        // v13 → v14 (recaps): none existed; the new Health cache column is never exported.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 13) {
            current = current.copy(
                schemaVersion = 14,
                tables = current.tables + (RECAP_TABLE to current.tables[RECAP_TABLE].orEmpty()),
            )
        }
        return current
    }
}
