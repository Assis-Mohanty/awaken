package com.assismohanty.awaken.core.data.catalog

import android.content.Context
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.engine.body.BenchmarkData
import com.assismohanty.awaken.engine.body.ExerciseOverlay
import com.assismohanty.awaken.engine.body.PlanLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled gym content beside the exercise catalog (GD §18): plans, the exercise overlay and benchmarks. Loaded once, off the main thread. */
@Singleton
class GymContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var plans: PlanLibrary? = null
    private var overlay: ExerciseOverlay? = null
    private var benchmarks: BenchmarkData? = null

    private suspend fun read(path: String): String = withContext(ioDispatcher) {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    suspend fun plans(): PlanLibrary = plans ?: mutex.withLock { plans ?: PlanLibrary.parse(read("gym/plans.json")).also { plans = it } }

    suspend fun overlay(): ExerciseOverlay = overlay ?: mutex.withLock { overlay ?: ExerciseOverlay.parse(read("gym/exercise_overlay.json")).also { overlay = it } }

    suspend fun benchmarks(): BenchmarkData = benchmarks ?: mutex.withLock { benchmarks ?: BenchmarkData.parse(read("gym/benchmarks.json")).also { benchmarks = it } }
}
