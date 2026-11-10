package com.assismohanty.awaken.core.protect

import android.content.Context
import com.assismohanty.awaken.core.lock.AlwaysAllowed
import com.assismohanty.awaken.core.lock.FocusLockState
import com.assismohanty.awaken.engine.BlockDecision
import com.assismohanty.awaken.engine.PauseRules
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Wires the watcher's two seams to the real phone: Focus Lock's live state and this phone's always-allowed apps. */
@Module
@InstallIn(SingletonComponent::class)
object ProtectModule {
    @Provides
    @Singleton
    fun provideLockCoverage(state: FocusLockState): LockCoverage = LockCoverage { pkg: String, now: Instant ->
        state.isActive() && state.decide(pkg, state.snapshot.first(), now) == BlockDecision.BLOCK
    }

    @Provides
    @Singleton
    fun provideProtectApps(@ApplicationContext context: Context, allowed: AlwaysAllowed): ProtectApps = object : ProtectApps {
        override fun alwaysAllowed(): Set<String> = allowed.current()

        override fun transientWindows(): Set<String> =
            PauseRules.TRANSIENT_WINDOWS + context.packageName + listOfNotNull(allowed.keyboard())
    }
}
