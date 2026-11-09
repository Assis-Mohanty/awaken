package com.assismohanty.awaken.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.ALL_MIGRATIONS
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.engine.model.Stat
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
    fun v2To3KeepsTheProfile() = runTest {
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
    fun v7To8KeepsHabitsPlain() = runTest {
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
            assertThat(habit?.templateId).isNull()
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
    fun v13To14AddsHealthDistance() = runTest {
        val path = createFromSchema(13)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO health_day_cache (game_date, steps, sleep_minutes, read_at) VALUES ('2026-10-01', 9000, 420, 0)")
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
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
    fun v22To23AddsTheQuitTablesEmpty() = runTest {
        val path = createFromSchema(22)
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.quitDao().counters()).isEmpty()
            assertThat(db.quitDao().relapses()).isEmpty()
            assertThat(db.quitDao().days()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v23To24AddsProgramsAndKeepsQuestsAndHabits() = runTest {
        val path = createFromSchema(23)
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
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.habitDao().get("h1")?.programId).isNull()
            assertThat(db.questDao().get("q1")?.title).isEqualTo("Read")
            assertThat(db.programDao().active()).isNull()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    @Test
    fun v24To25AddsTheNightVerdictAndKeepsAnExpiredNightUnknown() = runTest {
        val path = createFromSchema(24)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO quest_instance (id, game_date, type, stat, title, description, unit, target, difficulty, " +
                    "verification, min_duration_sec, status, created_at) " +
                    "VALUES ('n1', '2026-10-18', 'BEDTIME', 'VIT', 'Phone down', 'Phone down', 'night', 1, 'NORMAL', 'DEVICE_USAGE', 0, 'EXPIRED', 0)",
            )
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(db.questDao().get("n1")?.status).isEqualTo(com.assismohanty.awaken.engine.model.QuestStatus.EXPIRED)
            assertThat(db.questDao().get("n1")?.nightVerdict).isNull()
            db.questDao().expireNight("n1", com.assismohanty.awaken.engine.BedtimeVerdict.NO_DATA)
            assertThat(db.questDao().get("n1")?.nightVerdict).isEqualTo(com.assismohanty.awaken.engine.BedtimeVerdict.NO_DATA)
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    /**
     * The declutter migration drops achievements, cosmetics, campaigns, dungeons and recaps and rebuilds three tables, but it
     * never touches the ledger: every row, and every balance derived from it, comes through unchanged.
     */
    @Test
    fun v25To26DropsTheRemovedFeaturesAndKeepsTheLedgerAndTheRowsThatSurvive() = runTest {
        val path = createFromSchema(25)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, equipped_title_id, theme_id, equipped_frame_id, equipped_sigil_id, onboarding_done, " +
                    "owned_equipment, injury_mode, health_enabled, identities, step_goal) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 'veteran', 'theme_crimson_gate', 'frame_iron', " +
                    "'sigil_blade', 1, '', 0, 0, 'trains,reads', 9000)",
            )
            db.execSQL(
                "INSERT INTO habit (id, title, stat, difficulty, verification, days_of_week_mask, target, unit, active, created_at, campaign_id) " +
                    "VALUES ('h1', 'Read', 'INT', 'EASY', 'SELF', 127, 10, 'pages', 1, 0, 'c1')",
            )
            db.execSQL(
                "INSERT INTO quest_instance (id, game_date, type, habit_id, stat, title, description, unit, target, difficulty, " +
                    "verification, min_duration_sec, status, created_at, dungeon_id, dungeon_stage) " +
                    "VALUES ('q1', '2026-10-18', 'DUNGEON', NULL, 'INT', 'Stage', 'Stage', 'pages', 10, 'HARD', 'SELF', 0, 'ACTIVE', 0, 'd1', 2)",
            )
            listOf(
                "('XP_TOTAL', '', '', 120, 'QUEST', 'q1#1', '2026-10-18', 'SELF')",
                "('GOLD', '', '', 60, 'QUEST', 'q1#1', '2026-10-18', 'SELF')",
                "('GOLD', '', '', 50, 'ACHIEVEMENT', 'never_twice', '2026-10-18', '')",
                "('FRAGMENT', '', '', 3, 'QUEST', 'q1#1', '2026-10-18', 'SELF')",
                "('ITEM', '', 'sigil_blade', 1, 'SHOP', 'p1', '2026-10-18', '')",
            ).forEach { values ->
                db.execSQL(
                    "INSERT INTO ledger_entry (kind, stat, item_id, delta, source_type, source_id, game_date, xp_source, created_at) " +
                        "VALUES " + values.dropLast(1) + ", 0)",
                )
            }
            db.execSQL("INSERT INTO balance_cache (key, value) VALUES ('fragments', 3)")
            db.execSQL("INSERT INTO achievement_unlock (achievement_id, unlocked_at) VALUES ('never_twice', 0)")
            db.execSQL("INSERT INTO owned_cosmetic (cosmetic_id, acquired_at, source) VALUES ('sigil_blade', 0, 'SHOP')")
            db.execSQL(
                "INSERT INTO campaign (id, start_date, focus_stat, archetype, scores, oath_sealed_at, status, current_act, cleared_days) " +
                    "VALUES ('c1', '2026-10-01', 'INT', 'SCHOLAR', 'STR:3', 0, 'ACTIVE', 'FOUNDATION', 4)",
            )
            db.execSQL(
                "INSERT INTO dungeon (id, length_days, stat, fee, entered_game_date, first_stage_date, status) " +
                    "VALUES ('d1', 3, 'INT', 100, '2026-10-17', '2026-10-18', 'ACTIVE')",
            )
            db.execSQL("INSERT INTO recap (period_key, generated_at, payload_version, payload_json) VALUES ('M:2026-09', 0, 1, '{}')")
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // The ledger is intact: same rows, same sums, removed kinds included.
            val rows = db.ledgerDao().all()
            assertThat(rows).hasSize(5)
            assertThat(rows.filter { it.kind == com.assismohanty.awaken.engine.model.LedgerKind.GOLD }.sumOf { it.delta }).isEqualTo(110)
            assertThat(rows.single { it.sourceId == "never_twice" }.delta).isEqualTo(50)
            assertThat(rows.single { it.kind == com.assismohanty.awaken.engine.model.LedgerKind.FRAGMENT }.delta).isEqualTo(3)
            // The rows of the rebuilt tables survive.
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.name).isEqualTo("Assis")
            assertThat(profile?.stepGoal).isEqualTo(9000)
            assertThat(db.habitDao().get("h1")?.title).isEqualTo("Read")
            assertThat(db.questDao().get("q1")?.title).isEqualTo("Stage")
            // The removed tables are gone and the fragment balance cache entry with them.
            val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertThat(tables).containsNoneOf("achievement_unlock", "owned_cosmetic", "campaign", "dungeon", "recap")
            assertThat(db.ledgerDao().balances().none { it.key == "fragments" }).isTrue()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }

    /**
     * v26 -> v27 drops the identities column by rebuilding hunter_profile. The profile row, its other columns and the ledger
     * come through unchanged.
     */
    @Test
    fun v26To27DropsIdentitiesAndKeepsTheProfileAndTheLedger() = runTest {
        val path = createFromSchema(26)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO hunter_profile (id, hunter_id, name, created_at, first_game_date, focus_stat_1, focus_stat_2, " +
                    "day_start_hour, onboarding_done, owned_equipment, injury_mode, health_enabled, identities, step_goal) " +
                    "VALUES ('me', 'h1', 'Assis', 0, '2026-10-18', 'STR', 'INT', 4, 1, 'dumbbells', 0, 1, 'trains,reads', 9000)",
            )
            db.execSQL(
                "INSERT INTO ledger_entry (kind, stat, item_id, delta, source_type, source_id, game_date, xp_source, created_at) " +
                    "VALUES ('GOLD', '', '', 60, 'QUEST', 'q1#1', '2026-10-18', 'SELF', 0)",
            )
        }

        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.hunterDao().getProfile()
            assertThat(profile?.name).isEqualTo("Assis")
            assertThat(profile?.focusStats).containsExactly(Stat.STR, Stat.INT).inOrder()
            assertThat(profile?.stepGoal).isEqualTo(9000)
            assertThat(profile?.healthEnabled).isTrue()
            assertThat(profile?.ownedEquipment).containsExactly("dumbbells")
            assertThat(db.ledgerDao().all()).hasSize(1)
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(hunter_profile)").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertThat(columns).doesNotContain("identities")
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

    /**
     * v28 -> v29 (Gym redesign, GD section 18) only adds columns and two tables. A finished workout with its sets, a routine,
     * and the plate settings come through untouched, the old sets get no completion time (so they never pay XP), and every
     * new column reads its default.
     */
    @Test
    fun v28To29KeepsGymHistoryAndAddsTheNewColumnsAtTheirDefaults() = runTest {
        val path = createFromSchema(28)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO routine (id, name, updated_at) VALUES ('r1', 'Push', 5)")
            db.execSQL("INSERT INTO routine_exercise (routine_id, position, exercise_id, set_count, rest_sec) VALUES ('r1', 0, 'bench', 3, 90)")
            db.execSQL(
                "INSERT INTO workout (id, name, game_date, started_at, ended_at, rest_ends_at, rest_total_sec) " +
                    "VALUES ('w1', 'Push', '2026-10-18', 0, 1000, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_set (id, workout_id, exercise_id, exercise_name, primary_muscles, secondary_muscles, position, set_index, weight_kg, reps, done, rest_sec) " +
                    "VALUES ('s1', 'w1', 'bench', 'Bench press', 'chest', 'triceps', 0, 0, 60.0, 8, 1, 90)",
            )
            db.execSQL("INSERT INTO gym_settings (id, bar_kg, plates_kg, bar_lb, plates_lb, unit, rep_min, rep_max) VALUES (1, 20.0, '25,20', 45.0, '45,35', 'LB', 6, 10)")
        }
        val db = Room.databaseBuilder(context, AwakenDatabase::class.java, path)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.gymDao()
            val workout = dao.workout("w1")
            assertThat(workout?.name).isEqualTo("Push")
            assertThat(workout?.routineId).isNull()
            assertThat(workout?.mode).isEqualTo("LIST")
            val set = dao.sets("w1").single()
            assertThat(set.weightKg).isEqualTo(60.0)
            assertThat(set.reps).isEqualTo(8)
            assertThat(set.done).isTrue()
            assertThat(set.completedAt).isNull()
            assertThat(set.section).isEqualTo("WORK")
            assertThat(set.durationSec).isNull()
            assertThat(dao.previousSets("bench")).hasSize(1)
            val routine = dao.routineExercises("r1").single()
            assertThat(routine.setCount).isEqualTo(3)
            assertThat(routine.restSec).isEqualTo(90)
            assertThat(routine.section).isEqualTo("WORK")
            assertThat(routine.unilateral).isFalse()
            val settings = dao.gymSettings()
            assertThat(settings?.unit).isEqualTo("LB")
            assertThat(settings?.repMin).isEqualTo(6)
            assertThat(settings?.fitnessLevel).isNull()
            assertThat(settings?.equipment).isNull()
            assertThat(settings?.restCompoundSec).isEqualTo(120)
            assertThat(settings?.restIsolationSec).isEqualTo(60)
            assertThat(settings?.restSound).isTrue()
            assertThat(settings?.coached).isTrue()
            assertThat(dao.weekSlots()).isEmpty()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(AwakenDatabase.SCHEMA_VERSION)
        } finally {
            db.close()
        }
    }
}
