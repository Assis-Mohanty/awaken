package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuitDao
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.QuitCounterEntity
import com.assismohanty.awaken.core.data.db.entity.QuitDayEntity
import com.assismohanty.awaken.core.data.db.entity.RelapseEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.QuitDayVerdict
import com.assismohanty.awaken.engine.QuitKind
import com.assismohanty.awaken.engine.QuitRules
import com.assismohanty.awaken.engine.ScreenTimeRules
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One restart in a counter's log. [minutes] is set for the automatic restart of a Doomscrolling day. */
data class RestartEntry(val at: Instant, val trigger: String?, val note: String?, val auto: Boolean, val minutes: Int?)

/** A counter with everything its card and detail screen show, derived at [now] (GD §16.13). */
data class QuitCounterState(
    val counter: QuitCounterEntity,
    val current: Duration,
    val best: Duration,
    val reachedMilestone: Int?,
    val nextMilestone: Int?,
    val milestoneProgress: Float,
    val moneySavedMinor: Long?,
    val minutesReclaimed: Long?,
    /** Newest first. */
    val restarts: List<RestartEntry>,
    /** Doomscrolling only: the latest finalized day could not be read, so the count is waiting rather than moving. */
    val paused: Boolean,
) {
    val currentDays: Int get() = current.toDays().toInt()
    val bestDays: Int get() = best.toDays().toInt()
    val archived: Boolean get() = counter.archivedAt != null
}

private fun Int.days(): Duration = Duration.ofDays(toLong())

/** Builds a counter's state from its rows. Pure over the rows and [now]. */
internal fun quitState(
    counter: QuitCounterEntity,
    relapses: List<RelapseEntity>,
    days: List<QuitDayEntity>,
    now: Instant,
): QuitCounterState {
    val mine = relapses.filter { it.counterId == counter.id }
    val restarts = mine.sortedByDescending { it.at }.map { r ->
        RestartEntry(r.at, r.trigger, r.note, r.auto, minutes = r.gameDate?.let { date -> days.firstOrNull { it.counterId == counter.id && it.gameDate == date }?.minutes })
    }
    val (current, best, paused) = if (counter.kind.isTimeBased) {
        val runs = QuitRules.runs(counter.startAt, mine.filter { !it.auto }.map { it.at }, now)
        Triple(runs.current, runs.best, false)
    } else {
        val verdicts = days.filter { it.counterId == counter.id }.associate { it.gameDate to it.verdict }
        val (cur, top) = QuitRules.doomscrollRuns(verdicts)
        Triple(cur.days(), top.days(), verdicts.maxByOrNull { it.key }?.value == QuitDayVerdict.PAUSED)
    }
    val currentDays = current.toDays().toInt()
    return QuitCounterState(
        counter = counter,
        current = current,
        best = best,
        reachedMilestone = QuitRules.reachedMilestone(currentDays),
        nextMilestone = QuitRules.nextMilestone(currentDays),
        milestoneProgress = QuitRules.milestoneProgress(currentDays),
        moneySavedMinor = counter.costPerDayMinor?.let { QuitRules.moneySavedMinor(it, current) },
        minutesReclaimed = counter.minutesPerDay?.let { QuitRules.minutesReclaimed(it, current) },
        restarts = restarts,
        paused = paused,
    )
}

/** Counters with their derived runs, live. Read-only. */
class ObserveQuitCountersUseCase @Inject constructor(private val quitDao: QuitDao, private val clock: Clock) {
    operator fun invoke(ticks: Flow<Any>): Flow<List<QuitCounterState>> =
        combine(quitDao.observeCounters(), quitDao.observeRelapses(), quitDao.observeDays(), ticks) { counters, relapses, days, _ ->
            val now = clock.now()
            counters.map { quitState(it, relapses, days, now) }
        }
}

/**
 * The longest in-app run over every counter ever made, archived ones included (GD §16.13). A time-based counter counts from
 * `max(start, created)` so a back-dated start earns nothing; Doomscrolling counts clean days.
 */
class QuitRunReader @Inject constructor(private val quitDao: QuitDao, private val clock: Clock) {
    suspend fun bestInAppRunDays(): Int {
        val counters = quitDao.counters()
        if (counters.isEmpty()) return 0
        val relapses = quitDao.relapses()
        val days = quitDao.days()
        val now = clock.now()
        return counters.maxOf { counter ->
            if (counter.kind.isTimeBased) {
                val restarts = relapses.filter { it.counterId == counter.id && !it.auto }.map { it.at }
                QuitRules.bestInAppRunDays(counter.startAt, counter.createdAt, restarts, now)
            } else {
                QuitRules.doomscrollRuns(days.filter { it.counterId == counter.id }.associate { it.gameDate to it.verdict }).second
            }
        }
    }
}

sealed interface AddQuitResult {
    data class Added(val id: String) : AddQuitResult
    data object LimitReached : AddQuitResult
    data object InvalidName : AddQuitResult
    data object FutureStart : AddQuitResult

    /** One Doomscrolling counter is enough: a second would count the same days. */
    data object AlreadyExists : AddQuitResult
}

/** Makes a counter. A start in the past is allowed ("I stopped on …"); one in the future is not. */
class AddQuitCounterUseCase @Inject constructor(
    private val quitDao: QuitDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        kind: QuitKind,
        name: String,
        startAt: Instant?,
        costPerDayMinor: Long? = null,
        currency: String? = null,
        minutesPerDay: Int? = null,
    ): AddQuitResult = transactions.run {
        val now = clock.now()
        val cleanName = name.trim().take(QuitRules.NAME_MAX)
        if (cleanName.isEmpty()) return@run AddQuitResult.InvalidName
        // Doomscrolling is day-based and counts itself from now; a start date means nothing for it.
        val start = if (kind.isTimeBased) startAt ?: now else now
        if (start.isAfter(now)) return@run AddQuitResult.FutureStart
        val active = quitDao.counters().filter { it.archivedAt == null }
        if (active.size >= QuitRules.MAX_COUNTERS) return@run AddQuitResult.LimitReached
        if (kind == QuitKind.DOOMSCROLLING && active.any { it.kind == QuitKind.DOOMSCROLLING }) return@run AddQuitResult.AlreadyExists
        val id = UUID.randomUUID().toString()
        quitDao.upsertCounter(
            QuitCounterEntity(
                id = id,
                kind = kind,
                name = cleanName,
                startAt = start,
                createdAt = now,
                costPerDayMinor = costPerDayMinor?.takeIf { it > 0 },
                currency = currency?.takeIf { costPerDayMinor != null },
                minutesPerDay = minutesPerDay?.takeIf { it > 0 },
                autoLimitMin = if (kind == QuitKind.DOOMSCROLLING) QuitRules.DOOMSCROLL_LIMIT_MINUTES else null,
            ),
        )
        AddQuitResult.Added(id)
    }
}

sealed interface RestartResult {
    data object Logged : RestartResult
    data object NotFound : RestartResult

    /** Doomscrolling restarts itself from Screen Time; it is not logged by hand. */
    data object NotManual : RestartResult
    data object InvalidTime : RestartResult
}

/** Starts the count again. The best run is untouched and the restart stays in the log for good (GD §16.13). */
class LogQuitRestartUseCase @Inject constructor(
    private val quitDao: QuitDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(counterId: String, at: Instant? = null, trigger: String? = null, note: String? = null): RestartResult =
        transactions.run {
            val counter = quitDao.counter(counterId)?.takeIf { it.archivedAt == null } ?: return@run RestartResult.NotFound
            if (!counter.kind.isTimeBased) return@run RestartResult.NotManual
            val now = clock.now()
            val time = at ?: now
            if (time.isAfter(now) || time.isBefore(counter.startAt)) return@run RestartResult.InvalidTime
            quitDao.insertRelapse(
                RelapseEntity(
                    id = UUID.randomUUID().toString(),
                    counterId = counterId,
                    at = time,
                    trigger = trigger?.takeIf { it.isNotBlank() },
                    note = note?.trim()?.take(QuitRules.NOTE_MAX)?.takeIf { it.isNotEmpty() },
                ),
            )
            RestartResult.Logged
        }
}

/** Hides a counter from the list and frees its slot. Everything it recorded stays. */
class ArchiveQuitCounterUseCase @Inject constructor(private val quitDao: QuitDao, private val clock: Clock) {
    suspend operator fun invoke(counterId: String) = quitDao.archive(counterId, clock.now())
}

/** Distracting-app minutes of a finalized day, counted like the Screen Time screen; null when usage could not be read. */
class DistractingMinutesReader @Inject constructor(
    private val reader: ScreenTimeReader,
    private val usage: UsageGateway,
) {
    suspend fun minutesOn(profile: HunterProfileEntity, date: LocalDate): Int? {
        val read = reader.usageOn(profile, date)
        if (read !is UsageRead.Tracked) return null
        val used = read.trackedMillis.orEmpty()
        val installed = usage.launcherApps().associateBy { it.packageName }
        val rules = reader.rulesInForce(date)
        return used.filterKeys { it in installed }.entries.sumOf { (pkg, millis) ->
            val category = rules[pkg]?.category ?: installed[pkg]?.let { ScreenTimeRules.defaultCategory(it.packageName, it.kind) }
            if (category == AppCategory.DISTRACTING) (millis / MILLIS_PER_MINUTE).toInt() else 0
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/**
 * At each day rollover (GD §16.13): gives every active Doomscrolling counter a verdict for the day that just ended, and
 * evaluates the Clean Run achievements, in one transaction. The first counted day is the day after the counter was made.
 * Writing a day twice is a no-op. Fail closed: unreadable usage pauses the day. Never writes XP.
 */
class RecordQuitProgressUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val quitDao: QuitDao,
    private val minutes: DistractingMinutesReader,
    private val achievements: AchievementUnlocker,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate) {
        val profile = hunterDao.getProfile() ?: return
        val auto = quitDao.counters().filter { it.archivedAt == null && it.kind == QuitKind.DOOMSCROLLING }
        val due = auto.filter { counter ->
            val firstDay = GameDay.of(counter.createdAt, clock.zone(), profile.dayStartHour).plusDays(1)
            !date.isBefore(firstDay)
        }
        // Read outside the transaction: it can take a moment and writes nothing.
        val read = if (due.isEmpty()) null else minutes.minutesOn(profile, date)
        transactions.run {
            due.forEach { counter ->
                val verdict = QuitRules.doomscrollVerdict(read, counter.autoLimitMin ?: QuitRules.DOOMSCROLL_LIMIT_MINUTES)
                val inserted = quitDao.insertDay(QuitDayEntity(counter.id, date, verdict, read))
                if (inserted != -1L && verdict == QuitDayVerdict.OVER) {
                    quitDao.insertRelapse(
                        RelapseEntity(id = UUID.randomUUID().toString(), counterId = counter.id, at = clock.now(), gameDate = date, auto = true),
                    )
                }
            }
            achievements.unlockNew(profile, date, clock.now())
        }
    }
}
