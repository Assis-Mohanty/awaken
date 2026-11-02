package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

/** GD §16.10-16.12 pickups, step goal and verified phone-down nights (vectors #113-#121). */
class ScreenHealthRulesTest {
    private val day = LocalDate.of(2026, 11, 2)
    private fun at(h: Int, m: Int, date: LocalDate = day) = date.atTime(h, m).toInstant(ZoneOffset.UTC)
    private fun ev(type: ScreenEventType, h: Int, m: Int, date: LocalDate = day) = ScreenEvent(type, at(h, m, date))

    @Test
    fun `vector 113 - pickups, first pickup, last use and longest stretch`() {
        val today = listOf(
            ev(ScreenEventType.KEYGUARD_HIDDEN, 7, 10),
            ev(ScreenEventType.KEYGUARD_HIDDEN, 8, 0),
            ev(ScreenEventType.SCREEN_NON_INTERACTIVE, 8, 5),
            ev(ScreenEventType.KEYGUARD_HIDDEN, 12, 30),
        )
        val yesterday = listOf(ev(ScreenEventType.FOREGROUND, 21, 0, day.minusDays(1)), ev(ScreenEventType.FOREGROUND, 23, 48, day.minusDays(1)))

        val summary = PickupRules.summarize(today, yesterday)

        assertThat(summary.kind).isEqualTo(PickupKind.PICKUPS)
        assertThat(summary.count).isEqualTo(3)
        assertThat(summary.firstPickup).isEqualTo(at(7, 10))
        assertThat(summary.lastUseYesterday).isEqualTo(at(23, 48, day.minusDays(1)))
        assertThat(summary.longestStretch).isEqualTo(Duration.ofHours(4).plusMinutes(25))
    }

    @Test
    fun `vector 114 - a phone with no lock screen counts screen-ons`() {
        val today = (1..5).map { ev(ScreenEventType.SCREEN_INTERACTIVE, 8 + it, 0) }
        val summary = PickupRules.summarize(today, emptyList())
        assertThat(summary.kind).isEqualTo(PickupKind.SCREEN_ONS)
        assertThat(summary.count).isEqualTo(5)
    }

    @Test
    fun `the night before the first pickup and a stretch still running are not counted`() {
        val today = listOf(
            ev(ScreenEventType.SCREEN_NON_INTERACTIVE, 1, 0), // before the first pickup: the night
            ev(ScreenEventType.KEYGUARD_HIDDEN, 7, 0),
            ev(ScreenEventType.SCREEN_NON_INTERACTIVE, 7, 20),
            ev(ScreenEventType.KEYGUARD_HIDDEN, 7, 50),
            ev(ScreenEventType.SCREEN_NON_INTERACTIVE, 9, 0), // no next pickup yet
        )
        assertThat(PickupRules.summarize(today, emptyList()).longestStretch).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun `no events means a zero count and no times`() {
        val summary = PickupRules.summarize(emptyList(), emptyList())
        assertThat(summary.count).isEqualTo(0)
        assertThat(summary.firstPickup).isNull()
        assertThat(summary.longestStretch).isNull()
        assertThat(summary.lastUseYesterday).isNull()
    }

    @Test
    fun `vector 116 - the step ring fills to the goal and caps there`() {
        assertThat(StepGoalRules.progress(6_200, 8_000)).isWithin(1e-6f).of(0.775f)
        assertThat(StepGoalRules.reached(6_200, 8_000)).isFalse()
        assertThat(StepGoalRules.progress(9_100, 8_000)).isEqualTo(1f)
        assertThat(StepGoalRules.reached(9_100, 8_000)).isTrue()
    }

    @Test
    fun `the step goal is 1000 to 30000 in steps of 500`() {
        assertThat(StepGoalRules.DEFAULT_GOAL).isEqualTo(8_000)
        assertThat(StepGoalRules.isValidGoal(8_000)).isTrue()
        assertThat(StepGoalRules.isValidGoal(8_200)).isFalse()
        assertThat(StepGoalRules.isValidGoal(500)).isFalse()
        assertThat(StepGoalRules.isValidGoal(30_500)).isFalse()
        assertThat(StepGoalRules.clampGoal(8_200)).isEqualTo(8_000)
        assertThat(StepGoalRules.clampGoal(99_000)).isEqualTo(30_000)
    }

    private val instagram = "com.instagram.android"
    private val clock = "com.google.android.deskclock"
    private val allowed = setOf(clock, "com.assismohanty.awaken")

    @Test
    fun `vectors 117 to 119 - the grace decides kept or broken and allowed apps never count`() {
        fun verdict(usage: Map<String, Long>) = BedtimeRules.verdict(BedtimeRules.nonAllowedMillis(usage, allowed))
        assertThat(verdict(mapOf(instagram to 3 * 60_000L))).isEqualTo(BedtimeVerdict.KEPT)
        assertThat(verdict(mapOf(instagram to 5 * 60_000L))).isEqualTo(BedtimeVerdict.KEPT)
        assertThat(verdict(mapOf(instagram to 6 * 60_000L))).isEqualTo(BedtimeVerdict.BROKEN)
        assertThat(verdict(mapOf(clock to 12 * 60_000L))).isEqualTo(BedtimeVerdict.KEPT)
        assertThat(verdict(mapOf(instagram to 3 * 60_000L, "com.example.launcher" to 3 * 60_000L))).isEqualTo(BedtimeVerdict.BROKEN)
    }

    @Test
    fun `vector 120 - usage that could not be read is never kept`() {
        assertThat(BedtimeRules.verdict(null)).isEqualTo(BedtimeVerdict.NO_DATA)
    }

    @Test
    fun `vector 117 - the quest is Normal VIT device-usage and pays 78 XP at streak 0`() {
        val quest = BedtimeRules.bedtimeQuest("h1", day, "Phone down", "Keep it down")
        assertThat(quest.type).isEqualTo(QuestType.BEDTIME)
        assertThat(quest.stat).isEqualTo(Stat.VIT)
        assertThat(quest.difficulty).isEqualTo(Difficulty.NORMAL)
        assertThat(quest.verification).isEqualTo(Verification.DEVICE_USAGE)
        assertThat(quest.type.isGenerated).isFalse()
        assertThat(quest.type.canCrit).isTrue()
        assertThat(XpCalculator.questXp(quest.difficulty, 0, VerificationSource.USAGE, crit = false)).isEqualTo(78)
        assertThat(quest.questInstanceId).isEqualTo(BedtimeRules.bedtimeQuest("h1", day, "x", "y").questInstanceId)
    }

    @Test
    fun `the default window runs 23 00 to 06 30 on the wake day and crosses midnight`() {
        val setting = BedtimeSetting(day, true, BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE)
        val window = BedtimeRules.window(day, setting, ZoneOffset.UTC)
        assertThat(window.start).isEqualTo(at(23, 0, day.minusDays(1)))
        assertThat(window.end).isEqualTo(at(6, 30))
        assertThat(BedtimeRules.windDownOpensAt(window)).isEqualTo(at(22, 30, day.minusDays(1)))
    }

    @Test
    fun `a bedtime after midnight stays on the wake day`() {
        val setting = BedtimeSetting(day, true, 0, BedtimeRules.DEFAULT_WAKE)
        assertThat(BedtimeRules.window(day, setting, ZoneOffset.UTC).start).isEqualTo(at(0, 0))
    }

    @Test
    fun `window validity - steps, 5 to 12 hours, and a wake time after the day start`() {
        assertThat(BedtimeRules.isValidWindow(23 * 60, 6 * 60 + 30, 4)).isTrue()
        assertThat(BedtimeRules.isValidWindow(23 * 60 + 5, 6 * 60 + 30, 4)).isFalse() // not a 15-minute step
        assertThat(BedtimeRules.isValidWindow(19 * 60 + 45, 6 * 60, 4)).isFalse() // bedtime before 20:00
        assertThat(BedtimeRules.isValidWindow(2 * 60 + 15, 8 * 60, 4)).isFalse() // bedtime after 02:00
        assertThat(BedtimeRules.isValidWindow(2 * 60, 11 * 60 + 15, 4)).isFalse() // wake after 11:00
        assertThat(BedtimeRules.isValidWindow(2 * 60, 6 * 60, 4)).isFalse() // 4 hours
        assertThat(BedtimeRules.isValidWindow(20 * 60, 8 * 60 + 15, 4)).isFalse() // 12 h 15 min
        assertThat(BedtimeRules.isValidWindow(20 * 60, 8 * 60, 4)).isTrue() // exactly 12 hours
        assertThat(BedtimeRules.isValidWindow(23 * 60, 4 * 60, 4)).isFalse() // wake not later than the day start
        assertThat(BedtimeRules.isValidWindow(23 * 60, 4 * 60, 3)).isTrue()
    }

    @Test
    fun `vector 121 - an edit at 22 00 reaches the night after tonight`() {
        val old = BedtimeSetting(day.minusDays(10), true, 23 * 60, 6 * 60 + 30)
        val edit = BedtimeSetting(BedtimeRules.effectiveFromForEdit(day), true, 0, 6 * 60 + 30)
        val versions = listOf(old, edit)
        // Tonight's night ends tomorrow morning; the one after ends the day after.
        assertThat(BedtimeRules.settingFor(day.plusDays(1), versions)?.bedtimeMinute).isEqualTo(23 * 60)
        assertThat(BedtimeRules.settingFor(day.plusDays(2), versions)?.bedtimeMinute).isEqualTo(0)
    }

    @Test
    fun `no setting, or one switched off, means no night`() {
        assertThat(BedtimeRules.settingFor(day, emptyList())).isNull()
        val on = BedtimeSetting(day.minusDays(5), true, 23 * 60, 390)
        val off = BedtimeSetting(day.minusDays(1), false, 23 * 60, 390)
        assertThat(BedtimeRules.settingFor(day, listOf(on, off))).isNull()
        assertThat(BedtimeRules.settingFor(day.minusDays(3), listOf(on, off))).isEqualTo(on)
    }

    @Test
    fun `the breathing guide breathes in 4 s and out 6 s for 12 cycles`() {
        assertThat(BedtimeRules.BREATH_TOTAL_SECONDS).isEqualTo(120)
        assertThat(BedtimeRules.breathPhase(0.0)).isEqualTo(BreathPhase.IN)
        assertThat(BedtimeRules.breathPhase(3.9)).isEqualTo(BreathPhase.IN)
        assertThat(BedtimeRules.breathPhase(4.0)).isEqualTo(BreathPhase.OUT)
        assertThat(BedtimeRules.breathPhase(10.0)).isEqualTo(BreathPhase.IN)
        assertThat(BedtimeRules.breathPhase(119.9)).isEqualTo(BreathPhase.OUT)
        assertThat(BedtimeRules.breathPhase(120.0)).isEqualTo(BreathPhase.DONE)
    }
}
