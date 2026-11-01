package com.assismohanty.awaken.reelcounter.overlay

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * One Compose window added to the screen by the accessibility service. A window outside an activity has no
 * lifecycle or saved-state owner, so this host supplies both. Must be used from the main thread.
 * Every window-manager call is wrapped: the service may already be gone.
 */
internal class OverlayWindowHost(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: HostOwner? = null

    val isShown: Boolean get() = view != null

    fun show(params: WindowManager.LayoutParams, content: @Composable () -> Unit) {
        if (view != null) return
        val newOwner = HostOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent(content)
        }
        val added = runCatching { windowManager.addView(composeView, params) }.isSuccess
        if (added) {
            view = composeView
            owner = newOwner
        } else {
            newOwner.stop()
        }
    }

    fun update(params: WindowManager.LayoutParams) {
        val v = view ?: return
        runCatching { windowManager.updateViewLayout(v, params) }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
        owner?.stop()
        owner = null
    }

    private class HostOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
        }

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
