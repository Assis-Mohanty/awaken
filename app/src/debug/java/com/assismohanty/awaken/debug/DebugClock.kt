package com.assismohanty.awaken.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.di.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.debugDataStore: DataStore<Preferences> by preferencesDataStore(name = "debug_settings")

/** Debug-only time travel: a whole-day offset persisted in DataStore (Phase 4 Debug screen). */
@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = context.debugDataStore

    /** Starts at 0 until DataStore loads; a too-early "today" only looks like the clock moved back, which is safe. */
    val offsetDays: StateFlow<Long> = store.data
        .map { it[OFFSET_DAYS] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    suspend fun addDays(days: Long) {
        store.edit { it[OFFSET_DAYS] = (it[OFFSET_DAYS] ?: 0L) + days }
    }

    /** Debug-only: pretend Health Connect is connected and reports 9,000 steps today ([DebugHealthGateway]) (Phase 11). */
    val simulateSteps: StateFlow<Boolean> = store.data
        .map { it[SIMULATE_STEPS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setSimulateSteps(enabled: Boolean) {
        store.edit { it[SIMULATE_STEPS] = enabled }
    }

    suspend fun reset() {
        store.edit { it.remove(OFFSET_DAYS) }
    }

    private companion object {
        val OFFSET_DAYS = longPreferencesKey("offset_days")
        val SIMULATE_STEPS = booleanPreferencesKey("simulate_steps")
    }
}

/** Wall clock shifted by [DebugSettings.offsetDays]. The only other place "now" is read (invariant 6). */
@Singleton
class DebugClock @Inject constructor(private val settings: DebugSettings) : Clock {
    override fun now(): Instant = Instant.now().plus(Duration.ofDays(settings.offsetDays.value))

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugClockModule {
    @Binds
    abstract fun bindClock(impl: DebugClock): Clock
}
