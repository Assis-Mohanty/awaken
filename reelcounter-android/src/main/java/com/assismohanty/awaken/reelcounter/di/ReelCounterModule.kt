package com.assismohanty.awaken.reelcounter.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.db.ReelDao
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.overlay.OverlayController
import com.assismohanty.awaken.reelcounter.service.OverlaySink
import com.assismohanty.awaken.reelcounter.service.WidgetSink
import com.assismohanty.awaken.reelcounter.time.SystemReelClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReelCounterModule {
    @Provides
    @Singleton
    fun clock(): ReelClock = SystemReelClock()

    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @CounterDispatcher
    @Singleton
    fun counterDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReelDatabase =
        Room.databaseBuilder(context, ReelDatabase::class.java, "reelcounter.db").build()

    @Provides
    fun dao(db: ReelDatabase): ReelDao = db.dao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("rc_settings") })

    /** Replaced by the real widget refresher in Phase 5. */
    @Provides
    @Singleton
    fun widgetSink(): WidgetSink = WidgetSink { }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReelCounterBindings {
    @Binds
    abstract fun overlaySink(impl: OverlayController): OverlaySink
}
