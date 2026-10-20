package com.assismohanty.awaken.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (Phase 8, Focus Gates): `gate_run` gains its quest link, pause, open-stop, boot and collapse columns.
 * Nothing wrote `gate_run` before v2, so the table is rebuilt; every other table is untouched.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `gate_run`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gate_run` (`id` TEXT NOT NULL, `quest_instance_id` TEXT NOT NULL, " +
                "`gate_rank` TEXT NOT NULL, `stat` TEXT NOT NULL, `label` TEXT, `planned_sec` INTEGER NOT NULL, " +
                "`started_at` INTEGER NOT NULL, `ended_at` INTEGER, `background_ms` INTEGER NOT NULL, `paused_at` INTEGER, " +
                "`resumed_at` INTEGER, `last_stop_at` INTEGER, `last_stop_elapsed_ms` INTEGER, `boot_count` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `collapse_reason` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_run_status` ON `gate_run` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_run_quest_instance_id` ON `gate_run` (`quest_instance_id`)")
    }
}

/**
 * v2 → v3 (Phase 10, Vault): the hunter profile gains the equipped title frame and hunter sigil. Both are nullable, so
 * existing rows need no backfill and nothing is rebuilt.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `equipped_frame_id` TEXT")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `equipped_sigil_id` TEXT")
    }
}

/**
 * v3 → v4 (Phase 10, Settings): Injury Mode can exclude a chosen set of body-area tags. Existing rows get the previous
 * fixed rule (impact + legs), so nothing changes for anyone who already had it on.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `injury_tags` TEXT NOT NULL DEFAULT 'impact,legs'")
    }
}

/**
 * v4 → v5 (Phase 15, AI Paths): the five Path tables and `quest_instance.path_slot_id`. Purely additive, so every
 * existing row is untouched; the nullable column needs no backfill.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `path_slot_id` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_instance_path_slot_id` ON `quest_instance` (`path_slot_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `path` (`id` TEXT NOT NULL, `goal` TEXT NOT NULL, `current_level` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `primary_stat` TEXT NOT NULL, `secondary_stat` TEXT, `active_days_mask` INTEGER NOT NULL, " +
                "`quests_per_day` INTEGER NOT NULL, `weekly_minutes` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`current_milestone_index` INTEGER NOT NULL, `milestone_started_game_date` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `created_game_date` TEXT NOT NULL, `last_replan_iso_week` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `path_milestone` (`path_id` TEXT NOT NULL, `idx` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                "`summary` TEXT NOT NULL, `exit_criterion` TEXT NOT NULL, `plan_version` INTEGER NOT NULL, " +
                "PRIMARY KEY(`path_id`, `plan_version`, `idx`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `path_week` (`path_id` TEXT NOT NULL, `iso_week` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`progression_signal` TEXT NOT NULL, `prompt_version` TEXT NOT NULL, `response_sha256` TEXT NOT NULL, " +
                "`requested_at` INTEGER NOT NULL, `received_at` INTEGER, `manual_retries` INTEGER NOT NULL, " +
                "`suggest_advance` INTEGER NOT NULL, PRIMARY KEY(`path_id`, `iso_week`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `path_slot` (`id` TEXT NOT NULL, `path_id` TEXT NOT NULL, `iso_week` TEXT NOT NULL, " +
                "`blueprint_index` INTEGER NOT NULL, `assigned_game_date` TEXT, `carried_over` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `stat` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                "`target` INTEGER NOT NULL, `est_minutes` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                "`priced_difficulty` TEXT NOT NULL, `priced_verification` TEXT NOT NULL, `gate_rank` TEXT, " +
                "`min_duration_sec` INTEGER NOT NULL, `tags_json` TEXT NOT NULL, `rationale` TEXT NOT NULL, " +
                "`outcome` TEXT NOT NULL, `feedback` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_path_slot_path_id_iso_week_blueprint_index` " +
                "ON `path_slot` (`path_id`, `iso_week`, `blueprint_index`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_path_slot_assigned_game_date` ON `path_slot` (`assigned_game_date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_report` (`id` TEXT NOT NULL, `path_slot_id` TEXT NOT NULL, `reason` TEXT NOT NULL, " +
                "`prompt_version` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `sent_at` INTEGER, PRIMARY KEY(`id`))",
        )
    }
}

/** Every migration, in order. The database builder and the migration test both use this list. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
