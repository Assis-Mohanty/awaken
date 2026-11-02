package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One saved version of the phone-down night setting; it governs every wake day from [effectiveFrom] on (GD §16.12). */
data class BedtimeSetting(val effectiveFrom: LocalDate, val enabled: Boolean, val bedtimeMinute: Int, val wakeMinute: Int)

/** The span of a night in real time: from bedtime to wake. */
data class BedtimeWindow(val start: Instant, val end: Instant)

enum class BedtimeVerdict { KEPT, BROKEN, NO_DATA }

/** A phase of the optional wind-down breathing guide. */
enum class BreathPhase { IN, OUT, DONE }

/**
 * Verified phone-down bedtime (GD §16.12, approved 2026-11-02). Pure: the app supplies usage that the device observed;
 * nothing here pays for a night it did not see (fail closed).
 */
object BedtimeRules {
    /** N21: 23:00 to 06:30 until the hunter chooses otherwise. */
    const val DEFAULT_BEDTIME = 23 * 60
    const val DEFAULT_WAKE = 6 * 60 + 30

    /** N21: bedtime 20:00-02:00 and wake 04:00-11:00, in 15-minute steps. */
    const val STEP_MINUTES = 15
    const val BEDTIME_EARLIEST = 20 * 60
    const val BEDTIME_LATEST = 2 * 60
    val WAKE_RANGE = 4 * 60..11 * 60

    /** N21: the window is 5-12 hours long. */
    const val MIN_WINDOW_MINUTES = 5 * 60
    const val MAX_WINDOW_MINUTES = 12 * 60

    /** N22: up to 5 minutes of non-allowed foreground time still counts as kept (an alarm, a call, a glance). */
    const val GRACE_MILLIS = 5 * 60_000L

    /** The wind-down button opens this long before bedtime. */
    const val WIND_DOWN_LEAD_MINUTES = 30

    /** N23: inhale 4 s, exhale 6 s, 12 cycles = 2 minutes. */
    const val INHALE_SECONDS = 4
    const val EXHALE_SECONDS = 6
    const val BREATH_CYCLES = 12
    const val BREATH_TOTAL_SECONDS = (INHALE_SECONDS + EXHALE_SECONDS) * BREATH_CYCLES

    const val BEDTIME_SLOT = "bedtime"
    val BEDTIME_STAT = Stat.VIT
    val BEDTIME_DIFFICULTY = Difficulty.NORMAL
    const val BEDTIME_UNIT = "night"

    private const val MINUTES_PER_DAY = 24 * 60

    fun isValidBedtime(minute: Int): Boolean =
        minute % STEP_MINUTES == 0 && (minute in BEDTIME_EARLIEST until MINUTES_PER_DAY || minute in 0..BEDTIME_LATEST)

    fun isValidWake(minute: Int): Boolean = minute % STEP_MINUTES == 0 && minute in WAKE_RANGE

    fun windowMinutes(bedtimeMinute: Int, wakeMinute: Int): Int = Math.floorMod(wakeMinute - bedtimeMinute, MINUTES_PER_DAY)

    /** Both times valid, 5-12 hours apart, and the wake time later than the day start so the window ends inside one game-day. */
    fun isValidWindow(bedtimeMinute: Int, wakeMinute: Int, dayStartHour: Int): Boolean =
        isValidBedtime(bedtimeMinute) && isValidWake(wakeMinute) &&
            windowMinutes(bedtimeMinute, wakeMinute) in MIN_WINDOW_MINUTES..MAX_WINDOW_MINUTES &&
            wakeMinute > dayStartHour * 60

    /** Turning the feature on counts tonight's night (its wake day is tomorrow). */
    fun effectiveFromForEnable(today: LocalDate): LocalDate = today.plusDays(1)

    /** A change never reaches the night already ahead: it applies from the night after (vector #121). */
    fun effectiveFromForEdit(today: LocalDate): LocalDate = today.plusDays(2)

    /** The setting that governs [wakeDay]: the latest version in force, or null when none exists or it is switched off. */
    fun settingFor(wakeDay: LocalDate, versions: List<BedtimeSetting>): BedtimeSetting? =
        versions.filter { !it.effectiveFrom.isAfter(wakeDay) }.maxByOrNull { it.effectiveFrom }?.takeIf { it.enabled }

    /** The night that ends on [wakeDay]'s morning. */
    fun window(wakeDay: LocalDate, setting: BedtimeSetting, zone: ZoneId): BedtimeWindow {
        val end = ZonedDateTime.of(wakeDay, LocalTime.of(setting.wakeMinute / 60, setting.wakeMinute % 60), zone)
        return BedtimeWindow(end.minusMinutes(windowMinutes(setting.bedtimeMinute, setting.wakeMinute).toLong()).toInstant(), end.toInstant())
    }

    /** When the evening card offers the wind-down for [window]. */
    fun windDownOpensAt(window: BedtimeWindow): Instant = window.start.minusSeconds(WIND_DOWN_LEAD_MINUTES * 60L)

    /** Foreground time of every package that is not in [allowed]. */
    fun nonAllowedMillis(usageMillis: Map<String, Long>, allowed: Set<String>): Long =
        usageMillis.filterKeys { it !in allowed }.values.sum()

    /** KEPT within the grace, BROKEN past it, NO_DATA when usage could not be read (null): then nothing is paid. */
    fun verdict(nonAllowedMillis: Long?): BedtimeVerdict = when {
        nonAllowedMillis == null -> BedtimeVerdict.NO_DATA
        nonAllowedMillis <= GRACE_MILLIS -> BedtimeVerdict.KEPT
        else -> BedtimeVerdict.BROKEN
    }

    /** The BEDTIME quest for [wakeDay]; the id is deterministic per `(hunterId, wakeDay)` like every generated quest (GD §6.3). */
    fun bedtimeQuest(hunterId: String, wakeDay: LocalDate, title: String, description: String): QuestDraft = QuestDraft(
        questInstanceId = DirectiveGenerator.instanceId(hunterId, wakeDay, BEDTIME_SLOT),
        gameDate = wakeDay,
        type = QuestType.BEDTIME,
        templateId = null,
        stat = BEDTIME_STAT,
        secondaryStat = null,
        title = title,
        description = description,
        unit = BEDTIME_UNIT,
        target = 1,
        difficulty = BEDTIME_DIFFICULTY,
        verification = Verification.DEVICE_USAGE,
        minDurationSec = 0,
    )

    /** The breathing guide phase [elapsedSeconds] into the exercise. */
    fun breathPhase(elapsedSeconds: Double): BreathPhase {
        if (elapsedSeconds >= BREATH_TOTAL_SECONDS) return BreathPhase.DONE
        val inCycle = elapsedSeconds % (INHALE_SECONDS + EXHALE_SECONDS)
        return if (inCycle < INHALE_SECONDS) BreathPhase.IN else BreathPhase.OUT
    }
}
