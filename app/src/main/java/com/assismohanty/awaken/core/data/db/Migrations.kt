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

/** v12 → v13: weekly reviews (GD §14.4). No review existed before. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weekly_review` (`id` TEXT NOT NULL, `iso_week` TEXT NOT NULL, `submitted_at` INTEGER NOT NULL, " +
                "`went_well` TEXT, `slipped` TEXT, `focus_stat_1` TEXT NOT NULL, `focus_stat_2` TEXT NOT NULL, `intention` TEXT, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_review_iso_week` ON `weekly_review` (`iso_week`)")
    }
}

/** v13 → v14: stored recaps (GD §14.6), and distance in the rebuildable Health cache for the recap body card. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recap` (`period_key` TEXT NOT NULL, `generated_at` INTEGER NOT NULL, " +
                "`payload_version` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, PRIMARY KEY(`period_key`))",
        )
        db.execSQL("ALTER TABLE `health_day_cache` ADD COLUMN `distance_meters` REAL")
    }
}

/** v14 → v15: custom and stopwatch gates and Focus subjects (GD §8.1). Existing runs are presets with no subject. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'PRESET'")
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `subject_id` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `focus_subject` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_focus_subject_name` ON `focus_subject` (`name`)")
    }
}

/** v15 → v16: per-exercise rest and the persisted rest timer (GD §13.2). All new columns are nullable (= no rest). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `rest_sec` INTEGER")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `rest_sec` INTEGER")
        db.execSQL("ALTER TABLE `workout` ADD COLUMN `rest_ends_at` INTEGER")
        db.execSQL("ALTER TABLE `workout` ADD COLUMN `rest_total_sec` INTEGER")
    }
}

/** v16 → v17: a stopwatch can pause many times, so earlier pauses are folded into one total (GD §8.1 P9). */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `paused_before_ms` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v17 → v18: whether Focus Lock was active when the app left the foreground, so that stretch is not a breach (GD 8.2). */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `lock_at_stop` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v18 → v19: habit types, frequency and reminders, count progress, and the quest a Timed habit's run belongs to
 * (GD §16.1–16.2). Every column defaults to the behaviour habits had before (CHECK, fixed weekdays, no reminder).
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'CHECK'")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `frequency` TEXT NOT NULL DEFAULT 'DAYS_OF_WEEK'")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `times_per_week` INTEGER")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `reminder_time` INTEGER")
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `habit_quest_id` TEXT")
    }
}

/**
 * v19 -> v20: Pomodoro sessions, the versioned daily focus goal, and which session and round a gate run belongs to
 * (GD §16.3-16.4). No existing row changes.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `pomodoro_id` TEXT")
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `pomodoro_round` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pomodoro_session` (`id` TEXT NOT NULL, `focus_min` INTEGER NOT NULL, `short_break_min` INTEGER NOT NULL, " +
                "`long_break_min` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `stat` TEXT NOT NULL, `subject_id` TEXT, `started_at` INTEGER NOT NULL, " +
                "`phase` TEXT NOT NULL, `current_round` INTEGER NOT NULL, `phase_ends_at` INTEGER, `status` TEXT NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `focus_goal` (`effective_from` TEXT NOT NULL, `minutes` INTEGER NOT NULL, PRIMARY KEY(`effective_from`))")
    }
}

/** v20 → v21 (progression hints, plate calculator): two new tables; no existing row changes. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_pref` (`exercise_id` TEXT NOT NULL, `rep_min` INTEGER, `rep_max` INTEGER, `increment_kg` REAL, PRIMARY KEY(`exercise_id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gym_settings` (`id` INTEGER NOT NULL, `bar_kg` REAL NOT NULL, `plates_kg` TEXT NOT NULL, `bar_lb` REAL NOT NULL, " +
                "`plates_lb` TEXT NOT NULL, `unit` TEXT NOT NULL, `rep_min` INTEGER NOT NULL, `rep_max` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** v21 → v22 (step goal, phone-down nights): the goal defaults to 8,000 and the rule table starts empty (= off). */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `step_goal` INTEGER NOT NULL DEFAULT 8000")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bedtime_rule` (`effective_from` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `bedtime_min` INTEGER NOT NULL, " +
                "`wake_min` INTEGER NOT NULL, PRIMARY KEY(`effective_from`))",
        )
    }
}

/** v22 → v23 (quit counters): three new tables; nothing existing changes. */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quit_counter` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `start_at` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `cost_per_day_minor` INTEGER, `currency` TEXT, `minutes_per_day` INTEGER, `auto_limit_min` INTEGER, " +
                "`archived_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `relapse` (`id` TEXT NOT NULL, `counter_id` TEXT NOT NULL, `at` INTEGER NOT NULL, `game_date` TEXT, " +
                "`trigger` TEXT, `note` TEXT, `auto` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_relapse_counter_id_game_date` ON `relapse` (`counter_id`, `game_date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quit_day` (`counter_id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `verdict` TEXT NOT NULL, " +
                "`minutes` INTEGER, PRIMARY KEY(`counter_id`, `game_date`))",
        )
    }
}

/** v23 → v24 (Dungeons, Programs): three new tables and three nullable columns; existing rows keep meaning what they did. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `dungeon_id` TEXT")
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `dungeon_stage` INTEGER")
        db.execSQL("ALTER TABLE `habit` ADD COLUMN `program_id` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dungeon` (`id` TEXT NOT NULL, `length_days` INTEGER NOT NULL, `stat` TEXT NOT NULL, `fee` INTEGER NOT NULL, " +
                "`entered_game_date` TEXT NOT NULL, `first_stage_date` TEXT NOT NULL, `status` TEXT NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program` (`id` TEXT NOT NULL, `program_key` TEXT NOT NULL, `run_start_date` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`started_at` INTEGER NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program_mark` (`program_id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `choice` TEXT NOT NULL, " +
                "PRIMARY KEY(`program_id`, `game_date`))",
        )
    }
}

/** v25 (Batch 9 review): why an unpaid phone-down night expired, so a program can leave out a night nobody could read (ENGINE_DECISIONS #147). */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quest_instance` ADD COLUMN `night_verdict` TEXT")
    }
}

/**
 * v26 (declutter Batch 1): achievements, titles, cosmetics, campaigns, dungeons and recaps are gone. Their tables are
 * dropped and three tables are rebuilt without the removed columns (SQLite on minSdk 28 cannot drop a column). Ledger
 * rows are never touched: past gold and XP stay, and old enum values stay readable.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("achievement_unlock", "owned_cosmetic", "campaign", "dungeon", "recap").forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        // Fragments are no longer earned or shown; the balance cache is rebuildable from the ledger.
        db.execSQL("DELETE FROM `balance_cache` WHERE `key` = 'fragments'")
        // hunter_profile
        db.execSQL("CREATE TABLE IF NOT EXISTS `hunter_profile_new` (`id` TEXT NOT NULL, `hunter_id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `first_game_date` TEXT NOT NULL, `focus_stat_1` TEXT NOT NULL, `focus_stat_2` TEXT NOT NULL, `day_start_hour` INTEGER NOT NULL, `reminder_time` TEXT, `onboarding_done` INTEGER NOT NULL, `owned_equipment` TEXT NOT NULL, `injury_mode` INTEGER NOT NULL, `injury_tags` TEXT NOT NULL DEFAULT 'impact,legs', `health_enabled` INTEGER NOT NULL, `last_recalibrated_on` TEXT, `screen_time_from` TEXT, `identities` TEXT NOT NULL DEFAULT '', `step_goal` INTEGER NOT NULL DEFAULT 8000, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `hunter_profile_new` (`id`, `hunter_id`, `name`, `created_at`, `first_game_date`, `focus_stat_1`, `focus_stat_2`, `day_start_hour`, `reminder_time`, `onboarding_done`, `owned_equipment`, `injury_mode`, `injury_tags`, `health_enabled`, `last_recalibrated_on`, `screen_time_from`, `identities`, `step_goal`) SELECT `id`, `hunter_id`, `name`, `created_at`, `first_game_date`, `focus_stat_1`, `focus_stat_2`, `day_start_hour`, `reminder_time`, `onboarding_done`, `owned_equipment`, `injury_mode`, `injury_tags`, `health_enabled`, `last_recalibrated_on`, `screen_time_from`, `identities`, `step_goal` FROM `hunter_profile`")
        db.execSQL("DROP TABLE `hunter_profile`")
        db.execSQL("ALTER TABLE `hunter_profile_new` RENAME TO `hunter_profile`")
        // habit
        db.execSQL("CREATE TABLE IF NOT EXISTS `habit_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `stat` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `verification` TEXT NOT NULL, `days_of_week_mask` INTEGER NOT NULL, `target` INTEGER NOT NULL, `unit` TEXT NOT NULL, `active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `template_id` TEXT, `time_of_day` TEXT, `type` TEXT NOT NULL DEFAULT 'CHECK', `frequency` TEXT NOT NULL DEFAULT 'DAYS_OF_WEEK', `times_per_week` INTEGER, `reminder_time` INTEGER, `program_id` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `habit_new` (`id`, `title`, `stat`, `difficulty`, `verification`, `days_of_week_mask`, `target`, `unit`, `active`, `created_at`, `template_id`, `time_of_day`, `type`, `frequency`, `times_per_week`, `reminder_time`, `program_id`) SELECT `id`, `title`, `stat`, `difficulty`, `verification`, `days_of_week_mask`, `target`, `unit`, `active`, `created_at`, `template_id`, `time_of_day`, `type`, `frequency`, `times_per_week`, `reminder_time`, `program_id` FROM `habit`")
        db.execSQL("DROP TABLE `habit`")
        db.execSQL("ALTER TABLE `habit_new` RENAME TO `habit`")
        // quest_instance
        db.execSQL("CREATE TABLE IF NOT EXISTS `quest_instance_new` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `type` TEXT NOT NULL, `template_id` TEXT, `habit_id` TEXT, `stat` TEXT NOT NULL, `secondary_stat` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `unit` TEXT NOT NULL, `target` INTEGER NOT NULL, `difficulty` TEXT NOT NULL, `verification` TEXT NOT NULL, `min_duration_sec` INTEGER NOT NULL, `status` TEXT NOT NULL, `started_at` INTEGER, `completed_at` INTEGER, `created_at` INTEGER NOT NULL, `path_slot_id` TEXT, `set_reps` TEXT NOT NULL DEFAULT '', `time_of_day` TEXT, `progress` INTEGER NOT NULL DEFAULT 0, `night_verdict` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `quest_instance_new` (`id`, `game_date`, `type`, `template_id`, `habit_id`, `stat`, `secondary_stat`, `title`, `description`, `unit`, `target`, `difficulty`, `verification`, `min_duration_sec`, `status`, `started_at`, `completed_at`, `created_at`, `path_slot_id`, `set_reps`, `time_of_day`, `progress`, `night_verdict`) SELECT `id`, `game_date`, `type`, `template_id`, `habit_id`, `stat`, `secondary_stat`, `title`, `description`, `unit`, `target`, `difficulty`, `verification`, `min_duration_sec`, `status`, `started_at`, `completed_at`, `created_at`, `path_slot_id`, `set_reps`, `time_of_day`, `progress`, `night_verdict` FROM `quest_instance`")
        db.execSQL("DROP TABLE `quest_instance`")
        db.execSQL("ALTER TABLE `quest_instance_new` RENAME TO `quest_instance`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_instance_game_date` ON `quest_instance` (`game_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_instance_status` ON `quest_instance` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quest_instance_path_slot_id` ON `quest_instance` (`path_slot_id`)")
    }
}

/** Every migration, in order. The database builder and the migration test both use this list. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
)
