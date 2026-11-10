package com.assismohanty.awaken.core.data.export

import kotlinx.serialization.json.JsonObject

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
    private const val SUBJECT_TABLE = "focus_subject"
    private val FOCUS_V20_TABLES = listOf("pomodoro_session", "focus_goal")
    private val GYM_V21_TABLES = listOf("exercise_pref", "gym_settings")
    private val GYM_V29_TABLES = listOf("week_slot", "day_override")
    private const val PAUSE_TABLE = "pause_event"
    private const val BEDTIME_TABLE = "bedtime_rule"
    private val QUIT_V23_TABLES = listOf("quit_counter", "relapse", "quit_day")
    private val LONG_ARC_V24_TABLES = listOf("dungeon", "program", "program_mark")
    /** v25 → v26: tables removed by the declutter, and the columns removed from three surviving tables. */
    private val REMOVED_V26_TABLES = listOf("achievement_unlock", "owned_cosmetic", "campaign", "dungeon", "recap")
    private val REMOVED_V26_COLUMNS = mapOf(
        "hunter_profile" to listOf("equipped_title_id", "theme_id", "equipped_frame_id", "equipped_sigil_id"),
        "habit" to listOf("campaign_id"),
        "quest_instance" to listOf("dungeon_id", "dungeon_stage"),
    )
    /** v26 → v27: the identities column left the profile. */
    private const val PROFILE_TABLE = "hunter_profile"
    private const val IDENTITIES_COLUMN = "identities"
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
        // v14 → v15 (custom and stopwatch gates): no subjects existed, and the new gate_run columns default to PRESET / none.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 14) {
            current = current.copy(
                schemaVersion = 15,
                tables = current.tables + (SUBJECT_TABLE to current.tables[SUBJECT_TABLE].orEmpty()),
            )
        }
        // v15 → v16 (gym rest): the new columns are nullable (= no rest timer), so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 15) {
            current = current.copy(schemaVersion = 16)
        }
        // v16 → v17 (stopwatch pauses): the new gate_run column defaults to 0, so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 16) {
            current = current.copy(schemaVersion = 17)
        }
        // v17 → v18 (Focus Lock): the new gate_run column defaults to 0 and there are no new tables, so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 17) {
            current = current.copy(schemaVersion = 18)
        }
        // v18 → v19 (habit types): every new column defaults to the old behaviour (CHECK, fixed weekdays, no reminder), so old rows import as they are.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 18) {
            current = current.copy(schemaVersion = 19)
        }
        // v19 → v20 (Pomodoro, focus goal): neither table existed, and the new gate_run columns are nullable (= not a Pomodoro round).
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 19) {
            current = current.copy(
                schemaVersion = 20,
                tables = current.tables + FOCUS_V20_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v20 → v21 (progression hints, plate calculator): neither table existed, so the old file has none of their rows.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 20) {
            current = current.copy(
                schemaVersion = 21,
                tables = current.tables + GYM_V21_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v21 → v22 (step goal, phone-down nights): the new profile column defaults to 8,000 and no night was ever set.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 21) {
            current = current.copy(
                schemaVersion = 22,
                tables = current.tables + (BEDTIME_TABLE to current.tables[BEDTIME_TABLE].orEmpty()),
            )
        }
        // v22 → v23 (quit counters): none existed, so the old file has none of their rows.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 22) {
            current = current.copy(
                schemaVersion = 23,
                tables = current.tables + QUIT_V23_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v23 → v24 (Dungeons, Programs): none existed, and the new quest and habit columns are nullable (= part of neither).
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 23) {
            current = current.copy(
                schemaVersion = 24,
                tables = current.tables + LONG_ARC_V24_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v24 → v25: quest_instance gains a nullable night_verdict (= unknown, as before); no table changes.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 24) {
            current = current.copy(schemaVersion = 25)
        }
        // v25 → v26 (declutter Batch 1): achievements, cosmetics, campaigns, dungeons and recaps are gone. Their tables and the
        // removed columns are dropped from the file, so any older backup still imports; the ledger is carried over untouched.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 25) {
            current = current.copy(
                schemaVersion = 26,
                tables = current.tables.filterKeys { it !in REMOVED_V26_TABLES }.mapValues { (table, rows) ->
                    val dropped = REMOVED_V26_COLUMNS[table]
                    if (dropped == null) rows else rows.map { row -> JsonObject(row.filterKeys { it !in dropped }) }
                },
            )
        }
        // v26 → v27 (declutter Batch 2): identities are gone, so the column is dropped from every profile row.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 26) {
            current = current.copy(
                schemaVersion = 27,
                tables = current.tables.mapValues { (table, rows) ->
                    if (table != PROFILE_TABLE) rows else rows.map { row -> JsonObject(row.filterKeys { it != IDENTITIES_COLUMN }) }
                },
            )
        }
        // v27 → v28 (quiz onboarding): the new profile columns are nullable or default to none, so an older file simply leaves them out.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 27) {
            current = current.copy(schemaVersion = 28)
        }
        // v28 → v29 (Gym redesign): the weekly schedule and day overrides did not exist, and every new column is nullable or
        // has a default, so an older file simply leaves them out.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 28) {
            current = current.copy(
                schemaVersion = 29,
                tables = current.tables + GYM_V29_TABLES.filter { it !in current.tables }.associateWith { emptyList() },
            )
        }
        // v29 → v30 (Pause): the table did not exist, so the old file has none of its rows.
        if (current.app == ExportDocument.APP_ID && current.schemaVersion == 29) {
            current = current.copy(
                schemaVersion = 30,
                tables = current.tables + (PAUSE_TABLE to current.tables[PAUSE_TABLE].orEmpty()),
            )
        }
        return current
    }
}
