package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.DayOutcome.BROKEN
import com.assismohanty.awaken.engine.DayOutcome.CLEARED
import com.assismohanty.awaken.engine.DayOutcome.RECOVERY
import com.assismohanty.awaken.engine.DayOutcome.REST
import com.assismohanty.awaken.engine.DayOutcome.SHIELDED
import com.assismohanty.awaken.engine.model.Stat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** GAME_DESIGN §14.10 — the v1.1 growth-layer vectors, one named test per row. */
class GrowthVectorsTest {

    private val today = LocalDate.of(2026, 10, 29)
    private val longAgo = LocalDate.of(2026, 1, 1)

    /** Outcomes for the days ending yesterday, oldest first. */
    private fun window(vararg outcomes: DayOutcome): Map<LocalDate, DayOutcome> =
        outcomes.reversed().mapIndexed { i, outcome -> today.minusDays(i + 1L) to outcome }.toMap()

    private fun repeat(outcome: DayOutcome, times: Int) = Array(times) { outcome }

    @Test
    fun `vector 50 - 22 cleared and 6 broken = 22 of 28`() {
        val score = Consistency.score(window(*repeat(CLEARED, 22), *repeat(BROKEN, 6)), today, longAgo)
        assertThat(score).isEqualTo(ConsistencyScore(cleared = 22, eligible = 28))
        assertThat(score.ratio).isWithin(0.001).of(0.786)
    }

    @Test
    fun `vector 51 - rest and recovery days are excluded`() {
        val score = Consistency.score(
            window(*repeat(CLEARED, 20), *repeat(REST, 2), RECOVERY, *repeat(BROKEN, 5)),
            today,
            longAgo,
        )
        assertThat(score).isEqualTo(ConsistencyScore(cleared = 20, eligible = 25))
        assertThat(score.ratio).isWithin(1e-9).of(0.80)
    }

    @Test
    fun `vector 52 - a cleared rest day is eligible and cleared`() {
        // A rest day that was cleared is finalized as CLEARED (ENGINE_DECISIONS #12).
        assertThat(Consistency.over(listOf(CLEARED))).isEqualTo(ConsistencyScore(cleared = 1, eligible = 1))
    }

    @Test
    fun `vector 53 - a hunter who started 6 days ago has a 6-day window`() {
        val outcomes = window(*repeat(CLEARED, 5), BROKEN) + (today.minusDays(30) to BROKEN)
        val score = Consistency.score(outcomes, today, firstGameDate = today.minusDays(6))
        assertThat(score).isEqualTo(ConsistencyScore(cleared = 5, eligible = 6))
    }

    @Test
    fun `days older than 28 and today are outside the window`() {
        val outcomes = window(*repeat(CLEARED, 30)) + (today to BROKEN)
        assertThat(Consistency.score(outcomes, today, longAgo)).isEqualTo(ConsistencyScore(cleared = 28, eligible = 28))
    }

    @Test
    fun `vector 54 - only rest and recovery days means no score`() {
        val score = Consistency.score(window(REST, RECOVERY, REST), today, longAgo)
        assertThat(score.eligible).isEqualTo(0)
        assertThat(score.ratio).isNull()
    }

    @Test
    fun `vector 55 - missed yesterday shows the line, rest does not`() {
        assertThat(Consistency.missLine(yesterday = BROKEN, dayBefore = CLEARED)).isEqualTo(MissLine.MISSED_YESTERDAY)
        assertThat(Consistency.missLine(yesterday = SHIELDED, dayBefore = CLEARED)).isEqualTo(MissLine.MISSED_YESTERDAY)
        assertThat(Consistency.missLine(yesterday = REST, dayBefore = BROKEN)).isNull()
        assertThat(Consistency.missLine(yesterday = null, dayBefore = null)).isNull()
    }

    @Test
    fun `vector 56 - two missed days in a row reads a fresh start`() {
        assertThat(Consistency.missLine(yesterday = BROKEN, dayBefore = BROKEN)).isEqualTo(MissLine.FRESH_START)
    }

    @Test
    fun `vector 57 - recoveries are cleared days right after a missed day`() {
        val outcomes = listOf(CLEARED, BROKEN, CLEARED, SHIELDED, CLEARED, BROKEN, BROKEN, CLEARED)
        assertThat(Consistency.recoveries(outcomes)).isEqualTo(3)
    }

    @Test
    fun `vector 58 - the 5th recovery unlocks never_twice once for 50 gold`() {
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(recoveries = 4))).doesNotContain("never_twice")
        assertThat(AchievementEvaluator.evaluate(AchievementSnapshot(recoveries = 5))).contains("never_twice")
        assertThat(AchievementEvaluator.byId.getValue("never_twice").gold).isEqualTo(50)
        val sixth = AchievementSnapshot(recoveries = 6, alreadyUnlocked = setOf("never_twice"))
        assertThat(AchievementEvaluator.evaluate(sixth)).doesNotContain("never_twice")
    }

    private val identities = IdentityCatalog(
        version = 1,
        identities = listOf(
            Identity("trains", Stat.STR),
            Identity("reads", Stat.INT),
            Identity("ships", Stat.INT),
        ),
    )

    @Test
    fun `vector 75 - picks reads, trains, ships pre-select INT and STR`() {
        assertThat(identities.preselect(listOf("reads", "trains", "ships"))).containsExactly(Stat.INT, Stat.STR).inOrder()
    }

    @Test
    fun `vector 76 - picks reads, ships pre-select INT only`() {
        assertThat(identities.preselect(listOf("reads", "ships"))).containsExactly(Stat.INT)
    }

    @Test
    fun `vector 71 - the review is open Saturday to Monday`() {
        val friday = LocalDate.of(2026, 10, 30)
        val week = GameWeek.of(friday)
        assertThat(WeeklyReviewRules.reviewedWeek(friday)).isNull()
        assertThat(WeeklyReviewRules.reviewedWeek(friday.plusDays(1))).isEqualTo(week)
        assertThat(WeeklyReviewRules.reviewedWeek(friday.plusDays(2))).isEqualTo(week)
        assertThat(WeeklyReviewRules.reviewedWeek(friday.plusDays(3))).isEqualTo(week)
        assertThat(WeeklyReviewRules.reviewedWeek(friday.plusDays(4))).isNull()
        val key = WeeklyReviewRules.isoKey(week)
        assertThat(key).isEqualTo("2026-W44")
        assertThat(WeeklyReviewRules.isOpen(friday.plusDays(1), submittedWeeks = setOf(key))).isFalse()
    }

    @Test
    fun `vector 73 - the 4th review unlocks review4 for 50 gold`() {
        val after4 = AchievementEvaluator.evaluate(AchievementSnapshot(reviewsSubmitted = 4, alreadyUnlocked = setOf("review1")))
        assertThat(after4).containsExactly("review4")
        assertThat(AchievementEvaluator.byId.getValue("review4").gold).isEqualTo(50)
        assertThat(AchievementEvaluator.byId.getValue("review12").titleId).isEqualTo("the_reflective")
    }

    @Test
    fun `review text is cleaned, capped by code points and blank becomes null`() {
        assertThat(WeeklyReviewRules.cleanText("  ran\n\tthree   times  ",WeeklyReviewRules.TEXT_MAX)).isEqualTo("ran three times")
        assertThat(WeeklyReviewRules.cleanText("   ", WeeklyReviewRules.TEXT_MAX)).isNull()
        assertThat(WeeklyReviewRules.cleanText("😀".repeat(200), WeeklyReviewRules.INTENTION_MAX)?.codePointCount(0, 240)).isEqualTo(120)
        assertThat(WeeklyReviewRules.identityFor("h", "2026-W44", emptyList())).isNull()
        assertThat(WeeklyReviewRules.identityFor("h", "2026-W44", listOf("reads"))).isEqualTo("reads")
    }
}
