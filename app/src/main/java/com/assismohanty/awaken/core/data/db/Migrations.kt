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

/** Every migration, in order. The database builder and the migration test both use this list. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
