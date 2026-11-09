package com.assismohanty.awaken.core.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.domain.HabitDraft
import com.assismohanty.awaken.core.domain.SaveHabitResult
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.HabitFrequency
import com.assismohanty.awaken.engine.model.HabitType
import java.time.DayOfWeek
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
            val quest = source.fixtureQuest(name)
            source.completeQuest(quest.id)
        }
        source.grant(LedgerKind.GOLD, 2_000, "bonus")
        source.purchaseItem("streak_shield", "buy-shield")
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
        assertThat(target.db.hunterDao().getProfile()?.injuryTags).containsExactly("upper")
    }

    @Test
    fun habitTypesRemindersAndProgressSurviveARoundTrip() = runTest {
        source.seedHunter()
        source.rollover()
        val habitId = (
            source.saveHabit(
                HabitDraft(
                    id = null, title = "Drink water", stat = Stat.VIT, difficulty = Difficulty.EASY, days = DayOfWeek.entries.toSet(),
                    target = 8, unit = "glasses", type = HabitType.QUANTITY, reminderMinutes = 540,
                ),
            ) as SaveHabitResult.Saved
            ).habitId
        val quest = source.db.questDao().habitInstanceOn(habitId, source.today())!!
        repeat(3) { source.adjustQuantity.plus(quest.id) }

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        val habit = target.db.habitDao().get(habitId)!!
        assertThat(habit.type).isEqualTo(HabitType.QUANTITY)
        assertThat(habit.frequency).isEqualTo(HabitFrequency.DAYS_OF_WEEK)
        assertThat(habit.reminderTime).isEqualTo(540)
        assertThat(target.db.questDao().get(quest.id)?.progress).isEqualTo(3)
    }

    @Test
    fun pomodoroSessionsAndFocusGoalsSurviveARoundTrip() = runTest {
        source.seedHunter()
        val start = java.time.Instant.parse("2026-11-02T08:00:00Z")
        source.db.pomodoroDao().upsert(
            com.assismohanty.awaken.core.data.db.entity.PomodoroSessionEntity(
                id = "p1", focusMin = 25, shortBreakMin = 5, longBreakMin = 15, rounds = 4, stat = Stat.INT, subjectId = null,
                startedAt = start, phase = com.assismohanty.awaken.engine.PomodoroPhase.SHORT_BREAK, currentRound = 1,
                phaseEndsAt = start.plusSeconds(300), status = com.assismohanty.awaken.engine.PomodoroStatus.RUNNING,
            ),
        )
        source.db.focusDao().upsertGoal(
            com.assismohanty.awaken.core.data.db.entity.FocusGoalEntity(java.time.LocalDate.parse("2026-11-02"), 90),
        )

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        assertThat(target.db.pomodoroDao().get("p1")).isEqualTo(source.db.pomodoroDao().get("p1"))
        assertThat(target.db.focusDao().goals()).isEqualTo(source.db.focusDao().goals())
    }

    @Test
    fun exercisePrefsAndGymSettingsSurviveARoundTrip() = runTest {
        source.seedHunter()
        source.db.gymDao().upsertExercisePref(
            com.assismohanty.awaken.core.data.db.entity.ExercisePrefEntity("bench", repMin = 5, repMax = 8, incrementKg = 5.0),
        )
        source.gym.savePrefs(
            com.assismohanty.awaken.core.domain.GymPrefs(
                unit = com.assismohanty.awaken.engine.body.PlateUnit.LB,
                kg = com.assismohanty.awaken.engine.body.PlateSetup(15.0, listOf(20.0, 10.0)),
            ),
        )

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        assertThat(target.db.gymDao().exercisePref("bench")).isEqualTo(source.db.gymDao().exercisePref("bench"))
        assertThat(target.gym.prefs()).isEqualTo(source.gym.prefs())
    }

    @Test
    fun phoneDownNightsAndTheStepGoalSurviveARoundTrip() = runTest {
        source.seedHunter()
        source.setBedtime.enableOrEdit(22 * 60 + 30, 6 * 60)
        source.updateSettings.setStepGoal(12_000)

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        assertThat(target.db.screenTimeDao().allBedtime()).isEqualTo(source.db.screenTimeDao().allBedtime())
        assertThat(target.db.screenTimeDao().allBedtime()).hasSize(1)
        assertThat(checkNotNull(target.db.hunterDao().getProfile()).stepGoal).isEqualTo(12_000)
    }

    @Test
    fun quitCountersRestartsAndDayVerdictsSurviveARoundTrip() = runTest {
        source.seedHunter()
        val id = (source.addQuitCounter(com.assismohanty.awaken.engine.QuitKind.SMOKING, "Smoking", source.clock.now().minusSeconds(86_400L * 3), 15_000, "INR", 20) as com.assismohanty.awaken.core.domain.AddQuitResult.Added).id
        source.logQuitRestart(id, trigger = "STRESS", note = "Long week")
        source.addQuitCounter(com.assismohanty.awaken.engine.QuitKind.DOOMSCROLLING, "Doomscrolling", null)
        source.recordQuitProgress(source.today().plusDays(1))

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        assertThat(target.db.quitDao().counters()).isEqualTo(source.db.quitDao().counters())
        assertThat(target.db.quitDao().relapses()).isEqualTo(source.db.quitDao().relapses())
        assertThat(target.db.quitDao().days()).isEqualTo(source.db.quitDao().days())
        assertThat(target.db.quitDao().counters()).hasSize(2)
    }

    @Test
    fun programsAndTheirMarksSurviveARoundTrip() = runTest {
        source.seedHunter()
        source.rollover()
        source.grant(com.assismohanty.awaken.engine.model.LedgerKind.XP_TOTAL, com.assismohanty.awaken.engine.LevelCurve.cumulative(10), "xp")
        source.grant(com.assismohanty.awaken.engine.model.LedgerKind.GOLD, 500, "gold")
        source.startProgram("steady_30")
        source.db.programDao().insertMark(
            com.assismohanty.awaken.core.data.db.entity.ProgramMarkEntity(
                checkNotNull(source.db.programDao().active()).id, source.today(), com.assismohanty.awaken.engine.ProgramBreakChoice.CONTINUE,
            ),
        )

        assertThat(target.porter.import(source.porter.export())).isInstanceOf(ImportResult.Imported::class.java)

        val program = checkNotNull(source.db.programDao().active())
        assertThat(target.db.programDao().active()).isEqualTo(program)
        assertThat(target.db.programDao().marks(program.id)).isEqualTo(source.db.programDao().marks(program.id))
        assertThat(target.db.programDao().habits(program.id)).isEqualTo(source.db.programDao().habits(program.id))
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
            .filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it == "android_metadata" || it in ExportDocument.CACHE_TABLES }
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
        assertRejectedAndUntouched(mutate(good) { it["schemaVersion"] = JsonPrimitive(3) }, ImportProblem.OlderVersion(3))
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

    @Test
    fun aBackupFromBeforeTheSchemaGainedPathsStillImports() = runTest {
        playSomeGame()
        val v5 = source.porter.export()
        val pathTables = setOf("path", "path_milestone", "path_week", "path_slot", "ai_report")
        // What a v4 app wrote: no Path tables, no path_slot_id column on quests.
        val v4 = mutate(v5) { root ->
            root["schemaVersion"] = JsonPrimitive(4)
            root["tables"] = JsonObject(
                root.getValue("tables").jsonObject.filterKeys { it !in pathTables }.mapValues { (table, rows) ->
                    if (table != "quest_instance") rows else JsonArray(rows.jsonArray.map { JsonObject(it.jsonObject - "path_slot_id") })
                },
            )
        }

        val result = target.porter.import(v4)

        assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(target.pathDao.all()).isEmpty()
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(v5))
    }

    /**
     * The declutter dropped achievements, cosmetics, campaigns, dungeons and recaps (schema v26). A backup written before that
     * still imports: the removed tables and columns are ignored, and the ledger comes through untouched.
     */
    @Test
    fun aBackupFromBeforeTheDeclutterStillImports() = runTest {
        playSomeGame()
        val current = source.porter.export()
        val v25 = mutate(current) { root ->
            root["schemaVersion"] = JsonPrimitive(25)
            val tables = root.getValue("tables").jsonObject.toMutableMap()
            tables["achievement_unlock"] = JsonArray(listOf(JsonObject(mapOf("achievement_id" to JsonPrimitive("first_blood"), "unlocked_at" to JsonPrimitive(0)))))
            tables["owned_cosmetic"] = JsonArray(listOf(JsonObject(mapOf("cosmetic_id" to JsonPrimitive("sigil_blade"), "acquired_at" to JsonPrimitive(0), "source" to JsonPrimitive("SHOP")))))
            tables["campaign"] = JsonArray(emptyList())
            tables["dungeon"] = JsonArray(emptyList())
            tables["recap"] = JsonArray(listOf(JsonObject(mapOf("period_key" to JsonPrimitive("M:2026-09"), "generated_at" to JsonPrimitive(0), "payload_version" to JsonPrimitive(1), "payload_json" to JsonPrimitive("{}")))))
            fun JsonObject.plus(vararg extra: Pair<String, kotlinx.serialization.json.JsonElement>) = JsonObject(this + extra)
            tables["hunter_profile"] = JsonArray(
                tables.getValue("hunter_profile").jsonArray.map {
                    it.jsonObject.plus(
                        "equipped_title_id" to JsonPrimitive("veteran"), "theme_id" to JsonPrimitive("default"),
                        "equipped_frame_id" to kotlinx.serialization.json.JsonNull, "equipped_sigil_id" to kotlinx.serialization.json.JsonNull,
                    )
                },
            )
            tables["habit"] = JsonArray(tables.getValue("habit").jsonArray.map { it.jsonObject.plus("campaign_id" to kotlinx.serialization.json.JsonNull) })
            tables["quest_instance"] = JsonArray(
                tables.getValue("quest_instance").jsonArray.map {
                    it.jsonObject.plus("dungeon_id" to kotlinx.serialization.json.JsonNull, "dungeon_stage" to kotlinx.serialization.json.JsonNull)
                },
            )
            root["tables"] = JsonObject(tables)
        }

        assertThat(target.porter.import(v25)).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(target.db.ledgerDao().all().size).isEqualTo(source.db.ledgerDao().all().size)
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(current))
    }

    @Test
    fun aBackupFromBeforeGymAndDietStillImports() = runTest {
        playSomeGame()
        val current = source.porter.export()
        val bodyTables = setOf(
            "custom_exercise", "routine", "routine_exercise", "workout", "workout_set", "custom_food", "food_log", "nutrition_profile",
        )
        // What a v8 app wrote: none of the tracker tables.
        val v8 = mutate(current) { root ->
            root["schemaVersion"] = JsonPrimitive(8)
            root["tables"] = JsonObject(root.getValue("tables").jsonObject.filterKeys { it !in bodyTables })
        }

        assertThat(target.porter.import(v8)).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(current))
    }

    @Test
    fun aBackupFromBeforeTheGymRedesignStillImports() = runTest {
        playSomeGame()
        val current = source.porter.export()
        val newColumns = mapOf(
            "routine" to setOf("sort_order", "source_plan_id"),
            "routine_exercise" to setOf("rep_min", "rep_max", "superset", "unilateral", "section", "measure"),
            "workout" to setOf("routine_id", "mode"),
            "workout_set" to setOf("completed_at", "section", "duration_sec", "side", "superset", "rep_min", "rep_max"),
            "gym_settings" to setOf(
                "fitness_level", "goal", "equipment", "injuries", "sex", "rest_compound_sec", "rest_isolation_sec", "rest_sound", "rest_vibrate", "coached",
            ),
        )
        // What a v28 app wrote: no week_slot or day_override table, and none of the new gym columns.
        val v28 = mutate(current) { root ->
            root["schemaVersion"] = JsonPrimitive(28)
            root["tables"] = JsonObject(
                root.getValue("tables").jsonObject.filterKeys { it != "week_slot" && it != "day_override" }.mapValues { (table, rows) ->
                    val dropped = newColumns[table]
                    if (dropped == null) rows else JsonArray(rows.jsonArray.map { JsonObject(it.jsonObject.filterKeys { key -> key !in dropped }) })
                },
            )
        }

        assertThat(target.porter.import(v28)).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(current))
    }

    @Test
    fun aBackupFromBeforeCampaignsStillImports() = runTest {
        playSomeGame()
        val current = source.porter.export()
        // What a v7 app wrote: no campaign table, no campaign columns on habits.
        val v7 = mutate(current) { root ->
            root["schemaVersion"] = JsonPrimitive(7)
            root["tables"] = JsonObject(
                root.getValue("tables").jsonObject.filterKeys { it != "campaign" }.mapValues { (table, rows) ->
                    if (table != "habit") rows else JsonArray(rows.jsonArray.map { JsonObject(it.jsonObject - "campaign_id" - "template_id") })
                },
            )
        }

        assertThat(target.porter.import(v7)).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(current))
    }

    @Test
    fun aBackupFromBeforeScreenDisciplineStillImports() = runTest {
        playSomeGame()
        val current = source.porter.export()
        // What a v6 app wrote: no app_rule table, no screen_time_from column on the profile.
        val v6 = mutate(current) { root ->
            root["schemaVersion"] = JsonPrimitive(6)
            root["tables"] = JsonObject(
                root.getValue("tables").jsonObject.filterKeys { it != "app_rule" && it != "campaign" }.mapValues { (table, rows) ->
                    when (table) {
                        "hunter_profile" -> JsonArray(rows.jsonArray.map { JsonObject(it.jsonObject - "screen_time_from") })
                        "habit" -> JsonArray(rows.jsonArray.map { JsonObject(it.jsonObject - "campaign_id" - "template_id") })
                        else -> rows
                    }
                },
            )
        }

        val result = target.porter.import(v6)

        assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(target.ledger.balances()).isEqualTo(source.ledger.balances())
        assertThat(target.db.hunterDao().getProfile()?.screenTimeFrom).isNull()
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(current))
    }

    @Test
    fun pathsRoundTripThroughExportAndImport() = runTest {
        source.seedHunter()
        source.grantConsent()
        val created = source.createPath(
            com.assismohanty.awaken.engine.path.PathInput(
                "Learn to juggle", "", "", Stat.STR, null, setOf(java.time.DayOfWeek.MONDAY), 1, 60,
            ),
        ) as com.assismohanty.awaken.core.domain.CreatePathResult.Created
        source.runPathRequest(
            com.assismohanty.awaken.core.domain.PathJob(created.pathId, com.assismohanty.awaken.core.domain.PathRequestMode.PLAN_THEN_WEEK, "r"),
            attempt = 0,
        )
        val exported = source.porter.export()

        assertThat(target.porter.import(exported)).isInstanceOf(ImportResult.Imported::class.java)

        assertThat(target.pathDao.all()).isEqualTo(source.pathDao.all())
        assertThat(target.pathDao.currentMilestones(created.pathId)).isEqualTo(source.pathDao.currentMilestones(created.pathId))
        assertThat(tablesOf(target.porter.export())).isEqualTo(tablesOf(exported))
    }
}
