package com.assismohanty.awaken.core.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.assismohanty.awaken.MainActivity
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.work.ACTION_START_FOCUS
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The "Start focus" Quick Settings tile (GD §16.3). One tap starts the last-used session; while one runs, the tile shows the time
 * left. There is no foreground service: the tile is drawn when the shade opens and when the app tells it to refresh
 * ([requestRefresh]), and the session itself still runs on timestamps like every other (ARCH §3.3).
 */
class FocusTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            try {
                render()
            } catch (e: Exception) {
                Timber.e(e, "Drawing the focus tile failed")
            }
        }
    }

    /** Always asks the app to start the last session; the app decides (it opens the running one instead if one runs). */
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // the Intent form is the only one below API 34
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_START_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapseApi34(intent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startActivityAndCollapseApi34(intent: Intent) {
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val entry = EntryPointAccessors.fromApplication(applicationContext, FocusSurfaceEntryPoint::class.java)
        val surface = entry.surface().read()
        val now = entry.clock().now()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_gate)
        when (surface) {
            is FocusSurface.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.focus_tile_label)
                setSubtitle(tile, surface.last?.let { lastFocusLine(this, it, surface.subjectName) } ?: getString(R.string.focus_start_pick))
            }
            is FocusSurface.Running -> {
                tile.state = Tile.STATE_ACTIVE
                val left = surface.endsAt?.let { Duration.between(now, it).coerceAtLeast(Duration.ZERO) }
                tile.label = if (left != null) getString(R.string.focus_tile_left, clock(left)) else getString(R.string.focus_tile_minutes, Duration.between(surface.startedAt, now).toMinutes())
                setSubtitle(tile, getString(R.string.focus_tile_running))
            }
            is FocusSurface.Break -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = if (surface.over) getString(R.string.focus_start_break_over) else getString(R.string.focus_tile_break_left, clock(Duration.between(now, surface.endsAt).coerceAtLeast(Duration.ZERO)))
                setSubtitle(tile, getString(R.string.focus_tile_round, surface.round, surface.rounds))
            }
        }
        tile.updateTile()
    }

    private fun setSubtitle(tile: Tile, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = text
    }

    private fun clock(duration: Duration): String = "%d:%02d".format(duration.seconds / SECONDS_PER_MINUTE, duration.seconds % SECONDS_PER_MINUTE)

    companion object {
        private const val SECONDS_PER_MINUTE = 60L

        /** Asks the system to redraw the tile soon (a session started or ended). Harmless when the tile isn't added. */
        fun requestRefresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, FocusTileService::class.java)) }
        }
    }
}
