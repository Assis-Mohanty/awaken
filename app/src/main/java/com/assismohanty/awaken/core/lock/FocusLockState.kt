package com.assismohanty.awaken.core.lock

import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.domain.FocusLockStatus
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.FocusLockRules
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.LockGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

/** Everything the service needs to decide, as one value: the switch, the allowed apps, the running session. */
data class LockSnapshot(
    val enabled: Boolean = false,
    val userAllowed: Set<String> = emptySet(),
    val run: GateRunEntity? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** What the Focus Lock watcher asks of the rules: is a session being covered, and does this app get covered. */
interface FocusLockDecider {
    fun sessionRunning(snapshot: LockSnapshot, now: Instant): Boolean

    fun decide(packageName: String?, snapshot: LockSnapshot, now: Instant): BlockDecision
}

/**
 * Focus Lock's live state (GD 8.2). The cover is drawn by the Protect service from usage events, so "connected" means
 * the two permissions it needs are on; the rest comes from the preferences and the running gate. [isActive] is what the
 * Focus Gate rules ask: lock on in AWAKEN and the permissions really granted.
 */
@Singleton
class FocusLockState @Inject constructor(
    uiPrefs: UiPrefsRepository,
    gateRunDao: GateRunDao,
    private val alwaysAllowedApps: AlwaysAllowed,
    private val coverable: CoverableApps,
    private val access: FocusLockSystemAccess,
    @ApplicationScope scope: CoroutineScope,
) : FocusLockStatus, FocusLockDecider {
    private val connected = MutableStateFlow(false)

    @Volatile
    private var enabled = false

    init {
        uiPrefs.prefs.onEach { enabled = it.focusLock }.launchIn(scope)
    }

    override val serviceConnected: StateFlow<Boolean> = connected.asStateFlow()

    val snapshot: Flow<LockSnapshot> = combine(uiPrefs.prefs, gateRunDao.observeRunning()) { prefs, run ->
        LockSnapshot(prefs.focusLock, prefs.allowedApps, run, prefs.themeMode)
    }

    override fun isActive(): Boolean = enabled && access.permissionsReady()

    override fun alwaysAllowed(): Set<String> = alwaysAllowedApps.current()

    /** Reads the two permissions again (the app came to the front, a setting changed). */
    fun refreshAccess() {
        connected.value = access.permissionsReady()
    }

    /** A focus session is running and Focus Lock is on: something must be watching which app is in front. */
    suspend fun sessionRunning(now: Instant): Boolean = snapshot.first().let { sessionRunning(it, now) }

    override fun sessionRunning(snapshot: LockSnapshot, now: Instant): Boolean =
        snapshot.enabled && gate(snapshot.run, now).let { it.running && !it.paused }

    /** The session as the rules see it at [now]; a stopwatch has no planned end. */
    fun gate(run: GateRunEntity?, now: Instant): LockGate {
        // A Lockdown covers apps itself; Focus Lock stays out of its way (GD §19.2).
        if (run == null || run.lockdown) return LockGate(running = false, paused = false, endsAt = null)
        val timing = run.timing
        return LockGate(
            running = true,
            paused = GateRunMath.isPaused(timing, now),
            endsAt = if (run.mode == GateMode.STOPWATCH) null else GateRunMath.endAt(timing, now),
        )
    }

    override fun decide(packageName: String?, snapshot: LockSnapshot, now: Instant): BlockDecision {
        if (!snapshot.enabled) return BlockDecision.ALLOW
        // Only real apps are ever covered; the phone own windows (fingerprint, screenshot tools, gesture panels) are not apps.
        if (packageName != null && !coverable.isCoverable(packageName)) return BlockDecision.ALLOW
        return FocusLockRules.decide(packageName, gate(snapshot.run, now), alwaysAllowedApps.current(), snapshot.userAllowed, now)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FocusLockModule {
    @Binds
    abstract fun bindFocusLockStatus(state: FocusLockState): FocusLockStatus

    @Binds
    abstract fun bindFocusLockDecider(state: FocusLockState): FocusLockDecider
}
