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

/**
 * v5 → v6: the set counter. One text column on the quest holds the reps of each set ("10,8,6"); existing quests start with
 * no sets, so nothing is backfilled and no existing row changes meaning.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `set_reps` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v6 → v7: Screen Discipline (GD §6.7). The per-app rule table and the profile's start date. Purely additive: the
 * nullable column means "off" for every existing hunter, and the new table starts empty.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `screen_time_from` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_rule` (`package_name` TEXT NOT NULL, `effective_from` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `daily_limit_minutes` INTEGER, PRIMARY KEY(`package_name`, `effective_from`))",
        )
    }
}

/**
 * v7 → v8: the 45-day Campaign (GD §6.8). A new table, and two nullable habit columns that mark campaign habits. Every
 * existing habit stays a plain custom habit.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `campaign_id` TEXT")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `template_id` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `campaign` (`id` TEXT NOT NULL, `start_date` TEXT NOT NULL, `focus_stat` TEXT NOT NULL, " +
                "`archetype` TEXT NOT NULL, `scores` TEXT NOT NULL, `oath_sealed_at` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`current_act` TEXT NOT NULL, `cleared_days` INTEGER NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v8 → v9: Gym and Diet (GD §13). Eight new tables, all starting empty; no existing table changes. Trackers only, so
 * none of them is a ledger table.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_exercise` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `equipment` TEXT NOT NULL, " +
                "`primary_muscles` TEXT NOT NULL, `secondary_muscles` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_exercise` (`routine_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`exercise_id` TEXT NOT NULL, `set_count` INTEGER NOT NULL, PRIMARY KEY(`routine_id`, `position`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `game_date` TEXT NOT NULL, " +
                "`started_at` INTEGER NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_game_date` ON `workout` (`game_date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_set` (`id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "`exercise_name` TEXT NOT NULL, `primary_muscles` TEXT NOT NULL, `secondary_muscles` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `set_index` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                "`done` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_workout_id` ON `workout_set` (`workout_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_exercise_id` ON `workout_set` (`exercise_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_food` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `nutrients_json` TEXT NOT NULL, " +
                "`portions_json` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_log` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `meal` TEXT NOT NULL, " +
                "`food_id` TEXT NOT NULL, `name` TEXT NOT NULL, `grams` REAL NOT NULL, `portion_label` TEXT, " +
                "`nutrients_json` TEXT NOT NULL, `logged_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_game_date` ON `food_log` (`game_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_food_id` ON `food_log` (`food_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nutrition_profile` (`id` INTEGER NOT NULL, `sex` TEXT NOT NULL, `birth_year` INTEGER NOT NULL, " +
                "`height_cm` REAL NOT NULL, `weight_kg` REAL NOT NULL, `activity` TEXT NOT NULL, `goal` TEXT NOT NULL, " +
                "`overrides_json` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v9 → v10: time-of-day blocks (GD §14.5). Two nullable columns; every existing quest and habit becomes Anytime, which is
 * exactly how they were shown before.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `time_of_day` TEXT")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `time_of_day` TEXT")
    }
}

/** v10 → v11: identities (GD §14.7). Existing hunters have none until they pick some in Settings. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `identities` TEXT NOT NULL DEFAULT ''")
    }
}

/** v11 → v12: the rebuildable Health cache for insights (GD §14.3). Empty until Status reads Health Connect. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_day_cache` (`game_date` TEXT NOT NULL, `steps` INTEGER, `sleep_minutes` INTEGER, " +
                "`read_at` INTEGER NOT NULL, PRIMARY KEY(`game_date`))",
        )
    }
}

/** Every migration, in order. The database builder and the migration test both use this list. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
)
