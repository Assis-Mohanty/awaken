package com.assismohanty.awaken.core.data.di

import android.content.Context
import androidx.room.Room
import com.assismohanty.awaken.core.data.db.ALL_MIGRATIONS
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // No destructive migration fallback: every schema change ships a Migration (ARCH §2), so hunters never lose data.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AwakenDatabase =
        Room.databaseBuilder(context, AwakenDatabase::class.java, AwakenDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun provideHunterDao(db: AwakenDatabase) = db.hunterDao()
    @Provides fun provideQuestDao(db: AwakenDatabase) = db.questDao()
    @Provides fun provideCompletionDao(db: AwakenDatabase) = db.completionDao()
    @Provides fun provideHabitDao(db: AwakenDatabase) = db.habitDao()
    @Provides fun provideLedgerDao(db: AwakenDatabase) = db.ledgerDao()
    @Provides fun provideDayRecordDao(db: AwakenDatabase) = db.dayRecordDao()
    @Provides fun provideGateRunDao(db: AwakenDatabase) = db.gateRunDao()
    @Provides fun provideTrialDao(db: AwakenDatabase) = db.trialDao()
    @Provides fun provideAchievementDao(db: AwakenDatabase) = db.achievementDao()
    @Provides fun provideOwnedCosmeticDao(db: AwakenDatabase) = db.ownedCosmeticDao()
}
