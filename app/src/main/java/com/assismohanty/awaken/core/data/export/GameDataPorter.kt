package com.assismohanty.awaken.core.data.export

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.LevelCurve
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Export and import of the whole game state (PRD F17).
 *
 * Import is the one place that replaces the ledger tables wholesale: it is a deliberate restore, done in a single
 * transaction after validation, so any failure leaves the existing data untouched. Balances are rebuilt from the
 * imported ledger afterwards (CLAUDE.md invariants 1 and 3; ENGINE_DECISIONS #48).
 */
@Singleton
class GameDataPorter @Inject constructor(
    private val db: AwakenDatabase,
    private val ledger: LedgerRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun suggestedFileName(): String = "awaken-backup-${clock.now().atZone(clock.zone()).toLocalDate()}.json"

    suspend fun export(): String = withContext(ioDispatcher) {
        val tables = transactions.run {
            val raw = db.openHelper.writableDatabase
            ExportDocument.TABLES.associateWith { table -> raw.readRows(table) }
        }
        json.encodeToString(
            ExportDocument.serializer(),
            ExportDocument(ExportDocument.APP_ID, AwakenDatabase.SCHEMA_VERSION, clock.now().toString(), tables),
        )
    }

    suspend fun import(text: String): ImportResult = withContext(ioDispatcher) {
        val doc = try {
            json.decodeFromString(ExportDocument.serializer(), text)
        } catch (e: SerializationException) {
            return@withContext ImportResult.Rejected(ImportProblem.Malformed)
        } catch (e: IllegalArgumentException) {
            return@withContext ImportResult.Rejected(ImportProblem.Malformed)
        }
        val current = ExportUpgrade.toCurrent(doc)
        ExportValidator.check(current)?.let { return@withContext ImportResult.Rejected(it) }
        try {
            transactions.run { replaceAll(current) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportAbort) {
            ImportResult.Rejected(ImportProblem.InvalidRow(e.table))
        }
    }

    private suspend fun replaceAll(doc: ExportDocument): ImportResult {
        val raw = db.openHelper.writableDatabase
        (ExportDocument.TABLES + ExportDocument.CACHE_TABLE).forEach { raw.execSQL("DELETE FROM `$it`") }
        for (table in ExportDocument.TABLES) {
            val columns = raw.columnNames(table)
            for (row in doc.tables.getValue(table)) {
                if (row.keys.any { it !in columns }) throw ImportAbort(table)
                // The framework throws on a constraint violation and returns -1 for other failures; both abort the import.
                val rowId = try {
                    raw.insert(table, SQLiteDatabase.CONFLICT_ABORT, row.toContentValues())
                } catch (e: SQLException) {
                    throw ImportAbort(table)
                }
                if (rowId == -1L) throw ImportAbort(table)
            }
        }
        val balances = ledger.rebuildBalancesFromLedger()
        val ledgerRows = doc.tables.getValue("ledger_entry")
        val dates = ledgerRows.mapNotNull { (it["game_date"] as? JsonPrimitive)?.contentOrNull }
        val name = (doc.tables.getValue("hunter_profile").first()["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return ImportResult.Imported(
            ImportSummary(
                hunterName = name,
                level = LevelCurve.level(balances.xpTotal),
                ledgerRows = ledgerRows.size,
                firstDate = dates.minOrNull(),
                lastDate = dates.maxOrNull(),
            ),
        )
    }

    /** Thrown inside the import transaction so Room rolls everything back. */
    private class ImportAbort(val table: String) : RuntimeException("Import row rejected in $table")

    private fun SupportSQLiteDatabase.columnNames(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIndex)) }
        }

    private fun SupportSQLiteDatabase.readRows(table: String): List<JsonObject> =
        query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
            buildList {
                while (c.moveToNext()) add(JsonObject((0 until c.columnCount).associate { c.getColumnName(it) to c.jsonValue(it) }))
            }
        }

    private fun Cursor.jsonValue(index: Int) = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(index))
        else -> error("Unsupported column type in ${getColumnName(index)}")
    }

    private fun JsonObject.toContentValues(): ContentValues = ContentValues().also { values ->
        forEach { (key, element) ->
            when {
                element is JsonNull -> values.putNull(key)
                element !is JsonPrimitive -> throw ImportAbort(key)
                element.isString -> values.put(key, element.content)
                element.longOrNull != null -> values.put(key, element.longOrNull)
                element.doubleOrNull != null -> values.put(key, element.doubleOrNull)
                else -> throw ImportAbort(key)
            }
        }
    }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
