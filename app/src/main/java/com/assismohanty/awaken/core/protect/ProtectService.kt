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
import com.assismohanty.awaken.core.domain.ShieldStatus
import com.assismohanty.awaken.engine.LockdownRules
import com.assismohanty.awaken.feature.protect.LockdownSessionScreen
import com.assismohanty.awaken.feature.protect.LockdownSessionUiState
import com.assismohanty.awaken.feature.protect.LockdownSlotUi
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.combine
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
import com.assismohanty.awaken.core.work.ACTION_OPEN_GATES
import com.assismohanty.awaken.core.work.GateCoordinator
import com.assismohanty.awaken.core.lock.FocusLockState
import com.assismohanty.awaken.feature.focuslock.FocusLockOverlayContent
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
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Waits for the screen to be on and unlocked. [active] follows three broadcasts; one can be missed, so the state is read
 * again with [read] every [recheckMs] instead of waiting for a broadcast that may never come.
 */
internal suspend fun awaitActive(active: MutableStateFlow<Boolean>, recheckMs: Long, read: () -> Boolean) {
    while (withTimeoutOrNull(recheckMs) { active.first { it } } == null) active.value = read()
}

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

    @Inject lateinit var appIcons: AppIcons

    @Inject lateinit var focusWatcher: FocusLockWatcher

    @Inject lateinit var focusState: FocusLockState

    @Inject lateinit var coordinator: GateCoordinator

    private var audioFocus: AudioFocusRequest? = null
    private val holds = HashSet<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var overlay: BlockOverlay<Shown>? = null
    private var shown: Shown? = null
    private val active = MutableStateFlow(true)

    // Lockdown (GD §19.2): the running session as the database last said, the block screen, and what it shows.
    @Volatile private var lockdownSession: LockdownSessionEntity? = null
    private var lockdownOverlay: BlockOverlay<CoverShown>? = null
    private var focusOverlay: BlockOverlay<FocusShown>? = null
    private var focusBlocked: String? = null
    private var postedFor: String? = null
    private var postedFocus = false
    private var lastPrefs: UiPrefs = UiPrefs()
    @Volatile private var shields = ShieldStatus(0, false)
    private var openedAllowedAt: Instant = Instant.EPOCH
    private var pausedPackage: String? = null
    private var lastAwakenOpen: Instant = Instant.EPOCH

    /** The session screen drawn over everything: the state it shows, and the look it is drawn in. */
    private data class CoverShown(val ui: LockdownSessionUiState, val darkTheme: Boolean, val reduceMotion: Boolean)

    /** The Focus Lock cover's content: the app covered and the session clock, plus the look it is drawn in. */
    private data class FocusShown(val cover: FocusCover, val label: String, val icon: ImageBitmap?, val darkTheme: Boolean, val reduceMotion: Boolean)

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
            pausedPackage = request.packageName
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
            // Just after the hunter opened an allowed app from the screen, the launcher is still the app in front: leave it be.
            if (Duration.between(openedAllowedAt, clock.now()).toMillis() < OPEN_GRACE_MS) return
            // A cover that failed to appear holds nothing; the watcher asks for it again on its next look.
            if (lockdownOverlay?.show(CoverShown(sessionUi(cover.remaining), isDark(lastPrefs.themeMode), lastPrefs.reduceMotion)) != true) {
                Timber.w("The Lockdown screen could not be shown")
                holdBehind(false, "lockdown")
                return
            }
            holdBehind(true, "lockdown")
            // Same as Pause: the overlay alone leaves the app playing, so AWAKEN's own session screen comes forward and the app stops.
            if (cover.packageName != null && cover.packageName != packageName) bringAwakenToFront()
        }

        override fun uncover() {
            lockdownOverlay?.hide()
            holdBehind(false, "lockdown")
        }
    }

    private val focusScreen = object : FocusLockScreen {
        override fun cover(cover: FocusCover) {
            focusBlocked = cover.packageName
            // A cover that failed to appear holds nothing; the watcher asks for it again on its next look.
            if (focusOverlay?.show(FocusShown(cover, labelOf(cover.packageName), appIcons.icon(cover.packageName), isDark(lastPrefs.themeMode), lastPrefs.reduceMotion)) != true) {
                Timber.w("The Focus Lock cover could not be shown")
                holdBehind(false, "focus")
                return
            }
            holdBehind(true, "focus")
            // Same as Pause: the cover alone leaves the app playing, so AWAKEN comes forward under it and the app stops.
            if (cover.packageName != packageName) bringAwakenToFront()
        }

        override fun uncover() {
            focusBlocked = null
            focusOverlay?.hide()
            holdBehind(false, "focus")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = BlockOverlay<Shown>(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, onBackKey = ::notNow) { model ->
            AwakenTheme(darkTheme = model.darkTheme, reduceMotion = model.reduceMotion) {
                PauseOverlayContent(model.ui, onNotNow = ::notNow, onContinue = ::continueIn, onIntention = ::pick)
            }
        }
        // The session screen is drawn edge to edge over whatever is in front, and Back does nothing.
        lockdownOverlay = BlockOverlay<CoverShown>(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, onBackKey = {}, fullScreen = true) { model ->
            AwakenTheme(darkTheme = model.darkTheme, reduceMotion = model.reduceMotion) {
                LockdownSessionScreen(model.ui, onExit = ::leaveLockdown, onOpenApp = ::openAllowed, onCall = ::openDialer)
            }
        }
        focusOverlay = BlockOverlay<FocusShown>(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, onBackKey = ::focusBack) { model ->
            AwakenTheme(darkTheme = model.darkTheme, reduceMotion = model.reduceMotion) {
                FocusLockOverlayContent(
                    appLabel = model.label,
                    appIcon = model.icon,
                    time = model.cover.time,
                    countsUp = model.cover.countsUp,
                    onBackToFocus = ::focusBackToFocus,
                    onEndAndOpen = ::focusEndAndOpen,
                )
            }
        }
        scope.launch { uiPrefs.prefs.collect { lastPrefs = it } }
        scope.launch {
            focusState.snapshot.collect {
                focusWatcher.snapshot = it
                // The ongoing notice says what is being watched: swap it when a focus session starts or ends.
                val running = focusWatcher.sessionRunning()
                if (running != postedFocus) {
                    postedFocus = running
                    postForeground(lockdownSession)
                }
            }
        }
        scope.launch {
            combine(lockdownController.observeShields(), lockdownController.observeRunning()) { _, _ -> lockdownController.shieldStatus() }.collect { shields = it }
        }
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
            notifications.notStarted()
            stopSelf()
            return START_NOT_STICKY
        }
        controller.onServiceChanged(true)
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
        focusOverlay?.hide()
        focusOverlay = null
        holdBehind(false, "pause")
        holdBehind(false, "lockdown")
        holdBehind(false, "focus")
        controller.onServiceChanged(false)
        super.onDestroy()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            // One failed look must never end the watching (a Lockdown stops covering if it does): log it and look again.
            while (true) {
                try {
                    watch()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "The watch loop failed; looking again")
                    delay(RETRY_MS)
                }
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
                focusWatcher.onScreenOff(focusScreen)
                awaitActive(active, SCREEN_RECHECK_MS, ::isActive)
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
                if (inCall()) {
                    lockdownScreen.uncover()
                } else if (lockdownEnded(lockdownWatcher.poll(lockdownScreen))) {
                    lockdownUp = false
                }
                delay(LOCKDOWN_MS)
                continue
            }
            lockdownUp = false
            // A Focus Lock cover looks fast too: a covered app must not linger. It never waits for a call (the dialer is never covered).
            val focusRunning = focusWatcher.sessionRunning()
            if (focusRunning || focusWatcher.isCovering) {
                if (focusWatcher.poll(focusScreen) == PollResult.NoAccess) {
                    // The loop never ends while the service lives: the controller stops the service if access is really gone.
                    controller.refresh()
                    delay(STEADY_MS)
                    continue
                }
            }
            if (inCall()) {
                watcher.dismiss(screen)
            } else {
                when (val result = watcher.poll(screen)) {
                    PollResult.NoAccess -> {
                        controller.refresh()
                        delay(STEADY_MS)
                        continue
                    }
                    is PollResult.Ok -> if (result.changed) fastUntil = now.plusMillis(FAST_WINDOW_MS)
                }
            }
            if (!now.isBefore(nextCheck)) {
                nextCheck = now.plusMillis(CHECK_MS)
                controller.refresh()
            }
            delay(if (watcher.isShowing || focusRunning || now.isBefore(fastUntil)) FAST_MS else STEADY_MS)
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

    /** What the session screen shows now; the same numbers as the in-app one ([LockdownHostViewModel]). */
    private fun sessionUi(remaining: Duration): LockdownSessionUiState {
        val session = lockdownSession
        val chosen = session?.allowedPackages.orEmpty().sortedBy { labelOf(it).lowercase() }
        return LockdownSessionUiState(
            remainingMs = remaining.toMillis(),
            endsAtText = ENDS_AT.format(clock.now().plus(remaining).atZone(clock.zone())),
            slots = (0 until LockdownRules.MAX_APPS).map { i -> chosen.getOrNull(i)?.let { LockdownSlotUi(it, labelOf(it), appIcons.icon(it)) } ?: LockdownSlotUi(null, null, null) },
            shields = shields.shields,
            exit = shields.check,
        )
    }

    /** An allowed app from a slot: the screen steps aside, the watcher brings it back when the hunter leaves that app. */
    private fun openAllowed(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        openedAllowedAt = clock.now()
        lockdownScreen.uncover()
        runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Timber.w(it, "Could not open an allowed app") }
    }

    private fun openDialer() {
        openedAllowedAt = clock.now()
        lockdownScreen.uncover()
        runCatching { startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Timber.w(it, "Could not open the dialer") }
    }

    private fun leaveLockdown() {
        scope.launch { lockdownController.leaveWithShield() }
    }

    private fun foreground(session: LockdownSessionEntity?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val notification = if (session != null) {
            notifications.lockdownRunning(clock.now().plus(lockdownController.remaining(session)))
        } else if (focusWatcher.sessionRunning()) {
            notifications.focusRunning()
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
        if (overlay?.show(next) == true) {
            // The overlay alone leaves the app running (a reel keeps playing and nothing can mute it), so AWAKEN comes to the front under it.
            bringAwakenToFront()
        } else {
            // Nothing covers the app: clear the pause so the hold is let go and the next open is judged afresh.
            Timber.w("The pause screen could not be shown")
            watcher.dismiss(screen)
        }
    }

    /**
     * While the pause screen is up the app under it must be quiet and must not be counted: AWAKEN takes audio focus for the
     * moment (a well-behaved video app pauses when it loses focus; one that ignores focus keeps playing), and Reel counter
     * stops counting until the pause is gone.
     */
    private fun holdBehind(on: Boolean, owner: String = "pause") {
        reelCounter.setHeld(on, owner)
        if (on) holds.add(owner) else holds.remove(owner)
        val audio = getSystemService(AudioManager::class.java) ?: return
        if (holds.isNotEmpty()) {
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

    /** [Back to focus]: AWAKEN's Focus Gates screen, or the home screen when it cannot be opened. */
    private fun focusBackToFocus() {
        val opened = runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(ACTION_OPEN_GATES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }.onFailure { Timber.w(it, "Could not open AWAKEN from the cover") }.isSuccess
        if (!opened) screen.goHome()
        focusWatcher.dismissed(focusScreen)
    }

    /** Back on the cover: leave the blocked app for the home screen, then drop the cover. */
    private fun focusBack() {
        screen.goHome()
        focusWatcher.dismissed(focusScreen)
    }

    /** [End session and open]: the session collapses with no reward (GD 8.2), then the app opens. */
    private fun focusEndAndOpen() {
        val pkg = focusBlocked ?: return
        scope.launch {
            runCatching { coordinator.endEarly() }.onFailure { Timber.e(it, "Ending the session early failed") }
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { e -> Timber.w(e, "Could not open the app after ending the session") }
            }
            focusWatcher.dismissed(focusScreen)
        }
    }

    private fun notNow() {
        scope.launch { watcher.onNotNow(screen) }
    }

    private fun continueIn() {
        val app = pausedPackage
        scope.launch {
            watcher.onContinue(screen)
            // Continue: the app that was paused comes back to the front.
            app?.let { packageManager.getLaunchIntentForPackage(it) }?.let {
                runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { e -> Timber.w(e, "Could not reopen the paused app") }
            }
        }
    }

    /** AWAKEN's own window over the app in front, so that app stops (at most once a second). */
    private fun bringAwakenToFront() {
        val now = clock.now()
        if (Duration.between(lastAwakenOpen, now).toMillis() < REOPEN_MS) return
        lastAwakenOpen = now
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(open) }.onFailure { Timber.w(it, "Could not bring AWAKEN to the front") }
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

        /** After a failed look, wait this long before the next. */
        const val RETRY_MS = 1_000L

        /** After an allowed app is opened from the session screen, the screen waits this long before it can come back over it. */
        const val OPEN_GRACE_MS = 2_500L

        /** AWAKEN is brought to the front under a cover at most this often, so two quick opens do not fight. */
        const val REOPEN_MS = 1_000L

        /** While the screen is off, the screen state is read again this often in case a broadcast was missed. */
        const val SCREEN_RECHECK_MS = 5_000L

        /** Lockdown looks at the usage log this often: a gap here is time an app can show uncovered. */
        const val LOCKDOWN_MS = 250L
        val ENDS_AT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
