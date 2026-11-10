package com.assismohanty.awaken.core.protect

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.db.entity.LockdownSessionEntity
import com.assismohanty.awaken.core.data.repo.UiPrefs
import com.assismohanty.awaken.core.domain.LockdownController
import com.assismohanty.awaken.core.domain.LockdownTick
import com.assismohanty.awaken.feature.protect.LockdownBlockContent
import java.time.Duration
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.repo.LexiconSource
import com.assismohanty.awaken.core.data.repo.PauseStore
import com.assismohanty.awaken.core.data.repo.ThemeMode
import com.assismohanty.awaken.core.data.repo.UiPrefsRepository
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.lock.BlockOverlay
import com.assismohanty.awaken.engine.PauseRules
import com.assismohanty.awaken.reelcounter.service.CounterRunner
import com.assismohanty.awaken.engine.PauseVariant
import com.assismohanty.awaken.feature.protect.PauseOverlayContent
import com.assismohanty.awaken.feature.protect.PauseOverlayUiState
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Pause's foreground service (GD §19.1). It exists only while there is something to watch: Pause on, an app chosen, both
 * permissions granted and inside the active hours ([ProtectController] decides). Each look at the usage log is
 * [PauseWatcher.poll]; this class owns the timing, the screen-off pause in watching, the phone-call exception and the
 * pause window itself. It reads package names only and stores nothing but the finished pauses.
 */
@AndroidEntryPoint
class ProtectService : Service() {
    @Inject lateinit var watcher: PauseWatcher

    @Inject lateinit var controller: ProtectController

    @Inject lateinit var notifications: ProtectNotifications

    @Inject lateinit var store: PauseStore

    @Inject lateinit var uiPrefs: UiPrefsRepository

    @Inject lateinit var words: LexiconSource

    @Inject lateinit var clock: Clock

    @Inject lateinit var reelCounter: CounterRunner

    @Inject lateinit var lockdownWatcher: LockdownWatcher

    @Inject lateinit var lockdownController: LockdownController

    private var audioFocus: AudioFocusRequest? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var overlay: BlockOverlay<Shown>? = null
    private var shown: Shown? = null
    private val active = MutableStateFlow(true)

    // Lockdown (GD §19.2): the running session as the database last said, the block screen, and what it shows.
    @Volatile private var lockdownSession: LockdownSessionEntity? = null
    private var lockdownOverlay: BlockOverlay<CoverShown>? = null
    private var postedFor: String? = null
    private var lastPrefs: UiPrefs = UiPrefs()
    private var cachedPackage: String? = null
    private var cachedLabel: String = ""
    private var cachedIcon: ImageBitmap? = null

    /** The block screen's content: what is covered, how long is left, and the look it is drawn in. */
    private data class CoverShown(val label: String, val icon: ImageBitmap?, val remaining: Duration, val darkTheme: Boolean, val reduceMotion: Boolean)

    /** The pause window's content: the screen state plus the look it is drawn in. */
    private data class Shown(val ui: PauseOverlayUiState, val darkTheme: Boolean, val reduceMotion: Boolean)

    private val screenState = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            active.value = isActive()
        }
    }

    private val screen = object : PauseScreen {
        override fun show(request: PauseRequest) {
            holdBehind(true)
            scope.launch { present(request) }
        }

        override fun hide() {
            shown = null
            overlay?.hide()
            holdBehind(false)
        }

        override fun goHome() {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(home) }.onFailure { Timber.w(it, "Could not go home from the pause screen") }
        }
    }

    private val lockdownScreen = object : LockdownScreen {
        override fun cover(cover: LockdownCover) {
            holdBehind(true)
            if (cachedPackage != cover.packageName) {
                cachedPackage = cover.packageName
                cachedLabel = labelOf(cover.packageName)
                cachedIcon = runCatching { packageManager.getApplicationIcon(cover.packageName).toBitmap().asImageBitmap() }.getOrNull()
            }
            lockdownOverlay?.show(CoverShown(cachedLabel, cachedIcon, cover.remaining, isDark(lastPrefs.themeMode), lastPrefs.reduceMotion))
        }

        override fun uncover() {
            lockdownOverlay?.hide()
            holdBehind(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller.onServiceChanged(true)
        overlay = BlockOverlay<Shown>(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, onBackKey = ::notNow) { model ->
            AwakenTheme(darkTheme = model.darkTheme, reduceMotion = model.reduceMotion) {
                PauseOverlayContent(model.ui, onNotNow = ::notNow, onContinue = ::continueIn, onIntention = ::pick)
            }
        }
        lockdownOverlay = BlockOverlay<CoverShown>(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, onBackKey = ::openSession) { model ->
            AwakenTheme(darkTheme = model.darkTheme, reduceMotion = model.reduceMotion) {
                LockdownBlockContent(model.label, model.icon, model.remaining, onBackToSession = ::openSession)
            }
        }
        scope.launch { uiPrefs.prefs.collect { lastPrefs = it } }
        scope.launch {
            lockdownController.observeRunning().collect { session ->
                lockdownSession = session
                if (session?.id != postedFor) {
                    postedFor = session?.id
                    postForeground(session)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenState, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        active.value = isActive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching { foreground(lockdownSession) }
        if (started.isFailure) {
            Timber.w(started.exceptionOrNull(), "Pause could not go to the foreground")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_TURN_OFF) {
            scope.launch {
                store.setEnabled(false)
                controller.refresh()
            }
        } else {
            startLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenState) }
        scope.cancel()
        overlay?.hide()
        overlay = null
        lockdownOverlay?.hide()
        lockdownOverlay = null
        holdBehind(false)
        controller.onServiceChanged(false)
        super.onDestroy()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            try {
                watch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "The Pause loop failed")
                stopSelf()
            }
        }
    }

    /**
     * The loop (docs/protect/PLAN.md §1.2): nothing while the screen is off or locked; a fast look for a while after an
     * app change and while the pause is up; a slow look otherwise. A call in progress never gets a pause.
     */
    private suspend fun watch() {
        var fastUntil = Instant.MIN
        var nextCheck = Instant.MIN
        var lockdownUp = false
        while (true) {
            if (!active.value) {
                watcher.onScreenOff(screen)
                lockdownWatcher.onScreenOff(lockdownScreen)
                active.first { it }
                continue
            }
            val now = clock.now()
            if (lockdownSession != null) {
                // A Lockdown covers apps itself, so Pause stays out of the way; it looks fast, because a covered app must not linger.
                watcher.dismiss(screen)
                if (!lockdownUp) {
                    lockdownUp = true
                    if (lockdownEnded(lockdownWatcher.start(lockdownScreen))) {
                        lockdownUp = false
                        continue
                    }
                }
                if (!inCall() && lockdownEnded(lockdownWatcher.poll(lockdownScreen))) lockdownUp = false
                delay(FAST_MS)
                continue
            }
            lockdownUp = false
            if (inCall()) {
                watcher.dismiss(screen)
            } else {
                when (val result = watcher.poll(screen)) {
                    PollResult.NoAccess -> {
                        controller.refresh()
                        return
                    }
                    is PollResult.Ok -> if (result.changed) fastUntil = now.plusMillis(FAST_WINDOW_MS)
                }
            }
            if (!now.isBefore(nextCheck)) {
                nextCheck = now.plusMillis(CHECK_MS)
                controller.refresh()
            }
            delay(if (watcher.isShowing || now.isBefore(fastUntil)) FAST_MS else STEADY_MS)
        }
    }

    /** A Lockdown look is over for good (finished, broke, or is gone): say so if the app is closed, and let the service settle. */
    private fun lockdownEnded(tick: LockdownTick): Boolean {
        if (tick is LockdownTick.Running) return false
        when (tick) {
            is LockdownTick.Completed -> notifications.lockdownEnded(completed = true)
            is LockdownTick.Broken -> notifications.lockdownEnded(completed = false)
            else -> Unit
        }
        scope.launch { controller.refresh() }
        return true
    }

    /** [Back to session]: AWAKEN's own session screen, which is never covered. */
    private fun openSession() {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(open) }.onFailure { Timber.w(it, "Could not open the session from the block screen") }
    }

    private fun foreground(session: LockdownSessionEntity?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val notification = if (session != null) {
            notifications.lockdownRunning(clock.now().plus(lockdownController.remaining(session)))
        } else {
            notifications.running(turnOffIntent())
        }
        ServiceCompat.startForeground(this, ProtectNotifications.NOTIFICATION_ID, notification, type)
    }

    /** Swaps the ongoing notice when a Lockdown starts or ends. */
    private fun postForeground(session: LockdownSessionEntity?) {
        runCatching { foreground(session) }.onFailure { Timber.w(it, "Could not update the Pause notice") }
    }

    private suspend fun present(request: PauseRequest) {
        val prefs = uiPrefs.prefs.first()
        if (!watcher.isShowing) return
        val resources = words.resources(this)
        val lines = resources.getStringArray(R.array.pause_lines)
        val line = request.plan.variants.filterIsInstance<PauseVariant.Line>().firstOrNull()?.let { lines.getOrNull(it.index) }
        val seconds = request.plan.variants.filterIsInstance<PauseVariant.Breathing>().firstOrNull()?.seconds ?: 0
        val ui = PauseOverlayUiState(
            appLabel = labelOf(request.packageName),
            appIcon = iconOf(request.packageName),
            seconds = seconds,
            line = line,
            opensToday = request.opensToday,
            requireTap = request.requireTap,
            askIntention = request.plan.variants.any { it is PauseVariant.Intention },
        )
        val next = Shown(ui, isDark(prefs.themeMode), prefs.reduceMotion)
        shown = next
        overlay?.show(next)
    }

    /**
     * While the pause screen is up the app under it must be quiet and must not be counted: AWAKEN takes audio focus for the
     * moment (a well-behaved video app pauses when it loses focus; one that ignores focus keeps playing), and Reel counter
     * stops counting until the pause is gone.
     */
    private fun holdBehind(on: Boolean) {
        reelCounter.setHeld(on)
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

    private fun notNow() {
        scope.launch { watcher.onNotNow(screen) }
    }

    private fun continueIn() {
        scope.launch { watcher.onContinue(screen) }
    }

    private fun pick(intention: PauseRules.Intention) {
        watcher.onIntention(intention)
        val current = shown ?: return
        val next = current.copy(ui = current.ui.copy(chosen = intention))
        shown = next
        overlay?.show(next)
    }

    private fun isActive(): Boolean {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        return interactive && !locked
    }

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun turnOffIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, ProtectService::class.java).setAction(ACTION_TURN_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private suspend fun iconOf(pkg: String): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching { packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
    }

    private fun isDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private companion object {
        const val ACTION_TURN_OFF = "com.assismohanty.awaken.PAUSE_TURN_OFF"

        /** A look every 400 ms for 15 s after an app change and while the pause is up; every 1.5 s otherwise (PLAN §1.2). */
        const val FAST_MS = 400L
        const val STEADY_MS = 1_500L
        const val FAST_WINDOW_MS = 15_000L

        /** How often the service asks the controller whether the active hours are still open and the permissions still on. */
        const val CHECK_MS = 30_000L
    }
}
