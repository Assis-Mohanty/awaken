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

/**
 * v27 (declutter Batch 2): identities ("Someone who ...") are gone. `hunter_profile` is rebuilt without the column (SQLite on
 * minSdk 28 cannot drop one). `completion.crit` stays: old rows keep their value and new rows are always false.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `hunter_profile_new` (`id` TEXT NOT NULL, `hunter_id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `first_game_date` TEXT NOT NULL, `focus_stat_1` TEXT NOT NULL, `focus_stat_2` TEXT NOT NULL, `day_start_hour` INTEGER NOT NULL, `reminder_time` TEXT, `onboarding_done` INTEGER NOT NULL, `owned_equipment` TEXT NOT NULL, `injury_mode` INTEGER NOT NULL, `injury_tags` TEXT NOT NULL DEFAULT 'impact,legs', `health_enabled` INTEGER NOT NULL, `last_recalibrated_on` TEXT, `screen_time_from` TEXT, `step_goal` INTEGER NOT NULL DEFAULT 8000, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `hunter_profile_new` (`id`, `hunter_id`, `name`, `created_at`, `first_game_date`, `focus_stat_1`, `focus_stat_2`, `day_start_hour`, `reminder_time`, `onboarding_done`, `owned_equipment`, `injury_mode`, `injury_tags`, `health_enabled`, `last_recalibrated_on`, `screen_time_from`, `step_goal`) SELECT `id`, `hunter_id`, `name`, `created_at`, `first_game_date`, `focus_stat_1`, `focus_stat_2`, `day_start_hour`, `reminder_time`, `onboarding_done`, `owned_equipment`, `injury_mode`, `injury_tags`, `health_enabled`, `last_recalibrated_on`, `screen_time_from`, `step_goal` FROM `hunter_profile`")
        db.execSQL("DROP TABLE `hunter_profile`")
        db.execSQL("ALTER TABLE `hunter_profile_new` RENAME TO `hunter_profile`")
    }
}

/**
 * v27 -> v28 (quiz onboarding): the profile gains the body values onboarding asks for (they only prefill the Diet setup), the
 * Diet goal suggestion, and the training days with their reminder. The new columns are nullable or default to "none", so every
 * existing profile keeps behaving exactly as before.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `birth_year` INTEGER")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `height_cm` REAL")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `weight_kg` REAL")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `diet_goal_hint` TEXT")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `training_days` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `hunter_profile` ADD COLUMN `training_reminder` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v28 → v29 (Gym redesign, GD §18): routines gain an order, a source plan and per-exercise targets, supersets and
 * sections; the weekly schedule and one-off day overrides are new tables; workouts remember their routine and mode; set
 * rows gain a completion time, section, hold time, side, superset and planned range; Gym settings gain the onboarding
 * answers and the rest and logging preferences. Every new column is nullable or has a default, so every existing workout,
 * set, routine and personal record is untouched (sets from before have no completion time and so never pay XP).
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routine` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `routine` ADD COLUMN `source_plan_id` TEXT")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `rep_min` INTEGER")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `rep_max` INTEGER")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `superset` TEXT")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `unilateral` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `section` TEXT NOT NULL DEFAULT 'WORK'")
        db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `measure` TEXT NOT NULL DEFAULT 'REPS'")
        db.execSQL("CREATE TABLE IF NOT EXISTS `week_slot` (`weekday` INTEGER NOT NULL, `routine_id` TEXT NOT NULL, PRIMARY KEY(`weekday`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `day_override` (`game_date` TEXT NOT NULL, `routine_id` TEXT, PRIMARY KEY(`game_date`))")
        db.execSQL("ALTER TABLE `workout` ADD COLUMN `routine_id` TEXT")
        db.execSQL("ALTER TABLE `workout` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'LIST'")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `completed_at` INTEGER")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `section` TEXT NOT NULL DEFAULT 'WORK'")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `duration_sec` INTEGER")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `side` TEXT")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `superset` TEXT")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `rep_min` INTEGER")
        db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `rep_max` INTEGER")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `fitness_level` TEXT")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `goal` TEXT")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `equipment` TEXT")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `injuries` TEXT")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `sex` TEXT")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `rest_compound_sec` INTEGER NOT NULL DEFAULT 120")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `rest_isolation_sec` INTEGER NOT NULL DEFAULT 60")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `rest_sound` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `rest_vibrate` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `coached` INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v29 -> v30 (Focus protection, Pause): a new table of finished pauses. Nothing existing is touched. The rows are display
 * data (stats and the cooldown), never a ledger row.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pause_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, " +
                "`game_day` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `delay_seconds` INTEGER NOT NULL, `intention` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pause_event_game_day` ON `pause_event` (`game_day`)")
    }
}

/**
 * v30 -> v31 (Focus protection, Lockdown): a table of Lockdown sessions, and a flag on gate runs saying which run is one.
 * Every existing run gets 0 (an ordinary gate), so nothing existing changes.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lockdown_session` (`id` TEXT NOT NULL, `gate_run_id` TEXT NOT NULL, `planned_ms` INTEGER NOT NULL, " +
                "`served_ms` INTEGER NOT NULL, `anchor_elapsed_ms` INTEGER NOT NULL, `anchor_wall` INTEGER NOT NULL, `boot_count` INTEGER NOT NULL, " +
                "`allowed_packages` TEXT NOT NULL, `status` TEXT NOT NULL, `broken_reason` TEXT, `clock_changed` INTEGER NOT NULL, " +
                "`uncovered_ms` INTEGER NOT NULL, `heartbeat_wall` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lockdown_session_status` ON `lockdown_session` (`status`)")
        db.execSQL("ALTER TABLE `gate_run` ADD COLUMN `lockdown` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v31 -> v32 (Diet v2, docs/diet/PLAN.md section 3.8): every change is additive, so no diary row, custom food or profile is
 * touched. New columns are nullable or carry the old behaviour as their default (loss pace Steady, preset Balanced, entry
 * source Search). The four built-in meal slots use the ids the old `Meal` enum wrote, so `food_log.meal` needs no rewrite.
 * The profile's weight becomes the plan weight and the first weigh-in.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `custom_food` ADD COLUMN `brand` TEXT")
        db.execSQL("ALTER TABLE `custom_food` ADD COLUMN `barcode` TEXT")
        db.execSQL("ALTER TABLE `custom_food` ADD COLUMN `serving_grams` REAL")
        db.execSQL("ALTER TABLE `custom_food` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `custom_food` SET `updated_at` = `created_at`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_food_barcode` ON `custom_food` (`barcode`)")

        db.execSQL("ALTER TABLE `food_log` ADD COLUMN `note` TEXT")
        db.execSQL("ALTER TABLE `food_log` ADD COLUMN `portion_count` REAL")
        db.execSQL("ALTER TABLE `food_log` ADD COLUMN `entry_source` TEXT NOT NULL DEFAULT 'SEARCH'")

        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `loss_pace` TEXT NOT NULL DEFAULT 'STEADY'")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `macro_preset` TEXT NOT NULL DEFAULT 'BALANCED'")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `protein_per_kg` REAL")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `split_protein_pct` INTEGER")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `split_carbs_pct` INTEGER")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `split_fat_pct` INTEGER")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `plan_weight_kg` REAL")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `trend_dismissed_on` TEXT")
        db.execSQL("ALTER TABLE `nutrition_profile` ADD COLUMN `water_target_ml` INTEGER")
        db.execSQL("UPDATE `nutrition_profile` SET `plan_weight_kg` = `weight_kg`")

        db.execSQL("CREATE TABLE IF NOT EXISTS `meal_slot` (`id` TEXT NOT NULL, `name` TEXT, `position` INTEGER NOT NULL, `built_in` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `meal_slot` (`id`, `name`, `position`, `built_in`) VALUES ('BREAKFAST', NULL, 0, 1), ('LUNCH', NULL, 1, 1), ('SNACK', NULL, 2, 1), ('DINNER', NULL, 3, 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `favourite_food` (`food_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`food_id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `saved_meal` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_meal_item` (`id` TEXT NOT NULL, `saved_meal_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `food_id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `grams` REAL NOT NULL, `portion_label` TEXT, `nutrients_json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_meal_item_saved_meal_id` ON `saved_meal_item` (`saved_meal_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `servings` REAL NOT NULL, `cooked_grams` REAL, `note` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_ingredient` (`id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `food_id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `grams` REAL NOT NULL, `nutrients_json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_recipe_id` ON `recipe_ingredient` (`recipe_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `water_log` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `ml` INTEGER NOT NULL, `logged_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_log_game_date` ON `water_log` (`game_date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weight_log` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `weight_kg` REAL NOT NULL, `logged_at` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_log_game_date` ON `weight_log` (`game_date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `body_measure_log` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `kind` TEXT NOT NULL, `value_cm` REAL NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measure_log_game_date` ON `body_measure_log` (`game_date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `fasting_session` (`id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `target_minutes` INTEGER NOT NULL, `ended_at` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_plan_entry` (`id` TEXT NOT NULL, `game_date` TEXT NOT NULL, `meal` TEXT NOT NULL, `food_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`grams` REAL NOT NULL, `portion_label` TEXT, `nutrients_json` TEXT NOT NULL, `logged` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_entry_game_date` ON `meal_plan_entry` (`game_date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `diary_note` (`game_date` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`game_date`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_feedback` (`id` TEXT NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `shown_json` TEXT NOT NULL, `chosen_food_id` TEXT, PRIMARY KEY(`id`))",
        )

        // The profile's weight is the first weigh-in (UTC date of the last save; the log is for the trend, not for the ledger).
        db.execSQL(
            "INSERT INTO `weight_log` (`id`, `game_date`, `weight_kg`, `logged_at`, `note`) " +
                "SELECT 'seed:profile', date(`updated_at` / 1000, 'unixepoch'), `weight_kg`, `updated_at`, NULL FROM `nutrition_profile`",
        )
    }
}

/** Every migration, in order. The database builder and the migration test both use this list. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
)
