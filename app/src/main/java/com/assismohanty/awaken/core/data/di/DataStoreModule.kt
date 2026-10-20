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

/** AI Paths consent and the random install ID (ARCH §4). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiPrefsStore

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
    @AiPrefsStore
    fun provideAiPrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("ai_prefs")
    }
}
