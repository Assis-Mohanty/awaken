package com.assismohanty.awaken.core.reelcounter

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.designsystem.components.ReelBubble
import com.assismohanty.awaken.core.designsystem.lexicon.LexResources
import com.assismohanty.awaken.core.designsystem.lexicon.Lexicon
import com.assismohanty.awaken.core.designsystem.lexicon.PresentationMode
import com.assismohanty.awaken.core.designsystem.lexicon.lexString
import com.assismohanty.awaken.core.designsystem.theme.AwakenDimens
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.feature.reelcounter.ReelNudgeContent
import com.assismohanty.awaken.feature.reelcounter.ReelPanelContent
import com.assismohanty.awaken.feature.reelcounter.ReelPanelRow
import com.assismohanty.awaken.feature.reelcounter.labelRes
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterConfig
import com.assismohanty.awaken.reelcounter.core.stats.StatsCalculator
import com.assismohanty.awaken.reelcounter.core.time.GameDay
import com.assismohanty.awaken.reelcounter.core.time.ReelClock
import com.assismohanty.awaken.reelcounter.data.BubbleEdge
import com.assismohanty.awaken.reelcounter.data.ReelDayStart
import com.assismohanty.awaken.reelcounter.data.ReelRepository
import com.assismohanty.awaken.reelcounter.data.SettingsStore
import com.assismohanty.awaken.reelcounter.overlay.OverlayWindowHost
import com.assismohanty.awaken.reelcounter.overlay.ReelOverlayActions
import com.assismohanty.awaken.reelcounter.overlay.ReelOverlayUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reel Counter's overlay windows on AWAKEN's design system: the counter bubble (drag, snap to an edge, tap for a
 * panel, long-press to hide for this session), the panel and the limit nudge. Like Focus Lock's cover they are
 * accessibility-overlay windows added by the service itself, so no "draw over other apps" permission is needed.
 * Every call arrives on the main thread (`OverlayController` marshals them).
 */
@Singleton
class ReelOverlayWindows @Inject constructor(
    private val prefsRepository: UiPrefsRepository,
    private val settings: SettingsStore,
    private val repository: ReelRepository,
    private val clock: ReelClock,
    private val dayStart: ReelDayStart,
) : ReelOverlayUi {

    private data class Bubble(val app: ShortVideoApp, val sessionId: Long, val count: Int, val allowance: Int?)
    private data class Nudge(val app: ShortVideoApp, val count: Int, val allowance: Int?, val snoozesLeft: Int)
    private data class Look(val dark: Boolean, val presentation: PresentationMode, val reduceMotion: Boolean)

    private var service: AccessibilityService? = null
    private var actions: ReelOverlayActions? = null
    private var scope: CoroutineScope? = null
    private var bubbleHost: OverlayWindowHost? = null
    private var panelHost: OverlayWindowHost? = null
    private var nudgeHost: OverlayWindowHost? = null

    private var bubble by mutableStateOf<Bubble?>(null)
    private var nudge by mutableStateOf<Nudge?>(null)
    private var panelRows by mutableStateOf<List<ReelPanelRow>>(emptyList())
    private var look by mutableStateOf(Look(dark = true, presentation = PresentationMode.CLEAN, reduceMotion = false))

    private var placement = BubblePlacement(BubbleEdge.END, SettingsStore.DEFAULT_BUBBLE_Y)
    private var bubbleEnabled = true
    private val hiddenSessions = mutableSetOf<Long>()
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelJob: Job? = null
    private var dragging = false

    override fun attach(service: AccessibilityService, actions: ReelOverlayActions) {
        this.service = service
        this.actions = actions
        bubbleHost = OverlayWindowHost(service)
        panelHost = OverlayWindowHost(service)
        nudgeHost = OverlayWindowHost(service)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        prefsRepository.prefs.onEach { look = it.toLook(service) }.launchIn(s)
        settings.bubbleEnabled.onEach {
            bubbleEnabled = it
            if (!it) closeBubbleAndPanel() else bubble?.let { _ -> ensureBubbleWindow() }
        }.launchIn(s)
        combine(settings.bubbleEdge, settings.bubbleYFraction) { edge, y -> BubblePlacement(edge, y) }
            .onEach {
                if (!dragging) {
                    placement = it
                    moveBubbleToPlacement()
                }
            }.launchIn(s)
    }

    override fun detach() {
        hideAll()
        scope?.cancel()
        scope = null
        bubbleHost = null
        panelHost = null
        nudgeHost = null
        service = null
        actions = null
        hiddenSessions.clear()
    }

    override fun showBubble(app: ShortVideoApp, sessionId: Long, count: Int, allowance: Int?) {
        bubble = Bubble(app, sessionId, count, allowance)
        if (sessionId in hiddenSessions || !bubbleEnabled) return
        ensureBubbleWindow()
    }

    override fun updateBubble(app: ShortVideoApp, count: Int, allowance: Int?) {
        val current = bubble ?: return
        if (current.app == app) bubble = current.copy(count = count, allowance = allowance)
    }

    override fun hideAll() {
        closeBubbleAndPanel()
        hideNudge()
        bubble = null
    }

    override fun showNudge(app: ShortVideoApp, countToday: Int, allowance: Int?, snoozesLeft: Int) {
        nudge = Nudge(app, countToday, allowance, snoozesLeft)
        val svc = service ?: return
        val host = nudgeHost ?: return
        if (host.isShown) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        host.show(params) {
            val current = nudge
            if (current != null) {
                Themed {
                    ReelNudgeContent(
                        app = current.app,
                        count = current.count,
                        limit = current.allowance,
                        snoozesLeft = current.snoozesLeft,
                        snoozeSize = CounterConfig().snoozeSize,
                        onClose = {
                            hideNudge()
                            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        },
                        onSnooze = { actions?.snooze(current.app) },
                    )
                }
            }
        }
    }

    override fun hideNudge() {
        nudgeHost?.hide()
        nudge = null
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        AwakenTheme(darkTheme = look.dark, presentation = look.presentation, reduceMotion = look.reduceMotion, content = content)
    }

    // --- bubble ---

    private fun density(): Float = service?.resources?.displayMetrics?.density ?: 1f

    private fun px(dp: Dp): Int = (dp.value * density()).toInt()

    private fun bubbleSize(): Int = px(AwakenDimens().avatar)

    private fun screenWidth(): Int = service?.resources?.displayMetrics?.widthPixels ?: 0

    private fun usableTop(): Int = px(TOP_INSET)

    private fun usableBottom(): Int = (service?.resources?.displayMetrics?.heightPixels ?: 0) - px(BOTTOM_INSET)

    private fun ensureBubbleWindow() {
        val host = bubbleHost ?: return
        if (host.isShown) return
        val size = bubbleSize()
        val margin = px(EDGE_MARGIN)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = BubbleGeometry.x(placement.edge, screenWidth(), size, margin)
            y = BubbleGeometry.y(placement.yFraction, usableTop(), usableBottom(), size)
        }
        bubbleParams = params
        host.show(params) {
            val current = bubble
            if (current != null) {
                Themed {
                    val progress = current.allowance?.let { (current.count.toFloat() / it).coerceIn(0f, 1f) }
                    ReelBubble(
                        count = current.count,
                        progress = progress,
                        over = current.allowance != null && current.count >= current.allowance,
                        description = lexString(R.string.reel_cd_bubble, lexString(current.app.labelRes()), current.count),
                        modifier = Modifier.bubbleGestures(),
                    )
                }
            }
        }
    }

    private fun Modifier.bubbleGestures(): Modifier = this
        .pointerInput(Unit) {
            detectTapGestures(onTap = { togglePanel() }, onLongPress = { hideForSession() })
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragging = true },
                onDrag = { change, drag ->
                    change.consume()
                    moveBubbleBy(drag.x.toInt(), drag.y.toInt())
                },
                onDragEnd = { dropBubble() },
                onDragCancel = { dropBubble() },
            )
        }

    private fun moveBubbleBy(dx: Int, dy: Int) {
        val params = bubbleParams ?: return
        params.x += dx
        params.y += dy
        bubbleHost?.update(params)
    }

    private fun dropBubble() {
        dragging = false
        val params = bubbleParams ?: return
        placement = BubbleGeometry.snap(params.x, params.y, screenWidth(), usableTop(), usableBottom(), bubbleSize())
        moveBubbleToPlacement()
        val saved = placement
        scope?.launch { settings.setBubblePosition(saved.edge, saved.yFraction) }
    }

    private fun moveBubbleToPlacement() {
        val params = bubbleParams ?: return
        val size = bubbleSize()
        params.x = BubbleGeometry.x(placement.edge, screenWidth(), size, px(EDGE_MARGIN))
        params.y = BubbleGeometry.y(placement.yFraction, usableTop(), usableBottom(), size)
        bubbleHost?.update(params)
    }

    private fun hideForSession() {
        val current = bubble ?: return
        hiddenSessions += current.sessionId
        closeBubbleAndPanel()
        val svc = service ?: return
        Toast.makeText(svc, LexResources(svc.resources, Lexicon.of(look.presentation)).getString(R.string.reel_hidden_toast), Toast.LENGTH_SHORT).show()
    }

    private fun closeBubbleAndPanel() {
        closePanel()
        bubbleHost?.hide()
        bubbleParams = null
    }

    // --- panel ---

    private fun togglePanel() {
        if (panelHost?.isShown == true) closePanel() else openPanel()
    }

    private fun openPanel() {
        val svc = service ?: return
        val host = panelHost ?: return
        val anchor = bubbleParams ?: return
        val width = px(PANEL_WIDTH)
        val size = bubbleSize()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (placement.edge == BubbleEdge.END) anchor.x - width - px(EDGE_MARGIN) else anchor.x + size + px(EDGE_MARGIN)
            y = anchor.y
        }
        panelJob?.cancel()
        panelJob = scope?.launch {
            val zone = clock.zone()
            val hour = dayStart.dayStartHour.first().coerceIn(0, 23)
            val day = GameDay(zone, LocalTime.of(hour, 0)).dayOf(clock.now())
            launch {
                repository.observeDay(day).collect { totals ->
                    panelRows = totals.filter { it.count > 0 || it.seconds > 0 }
                        .sortedByDescending { it.count }
                        .map { ReelPanelRow(it.app, it.count, StatsCalculator.minutes(it.seconds)) }
                }
            }
            delay(PANEL_AUTO_CLOSE_MILLIS)
            closePanel()
        }
        host.show(params) { Themed { ReelPanelContent(rows = panelRows, onClose = { closePanel() }) } }
    }

    private fun closePanel() {
        panelJob?.cancel()
        panelJob = null
        panelHost?.hide()
    }

    private fun UiPrefs.toLook(service: AccessibilityService): Look {
        val dark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        return Look(dark, presentationMode ?: PresentationMode.CLEAN, reduceMotion)
    }

    private companion object {
        val TOP_INSET = 32.dp
        val BOTTOM_INSET = 48.dp
        val EDGE_MARGIN = 8.dp
        val PANEL_WIDTH = 260.dp
        const val PANEL_AUTO_CLOSE_MILLIS = 5_000L
    }
}
