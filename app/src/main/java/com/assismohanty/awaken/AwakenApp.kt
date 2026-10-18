package com.assismohanty.awaken

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.work.DayRolloverWorker
import com.assismohanty.awaken.core.work.GateCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class AwakenApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var hunterDao: HunterDao
    @Inject lateinit var clock: Clock
    @Inject lateinit var gateCoordinator: GateCoordinator

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Focus Gate background accounting is per process, not per screen (ARCH §3.3).
        ProcessLifecycleOwner.get().lifecycle.addObserver(gateCoordinator)
        appScope.launch {
            try {
                DayRolloverWorker.schedule(WorkManager.getInstance(this@AwakenApp), hunterDao, clock)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Scheduling the rollover worker failed")
            }
        }
    }
}
