package com.assismohanty.awaken.core.lock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.feature.focuslock.FocusLockOverlayContent
import java.time.Duration
import timber.log.Timber

/** What the cover shows; the service pushes a new one every second so the clock runs. */
data class OverlayModel(
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val time: Duration,
    val countsUp: Boolean,
    val darkTheme: Boolean,
)

/**
 * The full-screen cover Focus Lock draws over a blocked app (GD 8.2). It is an accessibility overlay window added by the
 * service itself, so it needs no "draw over other apps" permission. It consumes every touch and turns Back into
 * [onBackKey]. [show] while it is already up only updates what it says; the window is never added twice.
 */
class BlockOverlay(
    private val context: Context,
    private val onBackToFocus: () -> Unit,
    private val onEndAndOpen: () -> Unit,
    private val onBackKey: () -> Unit,
) {
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var owner: OverlayOwner? = null
    private var model by mutableStateOf<OverlayModel?>(null)

    val isShowing: Boolean get() = root != null

    fun show(next: OverlayModel) {
        model = next
        if (root != null) return
        val lifecycleOwner = OverlayOwner()
        val layout = BackInterceptingLayout(context, onBackKey).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
        }
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val current = model
                if (current != null) {
                    AwakenTheme(darkTheme = current.darkTheme) {
                        FocusLockOverlayContent(
                            appLabel = current.appLabel,
                            appIcon = current.appIcon,
                            time = current.time,
                            countsUp = current.countsUp,
                            onBackToFocus = onBackToFocus,
                            onEndAndOpen = onEndAndOpen,
                        )
                    }
                }
            }
        }
        layout.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            windowManager.addView(layout, layoutParams())
        } catch (e: RuntimeException) {
            // The window can be refused (service tearing down, display gone): the lock simply does not cover this time.
            Timber.w(e, "Focus Lock cover could not be added")
            lifecycleOwner.destroy()
            return
        }
        root = layout
        owner = lifecycleOwner
        lifecycleOwner.resume()
        layout.requestFocus()
    }

    fun hide() {
        val view = root ?: return
        root = null
        model = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Timber.w(e, "Focus Lock cover could not be removed")
        }
        owner?.destroy()
        owner = null
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Focusable on purpose: only a focused window receives Back. Laid out inside the system bars so navigation, the status bar and biometric prompts stay reachable.
        0,
        PixelFormat.OPAQUE,
    )

    /** Takes Back (and eats every touch) so nothing reaches the app underneath. */
    private class BackInterceptingLayout(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }

    /** Compose needs lifecycle, saved-state and view-model owners; a service window has none, so the cover brings its own. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
