package com.assismohanty.awaken.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.CampaignDao
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.InsightDao
import com.assismohanty.awaken.core.data.db.dao.WeeklyReviewDao
import com.assismohanty.awaken.core.data.db.dao.RecapDao
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.GymDao
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.LedgerDao
import com.assismohanty.awaken.core.data.db.dao.OwnedCosmeticDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.AiReportEntity
import com.assismohanty.awaken.core.data.db.entity.AppRuleEntity
import com.assismohanty.awaken.core.data.db.entity.CampaignEntity
import com.assismohanty.awaken.core.data.db.entity.BalanceCacheEntity
import com.assismohanty.awaken.core.data.db.entity.HealthDayCacheEntity
import com.assismohanty.awaken.core.data.db.entity.WeeklyReviewEntity
import com.assismohanty.awaken.core.data.db.entity.RecapEntity
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.CustomExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.CustomFoodEntity
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.HealthSyncStateEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.NutritionProfileEntity
import com.assismohanty.awaken.core.data.db.entity.OwnedCosmeticEntity
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathMilestoneEntity
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineEntity
import com.assismohanty.awaken.core.data.db.entity.RoutineExerciseEntity
import com.assismohanty.awaken.core.data.db.entity.StatTierEntity
import com.assismohanty.awaken.core.data.db.entity.TrialEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutEntity
import com.assismohanty.awaken.core.data.db.entity.WorkoutSetEntity

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
        HealthDayCacheEntity::class,
        WeeklyReviewEntity::class,
        RecapEntity::class,
        FocusSubjectEntity::class,
        DayRecordEntity::class,
        GateRunEntity::class,
        TrialEntity::class,
        AchievementUnlockEntity::class,
        OwnedCosmeticEntity::class,
        HealthSyncStateEntity::class,
        PathEntity::class,
        PathMilestoneEntity::class,
        PathWeekEntity::class,
        PathSlotEntity::class,
        AiReportEntity::class,
        AppRuleEntity::class,
        CampaignEntity::class,
        CustomExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutSetEntity::class,
        CustomFoodEntity::class,
        FoodLogEntity::class,
        NutritionProfileEntity::class,
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
    abstract fun pathDao(): PathDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun campaignDao(): CampaignDao
    abstract fun gymDao(): GymDao
    abstract fun dietDao(): DietDao
    abstract fun insightDao(): InsightDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao
    abstract fun recapDao(): RecapDao
    abstract fun focusDao(): FocusDao

    companion object {
        const val NAME = "awaken.db"

        /** Also the export format version: an export is only importable into the schema it was written from. */
        const val SCHEMA_VERSION = 16
    }
}
