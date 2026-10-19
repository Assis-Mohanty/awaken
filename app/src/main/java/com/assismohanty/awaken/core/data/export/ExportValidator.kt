package com.assismohanty.awaken.core.data.export

import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Checks an [ExportDocument] before anything is written (PRD F17: import validates with the engine's vocabulary).
 * Every enum-typed column is checked too: Room maps those by name on read, so one unknown value would otherwise import
 * cleanly and then crash the app on every launch.
 */
object ExportValidator {
    private fun names(values: Array<out Enum<*>>): Set<String> = values.map { it.name }.toSet()

    private val stats = names(Stat.entries.toTypedArray())

    /** Column → allowed names per table; a nullable column also accepts null. */
    private val enumColumns: Map<String, Map<String, Set<String>>> = mapOf(
        "hunter_profile" to mapOf("focus_stat_1" to stats, "focus_stat_2" to stats),
        "stat_tier" to mapOf("stat" to stats, "tier" to names(Difficulty.entries.toTypedArray())),
        "quest_instance" to mapOf(
            "type" to names(QuestType.entries.toTypedArray()),
            "stat" to stats,
            "secondary_stat" to stats,
            "difficulty" to names(Difficulty.entries.toTypedArray()),
            "verification" to names(Verification.entries.toTypedArray()),
            "status" to names(QuestStatus.entries.toTypedArray()),
        ),
        "completion" to mapOf(
            "quest_type" to names(QuestType.entries.toTypedArray()),
            "stat" to stats,
            "verification_source" to names(VerificationSource.entries.toTypedArray()),
        ),
        "habit" to mapOf(
            "stat" to stats,
            "difficulty" to names(Difficulty.entries.toTypedArray()),
            "verification" to names(Verification.entries.toTypedArray()),
        ),
        "ledger_entry" to mapOf(
            "kind" to names(LedgerKind.entries.toTypedArray()),
            "source_type" to names(SourceType.entries.toTypedArray()),
        ),
        "day_record" to mapOf("outcome" to names(DayOutcome.entries.toTypedArray())),
        "gate_run" to mapOf(
            "gate_rank" to names(GateRank.entries.toTypedArray()),
            "stat" to stats,
            "status" to names(GateRunStatus.entries.toTypedArray()),
            "collapse_reason" to names(GateCollapseReason.entries.toTypedArray()),
        ),
        "trial" to mapOf(
            "target_rank" to names(Rank.entries.toTypedArray()),
            "status" to names(RankRules.TrialStatus.entries.toTypedArray()),
        ),
    )

    fun check(doc: ExportDocument): ImportProblem? {
        if (doc.app != ExportDocument.APP_ID) return ImportProblem.NotAwakenFile
        if (doc.schemaVersion > AwakenDatabase.SCHEMA_VERSION) return ImportProblem.NewerVersion(doc.schemaVersion)
        if (doc.schemaVersion < AwakenDatabase.SCHEMA_VERSION) return ImportProblem.OlderVersion(doc.schemaVersion)
        ExportDocument.TABLES.firstOrNull { it !in doc.tables }?.let { return ImportProblem.MissingTable(it) }
        if (doc.tables.keys.any { it !in ExportDocument.TABLES }) return ImportProblem.Malformed
        val profiles = doc.tables.getValue("hunter_profile")
        // The app reads the single profile row by its fixed id; any other id would import as "no hunter".
        if (profiles.size != 1 || profiles.single().text("id") != HunterProfileEntity.SINGLETON_ID) return ImportProblem.NoHunter
        doc.tables.getValue("ledger_entry").forEachIndexed { index, row ->
            if (!row.isValidLedgerRow()) return ImportProblem.InvalidLedgerRow(index)
        }
        for ((table, columns) in enumColumns) {
            val bad = doc.tables.getValue(table).any { row ->
                columns.any { (column, allowed) -> !row.hasEnumValue(column, allowed) }
            }
            if (bad) return ImportProblem.InvalidRow(table)
        }
        return null
    }

    /** Absent or null is left to the schema (NOT NULL columns fail the insert and roll back); a value must be known. */
    private fun JsonObject.hasEnumValue(column: String, allowed: Set<String>): Boolean {
        val value = this[column] ?: return true
        if (value is JsonNull) return true
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content in allowed
    }

    private fun JsonObject.isValidLedgerRow(): Boolean {
        val kind = text("kind")?.let { name -> LedgerKind.entries.firstOrNull { it.name == name } } ?: return false
        if (text("source_type")?.let { name -> SourceType.entries.any { it.name == name } } != true) return false
        if (this["delta"].asLong() == null) return false
        if (text("source_id").isNullOrEmpty() || text("game_date").isNullOrEmpty()) return false
        return kind != LedgerKind.XP_STAT || text("stat")?.let { name -> Stat.entries.any { it.name == name } } == true
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun Any?.asLong(): Long? = ((this as? JsonPrimitive)?.takeIf { !it.isString })?.longOrNull
}
