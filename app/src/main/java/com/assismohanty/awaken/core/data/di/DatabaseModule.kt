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
    @Provides fun provideFocusDao(db: AwakenDatabase) = db.focusDao()
    @Provides fun providePomodoroDao(db: AwakenDatabase) = db.pomodoroDao()
    @Provides fun providePauseDao(db: AwakenDatabase) = db.pauseDao()
    @Provides fun provideQuitDao(db: AwakenDatabase) = db.quitDao()
    @Provides fun provideProgramDao(db: AwakenDatabase) = db.programDao()
    @Provides fun provideTrialDao(db: AwakenDatabase) = db.trialDao()
    @Provides fun providePathDao(db: AwakenDatabase) = db.pathDao()
    @Provides fun provideScreenTimeDao(db: AwakenDatabase) = db.screenTimeDao()
    @Provides fun provideGymDao(db: AwakenDatabase) = db.gymDao()
    @Provides fun provideDietDao(db: AwakenDatabase) = db.dietDao()
    @Provides fun provideInsightDao(db: AwakenDatabase) = db.insightDao()
    @Provides fun provideWeeklyReviewDao(db: AwakenDatabase) = db.weeklyReviewDao()
}
