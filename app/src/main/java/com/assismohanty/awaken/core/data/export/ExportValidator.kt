package com.assismohanty.awaken.core.data.export

import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Checks an [ExportDocument] before anything is written (PRD F17: import validates with the engine's vocabulary). */
object ExportValidator {
    fun check(doc: ExportDocument): ImportProblem? {
        if (doc.app != ExportDocument.APP_ID) return ImportProblem.NotAwakenFile
        if (doc.schemaVersion > AwakenDatabase.SCHEMA_VERSION) return ImportProblem.NewerVersion(doc.schemaVersion)
        if (doc.schemaVersion < AwakenDatabase.SCHEMA_VERSION) return ImportProblem.OlderVersion(doc.schemaVersion)
        ExportDocument.TABLES.firstOrNull { it !in doc.tables }?.let { return ImportProblem.MissingTable(it) }
        if (doc.tables.keys.any { it !in ExportDocument.TABLES }) return ImportProblem.Malformed
        if (doc.tables.getValue("hunter_profile").size != 1) return ImportProblem.NoHunter
        doc.tables.getValue("ledger_entry").forEachIndexed { index, row ->
            if (!row.isValidLedgerRow()) return ImportProblem.InvalidLedgerRow(index)
        }
        return null
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
