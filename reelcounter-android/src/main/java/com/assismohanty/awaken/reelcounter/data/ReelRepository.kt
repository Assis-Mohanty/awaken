package com.assismohanty.awaken.reelcounter.data

import androidx.room.withTransaction
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.count.CounterEffect
import com.assismohanty.awaken.reelcounter.core.count.RestoredSession
import com.assismohanty.awaken.reelcounter.core.export.ExportDailyTotal
import com.assismohanty.awaken.reelcounter.core.export.ExportDocument
import com.assismohanty.awaken.reelcounter.core.export.ExportEvent
import com.assismohanty.awaken.reelcounter.core.stats.DayTotal
import com.assismohanty.awaken.reelcounter.data.db.DailyTotalEntity
import com.assismohanty.awaken.reelcounter.data.db.ReelDatabase
import com.assismohanty.awaken.reelcounter.data.db.ReelEventEntity
import com.assismohanty.awaken.reelcounter.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreData(
    val counts: Map<ShortVideoApp, Int>,
    val snoozes: Map<ShortVideoApp, Int>,
    val sessions: Map<ShortVideoApp, RestoredSession>,
)

/** One transaction per effect batch; `daily_total` is only touched when the event row was really new. */
@Singleton
class ReelRepository @Inject constructor(
    private val db: ReelDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao = db.dao()

    /** Returns false when the event had already been recorded. */
    suspend fun recordReel(c: CounterEffect.Counted): Boolean = withContext(io) {
        val pkg = c.app.primaryPackage
        val day = c.day.toString()
        db.withTransaction {
            val id = dao.insertEvent(
                ReelEventEntity(
                    pkg = pkg,
                    ts = c.at.toEpochMilli(),
                    itemIndex = c.index,
                    day = day,
                    sessionId = c.sessionId,
                ),
            )
            if (id == -1L) {
                false
            } else {
                dao.ensureTotal(day, pkg)
                dao.incrementCount(day, pkg)
                true
            }
        }
    }

    suspend fun addSeconds(day: LocalDate, app: ShortVideoApp, seconds: Long) = withContext(io) {
        db.withTransaction {
            dao.ensureTotal(day.toString(), app.primaryPackage)
            dao.addSeconds(day.toString(), app.primaryPackage, seconds)
        }
    }

    suspend fun setSnoozes(day: LocalDate, app: ShortVideoApp, snoozes: Int) = withContext(io) {
        db.withTransaction {
            dao.ensureTotal(day.toString(), app.primaryPackage)
            dao.raiseSnoozes(day.toString(), app.primaryPackage, snoozes)
        }
    }

    suspend fun loadRestore(day: LocalDate): RestoreData = withContext(io) {
        val rows = dao.day(day.toString()).mapNotNull { r -> ShortVideoApp.fromPackage(r.pkg)?.let { it to r } }
        val sessions = dao.lastEventPerPkg().mapNotNull { e ->
            ShortVideoApp.fromPackage(e.pkg)?.let {
                it to RestoredSession(e.sessionId, e.itemIndex, Instant.ofEpochMilli(e.ts))
            }
        }
        RestoreData(
            counts = rows.associate { (app, r) -> app to r.count },
            snoozes = rows.associate { (app, r) -> app to r.snoozes },
            sessions = sessions.toMap(),
        )
    }

    suspend fun day(day: LocalDate): List<DayTotal> = withContext(io) {
        dao.day(day.toString()).mapNotNull { it.toDayTotal() }
    }

    fun observeDay(day: LocalDate): Flow<List<DayTotal>> =
        dao.observeDay(day.toString()).map { rows -> rows.mapNotNull { it.toDayTotal() } }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayTotal>> =
        dao.observeRange(from.toString(), to.toString()).map { rows -> rows.mapNotNull { it.toDayTotal() } }

    fun observeFirstDay(): Flow<LocalDate?> = dao.observeFirstDay().map { it?.let(LocalDate::parse) }

    suspend fun exportDocument(
        now: Instant,
        zone: ZoneId,
        dayStart: LocalTime,
        limits: Map<ShortVideoApp, Int>,
    ): ExportDocument = withContext(io) {
        ExportDocument(
            exportedAt = now.toString(),
            zone = zone.id,
            dayStart = dayStart.toString(),
            limits = limits.mapKeys { it.key.name },
            dailyTotals = dao.allTotals().mapNotNull { r ->
                ShortVideoApp.fromPackage(r.pkg)?.let {
                    ExportDailyTotal(r.date, it.name, r.pkg, r.count, r.seconds, r.snoozes)
                }
            },
            events = dao.allEvents().mapNotNull { e ->
                ShortVideoApp.fromPackage(e.pkg)?.let {
                    ExportEvent(it.name, e.pkg, Instant.ofEpochMilli(e.ts).toString(), e.day, e.itemIndex, e.sessionId)
                }
            },
        )
    }

    private fun DailyTotalEntity.toDayTotal(): DayTotal? =
        ShortVideoApp.fromPackage(pkg)?.let { DayTotal(LocalDate.parse(date), it, count, seconds, snoozes) }
}
