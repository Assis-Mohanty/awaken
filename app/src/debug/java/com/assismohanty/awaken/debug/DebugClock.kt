package com.assismohanty.awaken.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assismohanty.awaken.BuildConfig
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

    /**
     * Debug-only: answer Path Oracle calls from [FakePathOracle] instead of the network (Phase 15). Until the hunter flips
     * it, it follows whether a staging URL is configured, so a fresh checkout works offline.
     */
    val useFakeOracle: StateFlow<Boolean> = store.data
        .map { it[USE_FAKE_ORACLE] ?: !BuildConfig.ORACLE_BASE_URL.startsWith("https://") }
        .stateIn(scope, SharingStarted.Eagerly, !BuildConfig.ORACLE_BASE_URL.startsWith("https://"))

    suspend fun setUseFakeOracle(enabled: Boolean) {
        store.edit { it[USE_FAKE_ORACLE] = enabled }
    }

    /** Debug-only: make every Oracle call fail the same way, to see the Paths tab degrade (Phase 15c). */
    val oracleFailure: StateFlow<OracleFailure> = store.data
        .map { prefs -> OracleFailure.entries.firstOrNull { it.name == prefs[ORACLE_FAILURE] } ?: OracleFailure.NONE }
        .stateIn(scope, SharingStarted.Eagerly, OracleFailure.NONE)

    suspend fun setOracleFailure(failure: OracleFailure) {
        store.edit { it[ORACLE_FAILURE] = failure.name }
    }

    suspend fun reset() {
        store.edit { it.remove(OFFSET_DAYS) }
    }

    private companion object {
        val OFFSET_DAYS = longPreferencesKey("offset_days")
        val SIMULATE_STEPS = booleanPreferencesKey("simulate_steps")
        val USE_FAKE_ORACLE = booleanPreferencesKey("use_fake_oracle")
        val ORACLE_FAILURE = stringPreferencesKey("oracle_failure")
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

/** What the Debug screen forces every Path Oracle call to return; [NONE] lets the call through. */
enum class OracleFailure { NONE, OFFLINE, SERVER_ERROR, RATE_LIMITED }
