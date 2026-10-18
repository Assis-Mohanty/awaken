package com.assismohanty.awaken.core.data.clock

import android.content.Context
import android.os.SystemClock as AndroidSystemClock
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monotonic device time for Focus Gate background accounting (ARCH §3.3). Elapsed realtime ignores wall-clock changes
 * but restarts at boot, so [bootCount] tells whether two readings are comparable. Debug time travel does not touch it.
 */
interface DeviceUptime {
    fun elapsedRealtimeMs(): Long
    fun bootCount(): Int
}

@Singleton
class SystemDeviceUptime @Inject constructor(@ApplicationContext private val context: Context) : DeviceUptime {
    override fun elapsedRealtimeMs(): Long = AndroidSystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceUptimeModule {
    @Binds
    abstract fun bindDeviceUptime(impl: SystemDeviceUptime): DeviceUptime
}
