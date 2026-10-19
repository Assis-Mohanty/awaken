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
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(4)
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
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(4)
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
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(4)
        } finally {
            db.close()
        }
    }
}
