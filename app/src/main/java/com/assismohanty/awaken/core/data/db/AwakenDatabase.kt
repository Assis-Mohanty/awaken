package com.assismohanty.awaken.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.OwnedCosmeticDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.BalanceCacheEntity
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.HealthSyncStateEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.OwnedCosmeticEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.db.entity.TrialEntity

@Database(
    version = AwakenDatabase.SCHEMA_VERSION,
    exportSchema = true,
    entities = [
        HunterProfileEntity::class,
        StatTierEntity::class,
        QuestInstanceEntity::class,
        CompletionEntity::class,
        HabitEntity::class,
        LedgerEntryEntity::class,
        BalanceCacheEntity::class,
        DayRecordEntity::class,
        GateRunEntity::class,
        TrialEntity::class,
        AchievementUnlockEntity::class,
        OwnedCosmeticEntity::class,
        HealthSyncStateEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AwakenDatabase : RoomDatabase() {
    abstract fun hunterDao(): HunterDao
    abstract fun questDao(): QuestDao
    abstract fun completionDao(): CompletionDao
    abstract fun habitDao(): HabitDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun dayRecordDao(): DayRecordDao
    abstract fun gateRunDao(): GateRunDao
    abstract fun trialDao(): TrialDao
    abstract fun achievementDao(): AchievementDao
    abstract fun ownedCosmeticDao(): OwnedCosmeticDao

    companion object {
        const val NAME = "awaken.db"

        /** Also the export format version: an export is only importable into the schema it was written from. */
        const val SCHEMA_VERSION = 4
    }
}
