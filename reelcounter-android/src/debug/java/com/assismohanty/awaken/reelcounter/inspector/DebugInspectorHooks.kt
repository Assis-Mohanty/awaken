package com.assismohanty.awaken.reelcounter.inspector

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.assismohanty.awaken.reelcounter.R
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.Detection
import com.assismohanty.awaken.reelcounter.core.detect.ScreenDetector
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.ScrollSignal
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.di.IoDispatcher
import com.assismohanty.awaken.reelcounter.overlay.OverlayController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Debug build only: shows a chip on supported apps and dumps the ids-only node tree when it is tapped. */
@Singleton
class DebugInspectorHooks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val detectors: DetectorRepository,
    private val overlay: OverlayController,
    private val clock: ReelClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : InspectorHooks {

    override val screen: (@Composable (onBack: () -> Unit) -> Unit) = { onBack -> InspectorScreen(onBack) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val scrollLog = mutableMapOf<String, ArrayDeque<String>>()
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val fileFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    @Volatile private var enabled = false
    private var lastChipAt: Instant? = null
    private val hideChip = Runnable { overlay.hideDebugChip() }

    init {
        settings.inspectorEnabled.onEach {
            enabled = it
            if (!it) overlay.hideDebugChip()
        }.launchIn(scope)
    }

    override fun onScroll(pkg: String, scroll: ScrollSignal, at: Instant) {
        if (!enabled) return
        val line = "${timeFormat.format(at.atZone(clock.zone()))} src=${scroll.sourceViewId} " +
            "from=${scroll.fromIndex} to=${scroll.toIndex} count=${scroll.itemCount} dy=${scroll.scrollDeltaY}"
        synchronized(scrollLog) {
            val log = scrollLog.getOrPut(pkg) { ArrayDeque() }
            log.addLast(line)
            while (log.size > NodeTreeDumper.MAX_SCROLL_LOG) log.removeFirst()
        }
    }

    override fun onWindowEvent(pkg: String, nodes: NodeSource?, at: Instant) {
        if (!enabled) return
        handler.removeCallbacks(hideChip)
        handler.postDelayed(hideChip, CHIP_TIMEOUT_MILLIS)
        val last = lastChipAt
        if (last != null && Duration.between(last, at).toMillis() < CHIP_REFRESH_MILLIS) return
        lastChipAt = at
        val detection = ScreenDetector(detectors.config.value.config).detect(pkg, nodes)
        val label = when (detection) {
            is Detection.ShortVideo -> context.getString(R.string.rc_chip_match, detection.app.monogram.toString())
            Detection.NotShortVideo -> context.getString(R.string.rc_chip_nomatch)
        }
        overlay.showDebugChip(label) { dump(pkg, nodes, at) }
    }

    override fun onServiceStopped() {
        handler.removeCallbacks(hideChip)
        overlay.hideDebugChip()
    }

    private fun dump(pkg: String, nodes: NodeSource?, at: Instant) {
        scope.launch {
            val name = withContext(io) {
                val detection = ScreenDetector(detectors.config.value.config).detect(pkg, nodes)
                val log = synchronized(scrollLog) { scrollLog[pkg]?.toList().orEmpty() }
                val text = NodeTreeDumper.dump(
                    pkg = pkg,
                    at = at,
                    screen = nodes?.screen ?: com.assismohanty.awaken.reelcounter.core.model.Bounds(0, 0, 0, 0),
                    detection = detection,
                    root = nodes?.tree(MAX_DEPTH, MAX_NODES),
                    scrollLog = log,
                )
                val app = ShortVideoApp.fromPackage(pkg)?.name ?: pkg
                val dir = File(context.filesDir, DIR_NAME).also { it.mkdirs() }
                val file = File(dir, "$app-${fileFormat.format(at.atZone(clock.zone()))}.txt")
                file.writeText(text)
                file.name
            }
            Toast.makeText(context, context.getString(R.string.rc_dump_saved, name), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val DIR_NAME = "rc_inspector"
        private const val MAX_DEPTH = 60
        private const val MAX_NODES = 2_000
        private const val CHIP_REFRESH_MILLIS = 500L
        private const val CHIP_TIMEOUT_MILLIS = 6_000L
    }
}
