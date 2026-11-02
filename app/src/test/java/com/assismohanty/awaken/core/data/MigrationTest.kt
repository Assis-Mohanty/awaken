package com.assismohanty.awaken.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.ALL_MIGRATIONS
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every exported schema migrates forward with the hunter's data intact (ARCH §2: no destructive fallback).
 * The old database is built straight from its exported schema JSON; opening it with the current Room version runs the
 * migrations, and Room validates every table against the current schema (a mismatch throws).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Creates [version]'s database exactly as Room would have, from `schemas/<db>/<version>.json`. */
    private fun createFromSchema(version: Int): String {
        val path = context.getDatabasePath("migration-$version.db").apply { parentFile?.mkdirs(); delete() }.absolutePath
        val schema = context.assets.open("${AwakenDatabase::class.java.name}/$version.json").bufferedReader().use { it.readText() }
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
        }
        return path
    }

    @Test
    fun v1To2KeepsLedgerAndRebuildsGateRun() = runTest {
        val path = createFromSchema(1)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO ledger_entry (kind, stat, item_id, delta, source_type, source_id, game_date, xp_source, created_at) " +
                    "VALUES ('XP_TOTAL', '', '', 120, 'QUEST', 'q1#1', '2026-10-18', 'SELF', 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.ledgerDao().all().sumOf { it.delta }).isEqualTo(120)
            assertThat(db.gateRunDao().running()).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v2To3AddsTheEquippedFrameAndSigilWithoutTouchingTheProfile() = runTest {
        val path = createFromSchema(2)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, equipped_title_id, theme_id, onboarding_done, owned_equipment, injury_mode, health_enabled) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'veteran', 'default', 1, '', 0, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.name).isEqualTo("Assis")
            assertThat(profile?.equippedTitleId).isEqualTo("veteran")
            assertThat(profile?.equippedFrameId).isNull()
            assertThat(profile?.equippedSigilId).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v3To4GivesExistingHuntersTheOldInjuryRule() = runTest {
        val path = createFromSchema(3)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, theme_id, onboarding_done, owned_equipment, injury_mode, health_enabled) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'default', 1, 'dumbbells', 1, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.injuryMode).isTrue()
            assertThat(profile?.injuryTags).containsExactly("impact", "legs")
            assertThat(profile?.ownedEquipment).containsExactly("dumbbells")
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v5To6GivesExistingQuestsAnEmptySetCounter() = runTest {
        val path = createFromSchema(5)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO quest_instance (id, game_date, type, stat, title, description, unit, target, difficulty, " +
                    "verification, min_duration_sec, status, created_at) " +
                    "VALUES ('q1', '2026-10-18', 'SIDE', 'STR', 'Push-ups', 'Do them', 'reps', 20, 'NORMAL', 'SELF', 0, 'ACTIVE', 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val quest = db.questDao().get("q1")
            assertThat(quest?.title).isEqualTo("Push-ups")
            assertThat(quest?.setReps).isEmpty()
            assertThat(db.questDao().updateSetReps("q1", "10,8")).isEqualTo(1)
            assertThat(db.questDao().get("q1")?.setReps).isEqualTo("10,8")
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v6To7LeavesScreenDisciplineOffAndAddsAnEmptyRuleTable() = runTest {
        val path = createFromSchema(6)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, theme_id, onboarding_done, owned_equipment, injury_mode, injury_tags, health_enabled) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'default', 1, '', 0, 'impact,legs', 1)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.name).isEqualTo("Assis")
            assertThat(profile?.healthEnabled).isTrue()
            assertThat(profile?.screenTimeFrom).isNull()
            assertThat(db.screenTimeDao().allRules()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v7To8KeepsHabitsPlainAndAddsAnEmptyCampaignTable() = runTest {
        val path = createFromSchema(7)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO habit (id, title, stat, difficulty, verification, days_of_week_mask, target, unit, active, created_at) " +
                    "VALUES ('h1', 'Read', 'INT', 'NORMAL', 'SELF', 127, 20, 'pages', 1, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val habit = db.habitDao().get("h1")
            assertThat(habit?.title).isEqualTo("Read")
            assertThat(habit?.campaignId).isNull()
            assertThat(habit?.templateId).isNull()
            assertThat(db.campaignDao().active()).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v8To9AddsEmptyGymAndDietTablesAndKeepsHabits() = runTest {
        val path = createFromSchema(8)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO habit (id, title, stat, difficulty, verification, days_of_week_mask, target, unit, active, created_at) " +
                    "VALUES ('h1', 'Read', 'INT', 'NORMAL', 'SELF', 127, 20, 'pages', 1, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.habitDao().get("h1")?.title).isEqualTo("Read")
            assertThat(db.gymDao().activeWorkout()).isNull()
            assertThat(db.gymDao().customExercises()).isEmpty()
            assertThat(db.dietDao().profile()).isNull()
            assertThat(db.dietDao().customFoods()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v9To10MakesExistingQuestsAndHabitsAnytime() = runTest {
        val path = createFromSchema(9)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO habit (id, title, stat, difficulty, verification, days_of_week_mask, target, unit, active, created_at) " +
                    "VALUES ('h1', 'Read', 'INT', 'NORMAL', 'SELF', 127, 20, 'pages', 1, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val habit = db.habitDao().get("h1")
            assertThat(habit?.title).isEqualTo("Read")
            assertThat(habit?.timeOfDay).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v10To11GivesExistingHuntersNoIdentities() = runTest {
        val path = createFromSchema(10)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, theme_id, onboarding_done, owned_equipment, injury_mode, health_enabled) " +
                    "VALUES ('me', 'h', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'default', 1, '', 0, 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.name).isEqualTo("Assis")
            assertThat(profile?.identityIds).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v11To12AddsAnEmptyHealthCache() = runTest {
        val path = createFromSchema(11)
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.insightDao().healthDays(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-12-31"))).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v12To13AddsAnEmptyWeeklyReviewTable() = runTest {
        val path = createFromSchema(12)
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.weeklyReviewDao().count()).isEqualTo(0)
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v13To14AddsRecapsAndHealthDistance() = runTest {
        val path = createFromSchema(13)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO health_day_cache (game_date, steps, sleep_minutes, read_at) VALUES ('2026-10-01', 9000, 420, 0)")
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.recapDao().keys()).isEmpty()
            val cached = db.insightDao().healthDays(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-12-31")).single()
            assertThat(cached.steps).isEqualTo(9000L)
            assertThat(cached.distanceMeters).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v14To15AddsGateModeAndFocusSubjects() = runTest {
        val path = createFromSchema(14)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO gate_run (id, quest_instance_id, gate_rank, stat, label, planned_sec, started_at, background_ms, boot_count, status) " +
                    "VALUES ('g1', 'q1', 'D', 'INT', NULL, 1500, 0, 0, 1, 'CLEARED')",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val run = db.gateRunDao().get("g1")
            assertThat(run?.mode).isEqualTo(com.assismohanty.awaken.engine.GateMode.PRESET)
            assertThat(run?.subjectId).isNull()
            assertThat(db.focusDao().activeCount()).isEqualTo(0)
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v15To16AddsRestColumnsAndKeepsWorkouts() = runTest {
        val path = createFromSchema(15)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO workout (id, name, game_date, started_at) VALUES ('w1', 'Push', '2026-10-01', 0)")
            db.execSQL("INSERT INTO routine (id, name, updated_at) VALUES ('r1', 'Push day', 0)")
            db.execSQL("INSERT INTO routine_exercise (routine_id, position, exercise_id, set_count) VALUES ('r1', 0, 'bench', 3)")
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val workout = db.gymDao().workout("w1")
            assertThat(workout?.name).isEqualTo("Push")
            assertThat(workout?.restEndsAt).isNull()
            assertThat(db.gymDao().routineExercises("r1").single().restSec).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v16To17AddsFoldedPauseTimeDefaultingToZero() = runTest {
        val path = createFromSchema(16)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO gate_run (id, quest_instance_id, gate_rank, stat, label, planned_sec, started_at, background_ms, boot_count, status, mode) " +
                    "VALUES ('g1', 'q1', 'D', 'INT', NULL, 1500, 0, 0, 1, 'CLEARED', 'PRESET')",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.gateRunDao().get("g1")?.pausedBeforeMs).isEqualTo(0L)
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }


    @Test
    fun v17To18AddsLockAtStopDefaultingToFalse() = runTest {
        val path = createFromSchema(17)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO gate_run (id, quest_instance_id, gate_rank, stat, label, planned_sec, started_at, background_ms, boot_count, status, mode, paused_before_ms) " +
                    "VALUES ('g1', 'q1', 'D', 'INT', NULL, 1500, 0, 0, 1, 'RUNNING', 'PRESET', 0)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.gateRunDao().get("g1")?.lockAtStop).isFalse()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }
    @Test
    fun v18To19AddsHabitTypesDefaultingToTheOldBehaviour() = runTest {
        val path = createFromSchema(18)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO habit (id, title, stat, difficulty, verification, days_of_week_mask, target, unit, active, created_at) " +
                    "VALUES ('h1', 'Read', 'INT', 'EASY', 'SELF', 127, 10, 'pages', 1, 0)",
            )
            db.execSQL(
                "INSERT INTO quest_instance (id, game_date, type, habit_id, stat, title, description, unit, target, difficulty, " +
                    "verification, min_duration_sec, status, created_at) " +
                    "VALUES ('q1', '2026-10-18', 'HABIT', 'h1', 'INT', 'Read', 'Read', 'pages', 10, 'EASY', 'SELF', 0, 'ACTIVE', 0)",
            )
            db.execSQL(
                "INSERT INTO gate_run (id, quest_instance_id, gate_rank, stat, label, planned_sec, started_at, background_ms, boot_count, status, mode, paused_before_ms, lock_at_stop) " +
                    "VALUES ('g1', 'q1', 'D', 'INT', NULL, 1500, 0, 0, 1, 'CLEARED', 'PRESET', 0, 0)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val habit = db.habitDao().get("h1")
            assertThat(habit?.type).isEqualTo(com.assismohanty.awaken.engine.model.HabitType.CHECK)
            assertThat(habit?.frequency).isEqualTo(com.assismohanty.awaken.engine.model.HabitFrequency.DAYS_OF_WEEK)
            assertThat(habit?.timesPerWeek).isNull()
            assertThat(habit?.reminderTime).isNull()
            assertThat(db.questDao().get("q1")?.progress).isEqualTo(0)
            assertThat(db.gateRunDao().get("g1")?.habitQuestId).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v19To20AddsPomodoroAndFocusGoalAndKeepsGateRuns() = runTest {
        val path = createFromSchema(19)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO gate_run (id, quest_instance_id, gate_rank, stat, label, planned_sec, started_at, background_ms, boot_count, status, mode, paused_before_ms, lock_at_stop) " +
                    "VALUES ('g1', 'q1', 'D', 'INT', NULL, 1500, 0, 0, 1, 'CLEARED', 'PRESET', 0, 0)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val run = db.gateRunDao().get("g1")
            assertThat(run?.pomodoroId).isNull()
            assertThat(run?.pomodoroRound).isNull()
            assertThat(db.pomodoroDao().running()).isNull()
            assertThat(db.focusDao().goals()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v20To21AddsTheGymProgressTablesAndKeepsWorkouts() = runTest {
        val path = createFromSchema(20)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO workout (id, name, game_date, started_at, ended_at, rest_ends_at, rest_total_sec) " +
                    "VALUES ('w1', 'Push', '2026-10-18', 0, 1000, NULL, NULL)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.gymDao().workout("w1")?.name).isEqualTo("Push")
            assertThat(db.gymDao().exercisePref("bench")).isNull()
            assertThat(db.gymDao().gymSettings()).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v21To22AddsTheStepGoalAtEightThousandAndAnEmptyBedtimeTable() = runTest {
        val path = createFromSchema(21)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, day_start_hour, theme_id, onboarding_done, owned_equipment, injury_mode, health_enabled) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'default', 1, '', 0, 0)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.hunterDao().getProfile()?.stepGoal).isEqualTo(8_000)
            assertThat(db.screenTimeDao().allBedtime()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v4To5AddsThePathTablesAndKeepsExistingQuests() = runTest {
        val path = createFromSchema(4)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO quest_instance (id, game_date, type, stat, title, description, unit, target, difficulty, " +
                    "verification, min_duration_sec, status, created_at) " +
                    "VALUES ('q1', '2026-10-18', 'SIDE', 'STR', 'Push-ups', 'Do them', 'reps', 20, 'NORMAL', 'SELF', 0, 'ACTIVE', 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val quest = db.questDao().get("q1")
            assertThat(quest?.title).isEqualTo("Push-ups")
            assertThat(quest?.pathSlotId).isNull()
            assertThat(db.pathDao().all()).isEmpty()
            assertThat(db.pathDao().unsentReports()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }
}
