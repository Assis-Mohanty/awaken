package com.assismohanty.awaken.reelcounter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.model.Bounds
import com.assismohanty.awaken.reelcounter.core.model.NodeSnapshot
import com.assismohanty.awaken.reelcounter.core.model.NodeSource
import com.assismohanty.awaken.reelcounter.core.model.TreeNodeSource
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.service.OverlaySink
import com.assismohanty.awaken.reelcounter.service.WidgetSink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Instant

val T0: Instant = Instant.parse("2026-11-01T10:00:00Z")
val SCREEN = Bounds(0, 0, 1080, 2400)
const val IG_PKG = "com.instagram.android"

/** In-memory Room whose work runs inline, so tests do not depend on real threads. */
fun newDatabase(context: Context): ReelDatabase =
    Room.inMemoryDatabaseBuilder(context, ReelDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()

fun newDataStore(dispatcher: CoroutineDispatcher, file: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { file },
    )

class FakeOverlay : OverlaySink {
    val calls = mutableListOf<String>()
    override fun showBubble(app: ShortVideoApp) { calls += "bubble:$app" }
    override fun hideAll() { calls += "hideAll" }
    override fun showNudge(app: ShortVideoApp, countToday: Int, snoozesLeft: Int) { calls += "nudge:$app:$countToday:$snoozesLeft" }
    override fun hideNudge() { calls += "hideNudge" }
}

class FakeWidget : WidgetSink {
    var requests = 0
    override fun request() { requests++ }
}

/** An Instagram-like screen: a full-screen scrollable pager with one full-screen child. */
fun pagerScreen(pkg: String = IG_PKG): NodeSource = TreeNodeSource(
    root = NodeSnapshot(
        key = "root", viewId = null, className = "FrameLayout", bounds = SCREEN, scrollable = false, visibleToUser = true,
        children = listOf(
            NodeSnapshot(
                key = "pager", viewId = "$pkg:id/pager", className = "ViewPager", bounds = SCREEN, scrollable = true,
                visibleToUser = true,
                children = listOf(NodeSnapshot("c1", null, "View", SCREEN, false, true)),
            ),
        ),
    ),
    packageName = pkg,
    screen = SCREEN,
)

fun plainScreen(pkg: String = IG_PKG): NodeSource = TreeNodeSource(
    root = NodeSnapshot("root", null, "FrameLayout", SCREEN, false, true),
    packageName = pkg,
    screen = SCREEN,
)
