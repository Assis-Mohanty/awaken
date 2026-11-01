package com.assismohanty.awaken.reelcounter.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.service.OverlaySink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes the counter's overlay calls to the host app's [ReelOverlayUi] and owns the debug-only inspector chip.
 * Every window is `TYPE_ACCESSIBILITY_OVERLAY`, so no SYSTEM_ALERT_WINDOW permission is needed. Main thread only.
 */
@Singleton
class OverlayController @Inject constructor(private val ui: ReelOverlayUi) : OverlaySink {
    private var service: AccessibilityService? = null
    private var chipHost: OverlayWindowHost? = null
    private val main = Handler(Looper.getMainLooper())
    private var chipLabel by mutableStateOf("")
    private var chipClick: () -> Unit = {}

    fun attach(service: AccessibilityService, actions: ReelOverlayActions) {
        this.service = service
        chipHost = OverlayWindowHost(service)
        ui.attach(service, actions)
    }

    /** Called on the main thread by the service: hides everything now, not later, before the windows' owner is gone. */
    fun detach() {
        hideDebugChip()
        ui.hideAll()
        ui.detach()
        chipHost = null
        service = null
    }

    /** Debug inspector chip. Updating the label while it is shown just changes the text. */
    fun showDebugChip(label: String, onClick: () -> Unit) {
        val host = chipHost ?: return
        val svc = service ?: return
        chipLabel = label
        chipClick = onClick
        if (host.isShown) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (64 * svc.resources.displayMetrics.density).toInt()
        }
        host.show(params) {
            // Debug-only developer chip: not part of the product design, so it does not use AWAKEN's tokens.
            BasicText(
                text = chipLabel,
                style = TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .clickable { chipClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }

    fun hideDebugChip() {
        chipHost?.hide()
    }

    /** The counter reports from its own thread; windows can only be touched on the main thread. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    override fun showBubble(app: ShortVideoApp, sessionId: Long, count: Int, allowance: Int?) = onMain {
        ui.showBubble(app, sessionId, count, allowance)
    }

    override fun updateBubble(app: ShortVideoApp, count: Int, allowance: Int?) = onMain { ui.updateBubble(app, count, allowance) }

    override fun hideAll() = onMain {
        hideDebugChip()
        ui.hideAll()
    }

    override fun showNudge(app: ShortVideoApp, countToday: Int, allowance: Int?, snoozesLeft: Int) = onMain {
        ui.showNudge(app, countToday, allowance, snoozesLeft)
    }

    override fun hideNudge() = onMain { ui.hideNudge() }
}
