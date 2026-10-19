package com.assismohanty.awaken.core.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 10b: export → import round trip, validation and atomicity on real in-memory databases (PRD F17). */
@RunWith(AndroidJUnit4::class)
class GameDataPorterTest {
    private val source = TestGraph()
    private val target = TestGraph()

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun playSomeGame() {
        source.seedHunter()
        listOf("one", "two", "crit").forEach { name ->
            val quest = source.fixtureQuest(name, crit = name == "crit")
            source.completeQuest(quest.id)
        }
        source.grant(LedgerKind.GOLD, 2_000, "bonus")
        source.purchaseItem("streak_shield", "buy-shield")
        source.purchaseItem("sigil_wolf", "buy-sigil")
        source.equipCosmetic(ShopKind.SIGIL, "sigil_wolf")
        source.updateSettings.setEquipment(setOf("dumbbells"))
        source.updateSettings.setInjury(true, setOf("upper"))
    }

    private fun tablesOf(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject.getValue("tables").jsonObject

    private fun mutate(text: String, change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val root = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        change(root)
        return JsonObject(root).toString()
    }

    private fun mutateTable(text: String, table: String, change: (List<JsonObject>) -> List<JsonObject>): String =
        mutate(text) { root ->
            val tables = root.getValue("tables").jsonObject.toMutableMap()
            tables[table] = JsonArray(change(tables.getValue(table).jsonArray.map { it.jsonObject }))
            root["tables"] = JsonObject(tables)
        }

    @Test
    fun aRoundTripRestoresEveryTableAndTheBalances() = runTest {
        playSomeGame()
        val exported = source.porter.export()
        target.seedHunter() // different data that must be replaced
        target.fixtureQuest("old").let { target.completeQuest(it.id) }

        val result = target.porter.import(exported)

        assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(exported))
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(target.ledger.balances()).isEqualTo(target.ledger.balancesFromLedger())
        assertThat(target.db.hunterDao().getProfile()).isEqualTo(source.db.hunterDao().getProfile())
        assertThat(target.db.hunterDao().getProfile()?.equippedSigilId).isEqualTo("sigil_wolf")
        assertThat(target.db.hunterDao().getProfile()?.injuryTags).containsExactly("upper")
    }

    @Test
    fun theSummaryDescribesWhatWasRestored() = runTest {
        playSomeGame()
        val summary = (target.porter.import(source.porter.export()) as ImportResult.Imported).summary

        assertThat(summary.hunterName).isEqualTo("Test Hunter")
        assertThat(summary.level).isAtLeast(1)
        assertThat(summary.ledgerRows).isEqualTo(source.db.ledgerDao().all().size)
        assertThat(summary.firstDate).isNotNull()
    }

    @Test
    fun theFileCarriesNoBalanceCacheAndCoversEveryGameTable() = runTest {
        playSomeGame()
        val tables = tablesOf(source.porter.export())
        assertThat(tables.keys).doesNotContain("balance_cache")
        assertThat(tables.keys).containsExactlyElementsIn(ExportDocument.TABLES)

        val roomTables = source.db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            .filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it == "android_metadata" || it == "balance_cache" }
        assertThat(ExportDocument.TABLES).containsExactlyElementsIn(roomTables)
        assertThat(AwakenDatabase.SCHEMA_VERSION).isEqualTo(source.db.openHelper.readableDatabase.version)
    }

    private suspend fun assertRejectedAndUntouched(text: String, problem: ImportProblem) {
        target.seedHunter()
        target.fixtureQuest("keep").let { target.completeQuest(it.id) }
        val before = target.porter.export()
        val balancesBefore = target.ledger.balances()

        assertThat(target.porter.import(text)).isEqualTo(ImportResult.Rejected(problem))

        assertThat(target.porter.export()).isEqualTo(before)
        assertThat(target.ledger.balances()).isEqualTo(balancesBefore)
    }

    @Test
    fun malformedAndForeignFilesAreRejected() = runTest {
        playSomeGame()
        val good = source.porter.export()
        assertRejectedAndUntouched("not json at all", ImportProblem.Malformed)
        assertRejectedAndUntouched("{\"hello\": 1}", ImportProblem.Malformed)
        assertRejectedAndUntouched(mutate(good) { it["app"] = JsonPrimitive("other") }, ImportProblem.NotAwakenFile)
    }

    @Test
    fun otherSchemaVersionsAreRejected() = runTest {
        playSomeGame()
        val good = source.porter.export()
        assertRejectedAndUntouched(mutate(good) { it["schemaVersion"] = JsonPrimitive(AwakenDatabase.SCHEMA_VERSION + 1) }, ImportProblem.NewerVersion(AwakenDatabase.SCHEMA_VERSION + 1))
        assertRejectedAndUntouched(mutate(good) { it["schemaVersion"] = JsonPrimitive(AwakenDatabase.SCHEMA_VERSION - 1) }, ImportProblem.OlderVersion(AwakenDatabase.SCHEMA_VERSION - 1))
    }

    @Test
    fun aMissingTableOrHunterIsRejected() = runTest {
        playSomeGame()
        val good = source.porter.export()
        assertRejectedAndUntouched(
            mutate(good) { root -> root["tables"] = JsonObject(root.getValue("tables").jsonObject - "ledger_entry") },
            ImportProblem.MissingTable("ledger_entry"),
        )
        assertRejectedAndUntouched(mutateTable(good, "hunter_profile") { emptyList() }, ImportProblem.NoHunter)
    }

    @Test
    fun aLedgerRowWithAnUnknownKindOrSourceIsRejected() = runTest {
        playSomeGame()
        val good = source.porter.export()
        val badKind = mutateTable(good, "ledger_entry") { rows ->
            rows.mapIndexed { i, row -> if (i == 0) JsonObject(row + ("kind" to JsonPrimitive("MAGIC"))) else row }
        }
        assertRejectedAndUntouched(badKind, ImportProblem.InvalidLedgerRow(0))
        val badSource = mutateTable(good, "ledger_entry") { rows ->
            rows.mapIndexed { i, row -> if (i == 1) JsonObject(row + ("source_type" to JsonPrimitive("GIFT"))) else row }
        }
        assertRejectedAndUntouched(badSource, ImportProblem.InvalidLedgerRow(1))
    }

    @Test
    fun anUnknownColumnRollsTheWholeImportBack() = runTest {
        playSomeGame()
        val good = source.porter.export()
        val unknownColumn = mutateTable(good, "completion") { rows ->
            rows.map { JsonObject(it + ("surprise" to JsonPrimitive(1))) }
        }
        assertRejectedAndUntouched(unknownColumn, ImportProblem.InvalidRow("completion"))
    }

    @Test
    fun aDuplicateLedgerKeyRollsTheWholeImportBack() = runTest {
        playSomeGame()
        val good = source.porter.export()
        val duplicated = mutateTable(good, "ledger_entry") { rows ->
            val first = rows.first()
            // Same idempotency key as the first row, different id: the unique index must refuse it.
            rows + JsonObject(first + ("id" to JsonPrimitive(9_999)))
        }
        assertRejectedAndUntouched(duplicated, ImportProblem.InvalidRow("ledger_entry"))
    }

    @Test
    fun theHunterCanKeepPlayingAfterAnImport() = runTest {
        playSomeGame()
        target.porter.import(source.porter.export())

        val quest = target.fixtureQuest("after-import")
        val before = target.ledger.balances().xpTotal
        target.completeQuest(quest.id)

        assertThat(target.ledger.balances().xpTotal).isGreaterThan(before)
        assertThat(target.db.hunterDao().getTiers().map { it.stat }).containsExactlyElementsIn(Stat.entries)
    }

    @Test
    fun anUnknownEnumValueIsRejectedBeforeItCanCrashTheApp() = runTest {
        playSomeGame()
        val good = source.porter.export()
        val badStatus = mutateTable(good, "quest_instance") { rows ->
            rows.mapIndexed { i, row -> if (i == 0) JsonObject(row + ("status" to JsonPrimitive("HALF_DONE"))) else row }
        }
        assertRejectedAndUntouched(badStatus, ImportProblem.InvalidRow("quest_instance"))
        val badStat = mutateTable(good, "stat_tier") { rows ->
            rows.mapIndexed { i, row -> if (i == 0) JsonObject(row + ("stat" to JsonPrimitive("LUCK"))) else row }
        }
        assertRejectedAndUntouched(badStat, ImportProblem.InvalidRow("stat_tier"))
    }

    @Test
    fun aProfileWithAnotherIdIsRejected() = runTest {
        playSomeGame()
        val otherId = mutateTable(source.porter.export(), "hunter_profile") { rows ->
            rows.map { JsonObject(it + ("id" to JsonPrimitive("someone_else"))) }
        }
        assertRejectedAndUntouched(otherId, ImportProblem.NoHunter)
    }
}
