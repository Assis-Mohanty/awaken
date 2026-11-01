package com.assismohanty.awaken.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.assismohanty.awaken.core.data.di.ApplicationScope
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.ObserveTodayUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pushes the widget to match the game (ARCH §3.6: "refresh on every ledger write and at rollover"). Every ledger write
 * changes a quest or a balance, and a rollover changes the day, so watching the same Today model the screen uses covers
 * both; the widget re-renders only when what it shows actually changed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeToday: ObserveTodayUseCase,
    private val dayRefresher: DayRefresher,
    private val gateRunDao: com.assismohanty.awaken.core.data.db.dao.GateRunDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        // The Focus widget changes when a session starts, ends or is cleared; the latest run row covers all three.
        scope.launch {
            gateRunDao.observeLatest().map { it?.id to it?.status }.distinctUntilChanged().collect {
                try {
                    FocusWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Updating the focus widget failed")
                }
            }
        }
        scope.launch {
            observeToday(dayRefresher.ticks)
                .map { it.toWidgetSnapshot() }
                .distinctUntilChanged()
                .collect {
                    try {
                        TodayWidget().updateAll(context)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Updating the widget failed")
                    }
                }
        }
    }
}
