package com.assismohanty.awaken.reelcounter.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.service.OverlaySink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every overlay window the service draws. All windows are `TYPE_ACCESSIBILITY_OVERLAY`, so no
 * SYSTEM_ALERT_WINDOW permission is needed. Phase 2 only draws the debug chip; the bubble, panel and
 * nudge arrive in Phases 3 and 5. Main thread only.
 */
@Singleton
class OverlayController @Inject constructor() : OverlaySink {
    private var service: AccessibilityService? = null
    private var chipHost: OverlayWindowHost? = null
    private var chipLabel by mutableStateOf("")
    private var chipClick: () -> Unit = {}

    fun attach(service: AccessibilityService) {
        this.service = service
        chipHost = OverlayWindowHost(service)
    }

    fun detach() {
        hideAll()
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
            // Debug-only chip; the designsystem tokens replace these colours in Phase 3.
            Text(
                text = chipLabel,
                color = Color.White,
                fontSize = 13.sp,
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

    override fun showBubble(app: ShortVideoApp) = Unit

    override fun hideAll() {
        hideDebugChip()
    }

    override fun showNudge(app: ShortVideoApp, countToday: Int, snoozesLeft: Int) = Unit

    override fun hideNudge() = Unit
}
