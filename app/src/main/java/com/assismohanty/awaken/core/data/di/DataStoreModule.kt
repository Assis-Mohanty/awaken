package com.assismohanty.awaken.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Pending System notices (ARCH §4: DataStore holds UI state only). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NoticePrefs

/** In-app look-and-feel toggles: haptics, reduce motion (DS §2.5, §2.6). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UiPrefsStore

/** The last focus session started and the Pomodoro defaults, for the tile and widget (GD §16.3, ARCH §3.15). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFocusPrefs

/** Pause settings: target apps, delay, cooldown, hours (GD §19.1). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PausePrefs

/** AI Paths consent and the random install ID (ARCH §4). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiPrefsStore

/** Which Screen Discipline alerts were already sent today (once per app per day, GD §6.7). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScreenTimeAlertPrefs

/** The onboarding quiz draft, so the flow resumes after the app is killed (docs/onboarding/PLAN.md §3.2). Not backed up. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnboardingDraftPrefs

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @NoticePrefs
    fun provideNoticePrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("notices")
    }

    @Provides
    @Singleton
    @UiPrefsStore
    fun provideUiPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("ui_prefs")
    }

    @Provides
    @Singleton
    @LastFocusPrefs
    fun provideLastFocusPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("last_focus")
    }

    @Provides
    @Singleton
    @AiPrefsStore
    fun provideAiPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("ai_prefs")
    }

    @Provides
    @Singleton
    @ScreenTimeAlertPrefs
    fun provideScreenTimeAlertPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("screen_time_alerts")
    }

    @Provides
    @Singleton
    @OnboardingDraftPrefs
    fun provideOnboardingDraftPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("onboarding_draft")
    }

    @Provides
    @Singleton
    @PausePrefs
    fun providePausePrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("pause")
    }
}
