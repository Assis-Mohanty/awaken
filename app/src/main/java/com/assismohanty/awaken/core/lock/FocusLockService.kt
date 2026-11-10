package com.assismohanty.awaken.core.lock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.feature.focuslock.FocusLockOverlayContent
import com.assismohanty.awaken.core.work.ACTION_OPEN_GATES
import com.assismohanty.awaken.core.work.GateCoordinator
import com.assismohanty.awaken.engine.BlockDecision

import com.assismohanty.awaken.engine.FocusLockRules
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.reelcounter.service.CounterRunner
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Focus Lock (GD 8.2). While a focus session runs it covers any app that is not allowed with [BlockOverlay].
 *
 * Privacy by construction: it listens to "a window opened" events only (see `focus_lock_service.xml`: no content
 * retrieval, no key events, no gestures), reads nothing from an event but the package name, stores nothing about it and
 * sends nothing anywhere. It is scoped too ([applyScope]): AWAKEN alone while no session runs, and never a payment app.
 */
@AndroidEntryPoint
class FocusLockService : AccessibilityService() {
    @Inject lateinit var state: FocusLockState

    @Inject lateinit var clock: Clock

    @Inject lateinit var coordinator: GateCoordinator

    @Inject lateinit var allowed: AlwaysAllowed

    @Inject lateinit var coverable: CoverableApps

    @Inject lateinit var reelCounter: CounterRunner

    private var scope: CoroutineScope? = null
    private var overlay: BlockOverlay<OverlayModel>? = null
    private var tick: Job? = null
    private var snapshot = LockSnapshot()

    /** Screen off or the lock screen: never leave the cover over the keyguard, fingerprint or PIN. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lastPackage = null
            uncover()
        }
    }

    /** An app was installed, removed or updated while a session runs: the apps to listen to are worked out again. */
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            allowed.invalidate()
            coverable.invalidate()
            applyScope(force = true)
        }
    }
    private var packageReceiverOn = false
    private var scopeWide: Boolean? = null
    private var scopePackages: Set<String> = emptySet()

    /** The app currently covered; null when nothing is. */
    private var blockedPackage: String? = null
    private var lastPackage: String? = null
    private var lastEventTime = 0L
    private var audioFocus: AudioFocusRequest? = null
    private var lastFront = 0L
    private val icons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        overlay = BlockOverlay<OverlayModel>(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, onBackKey = ::backKey) { model ->
            AwakenTheme(darkTheme = model.darkTheme) {
                FocusLockOverlayContent(
                    appLabel = model.appLabel,
                    appIcon = model.appIcon,
                    time = model.time,
                    countsUp = model.countsUp,
                    onBackToFocus = ::backToFocus,
                    onEndAndOpen = ::endAndOpen,
                )
            }
        }
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        state.onServiceConnected()
        // The gate ending, pausing or being cleared uncovers the app at once, without waiting for another window event.
        serviceScope.launch {
            state.snapshot.collect {
                snapshot = it
                applyScope()
                refresh()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        // AWAKEN itself (and the cover, which is ours) neither shows nor hides the cover.
        if (pkg == packageName) return
        if (isLocked()) {
            uncover()
            return
        }
        if (pkg != null && pkg == lastPackage && event.eventTime - lastEventTime < DEBOUNCE_MS) return
        lastPackage = pkg
        lastEventTime = event.eventTime
        if (state.decide(pkg, snapshot, clock.now()) == BlockDecision.BLOCK && pkg != null) cover(pkg) else uncover()
    }

    /**
     * Who the service listens to. With no session running it is AWAKEN alone, so UPI and bank apps see a service that
     * watches one app. While a session runs it is every app but the payment apps (never an empty list: empty means every
     * app to Android). Payment apps send no events, so they are never covered. FocusLockRules decides the rest.
     */
    private fun applyScope(force: Boolean = false) {
        val wide = snapshot.enabled && state.gate(snapshot.run, clock.now()).let { it.running && !it.paused }
        val target = if (wide) {
            FocusLockRules.watchScope(allowed.watchCandidates(), allowed.paymentApps(), packageName)
        } else {
            setOf(packageName)
        }
        setPackageReceiver(wide)
        if (!force && scopeWide == wide && scopePackages == target) return
        val info = serviceInfo ?: return
        info.packageNames = target.toTypedArray()
        serviceInfo = info
        scopeWide = wide
        scopePackages = target
        if (!wide) {
            lastPackage = null
            uncover()
        }
    }

    private fun setPackageReceiver(on: Boolean) {
        if (on == packageReceiverOn) return
        packageReceiverOn = on
        if (on) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packagesChanged, filter)
        } else {
            runCatching { unregisterReceiver(packagesChanged) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        runCatching { unregisterReceiver(screenOff) }
        setPackageReceiver(false)
        uncover()
        scope?.cancel()
        scope = null
        overlay = null
        state.onServiceGone()
    }

    private fun cover(pkg: String) {
        blockedPackage = pkg
        holdBehind(true)
        bringAwakenToFront()
        refresh()
        if (tick == null) {
            tick = scope?.launch {
                while (true) {
                    delay(TICK_MS)
                    refresh()
                }
            }
        }
        if (icons[pkg] == null && !icons.containsKey(pkg)) {
            scope?.launch {
                val icon = withContext(Dispatchers.Default) {
                    runCatching { packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
                }
                icons[pkg] = icon
                if (blockedPackage == pkg) refresh()
            }
        }
    }

    private fun uncover() {
        blockedPackage = null
        tick?.cancel()
        tick = null
        overlay?.hide()
        holdBehind(false)
    }

    /**
     * The cover alone leaves the app under it running, so a reel keeps playing. AWAKEN takes audio focus (a video app pauses
     * when it loses it) and Reel counter stops counting until the cover is gone. Same as the pause screen.
     */
    private fun holdBehind(on: Boolean) {
        reelCounter.setHeld(on, owner = "focuslock")
        val audio = getSystemService(AudioManager::class.java) ?: return
        if (on) {
            if (audioFocus != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) audioFocus = request
        } else {
            audioFocus?.let { audio.abandonAudioFocusRequest(it) }
            audioFocus = null
        }
    }

    /** AWAKEN's own window comes forward under the cover so the covered app stops (at most once a second). */
    private fun bringAwakenToFront() {
        val now = clock.now().toEpochMilli()
        if (now - lastFront < 1_000) return
        lastFront = now
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }.onFailure { Timber.w(it, "Could not bring AWAKEN to the front") }
    }

    /** Re-decides for the covered app and re-draws the cover with the clock as it is now. */
    private fun refresh() {
        val pkg = blockedPackage ?: return
        if (isLocked()) {
            uncover()
            return
        }
        val now = clock.now()
        val run = snapshot.run
        if (run == null || state.decide(pkg, snapshot, now) != BlockDecision.BLOCK) {
            uncover()
            return
        }
        val stopwatch = run.mode == GateMode.STOPWATCH
        val time = if (stopwatch) GateRunMath.elapsed(run.timing, now) else GateRunMath.remaining(run.timing, now)
        overlay?.show(
            OverlayModel(
                appLabel = labelOf(pkg),
                appIcon = icons[pkg],
                time = time,
                countsUp = stopwatch,
                darkTheme = isDark(snapshot.themeMode),
            ),
        )
    }

    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun isDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** [Back to focus]: AWAKEN's Focus Gates screen, or the home screen when it cannot be opened. */
    private fun backToFocus() {
        val opened = runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(ACTION_OPEN_GATES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }.onFailure { Timber.w(it, "Could not open AWAKEN from the cover") }.isSuccess
        if (!opened) performGlobalAction(GLOBAL_ACTION_HOME)
        uncover()
    }

    /** Back on the cover: leave the blocked app for the home screen, then drop the cover. */
    private fun backKey() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        uncover()
    }

    /** [End session and open]: the session collapses with no reward (GD 8.2), then the app opens. */
    private fun endAndOpen() {
        val pkg = blockedPackage ?: return
        if (isLocked()) {
            uncover()
            return
        }
        scope?.launch {
            runCatching { coordinator.endEarly() }.onFailure { Timber.e(it, "Ending the session early failed") }
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Timber.w(it, "Could not open the app after ending the session") }
            }
            uncover()
        }
    }

    private companion object {
        /** Window events for the same app arrive in bursts; one decision per half second is plenty. */
        const val DEBOUNCE_MS = 500L
        const val TICK_MS = 1_000L
    }
}
