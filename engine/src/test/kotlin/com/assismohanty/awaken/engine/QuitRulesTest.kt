package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §16.13 quit counters (vectors #122-#126). */
class QuitRulesTest {
    private fun at(text: String) = Instant.parse("${text}Z")

    @Test
    fun `vector 122 - current and best run, kept after a restart`() {
        val runs = QuitRules.runs(at("2026-11-01T08:00:00"), listOf(at("2026-11-05T20:00:00")), at("2026-11-08T09:00:00"))

        assertThat(runs.current).isEqualTo(Duration.ofDays(2).plusHours(13))
        assertThat(runs.currentDays).isEqualTo(2)
        assertThat(runs.best).isEqualTo(Duration.ofDays(4).plusHours(12))
        assertThat(runs.bestDays).isEqualTo(4)
    }

    @Test
    fun `the best run can be the one still running`() {
        val runs = QuitRules.runs(at("2026-11-01T00:00:00"), listOf(at("2026-11-02T00:00:00")), at("2026-11-08T00:00:00"))
        assertThat(runs.current).isEqualTo(Duration.ofDays(6))
        assertThat(runs.best).isEqualTo(Duration.ofDays(6))
    }

    @Test
    fun `restarts out of order, before the start or after now are handled`() {
        val runs = QuitRules.runs(
            start = at("2026-11-01T00:00:00"),
            restarts = listOf(at("2026-11-06T00:00:00"), at("2026-10-01T00:00:00"), at("2026-11-04T00:00:00"), at("2026-12-01T00:00:00")),
            now = at("2026-11-08T00:00:00"),
        )
        assertThat(runs.current).isEqualTo(Duration.ofDays(2))
        assertThat(runs.best).isEqualTo(Duration.ofDays(3))
    }

    @Test
    fun `a start in the future has no run yet`() {
        assertThat(QuitRules.runs(at("2026-11-09T00:00:00"), emptyList(), at("2026-11-08T00:00:00")).current).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `under a day shows hours and no whole days`() {
        val runs = QuitRules.runs(at("2026-11-08T02:00:00"), emptyList(), at("2026-11-08T09:30:00"))
        assertThat(runs.currentDays).isEqualTo(0)
        assertThat(QuitRules.hoursUnderOneDay(runs.current)).isEqualTo(7)
        assertThat(QuitRules.hoursUnderOneDay(Duration.ofDays(2))).isEqualTo(0)
    }

    @Test
    fun `vector 123 - a back-dated start shows its days but earns only the in-app run`() {
        val start = at("2026-10-01T00:00:00")
        val created = at("2026-11-01T12:00:00")
        val now = at("2026-11-08T12:00:00")

        assertThat(QuitRules.runs(start, emptyList(), now).currentDays).isEqualTo(38)
        assertThat(QuitRules.bestInAppRunDays(start, created, emptyList(), now)).isEqualTo(7)
    }

    @Test
    fun `the in-app run ignores restarts from before the counter was made`() {
        val days = QuitRules.bestInAppRunDays(
            start = at("2026-10-01T00:00:00"),
            created = at("2026-11-01T00:00:00"),
            restarts = listOf(at("2026-10-20T00:00:00")),
            now = at("2026-11-11T00:00:00"),
        )
        assertThat(days).isEqualTo(10)
    }

    @Test
    fun `vector 124 - money saved and time reclaimed use whole days`() {
        val run = Duration.ofDays(2).plusHours(13)
        assertThat(QuitRules.moneySavedMinor(150, run)).isEqualTo(300)
        assertThat(QuitRules.minutesReclaimed(20, run)).isEqualTo(40)
        assertThat(QuitRules.moneySavedMinor(150, Duration.ofHours(23))).isEqualTo(0)
    }

    @Test
    fun `milestones are 1 3 7 14 30 90 180 365`() {
        assertThat(QuitRules.MILESTONES).containsExactly(1, 3, 7, 14, 30, 90, 180, 365).inOrder()
        assertThat(QuitRules.reachedMilestone(0)).isNull()
        assertThat(QuitRules.nextMilestone(0)).isEqualTo(1)
        assertThat(QuitRules.reachedMilestone(13)).isEqualTo(7)
        assertThat(QuitRules.nextMilestone(13)).isEqualTo(14)
        assertThat(QuitRules.nextMilestone(365)).isNull()
        assertThat(QuitRules.milestoneProgress(10)).isWithin(1e-6f).of((10 - 7) / 7f)
        assertThat(QuitRules.milestoneProgress(400)).isEqualTo(1f)
    }

    @Test
    fun `vector 125 - doomscrolling days, with an unreadable day paused`() {
        val monday = LocalDate.of(2026, 11, 2)
        val verdicts = mapOf(
            monday to QuitRules.doomscrollVerdict(40),
            monday.plusDays(1) to QuitRules.doomscrollVerdict(null),
            monday.plusDays(2) to QuitRules.doomscrollVerdict(55),
        )
        assertThat(verdicts.values).containsExactly(QuitDayVerdict.CLEAN, QuitDayVerdict.PAUSED, QuitDayVerdict.CLEAN).inOrder()
        assertThat(QuitRules.doomscrollRuns(verdicts)).isEqualTo(2 to 2)

        val withThursday = verdicts + (monday.plusDays(3) to QuitRules.doomscrollVerdict(75))
        assertThat(withThursday.getValue(monday.plusDays(3))).isEqualTo(QuitDayVerdict.OVER)
        assertThat(QuitRules.doomscrollRuns(withThursday)).isEqualTo(0 to 2)
    }

    @Test
    fun `sixty minutes exactly is still clean and sixty one is over`() {
        assertThat(QuitRules.doomscrollVerdict(60)).isEqualTo(QuitDayVerdict.CLEAN)
        assertThat(QuitRules.doomscrollVerdict(61)).isEqualTo(QuitDayVerdict.OVER)
    }

    @Test
    fun `limits are 6 counters and 24 character names`() {
        assertThat(QuitRules.MAX_COUNTERS).isEqualTo(6)
        assertThat(QuitRules.NAME_MAX).isEqualTo(24)
        assertThat(QuitRules.NOTE_MAX).isEqualTo(140)
        assertThat(QuitKind.DOOMSCROLLING.isTimeBased).isFalse()
        assertThat(QuitKind.SMOKING.isTimeBased).isTrue()
    }

    @Test
    fun `vector 126 - clean run achievements pay gold once and never XP`() {
        val byId = AchievementEvaluator.byId
        assertThat(listOf("quit_7", "quit_30", "quit_90", "quit_365").map { byId.getValue(it).gold }).containsExactly(30, 100, 150, 200).inOrder()
        assertThat(byId.getValue("quit_365").titleId).isEqualTo("unshaken")
        val first = AchievementEvaluator.evaluate(AchievementSnapshot(quitRunDays = 30))
        assertThat(first).containsAtLeast("quit_7", "quit_30")
        assertThat(first).containsNoneOf("quit_90", "quit_365")
        // Two counters reaching 30 are one snapshot value; once unlocked it is never offered again.
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(quitRunDays = 30, alreadyUnlocked = first.toSet()))).doesNotContain("quit_30")
    }
}
