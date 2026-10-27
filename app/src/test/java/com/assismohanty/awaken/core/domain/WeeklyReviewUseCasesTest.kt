package com.assismohanty.awaken.core.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.db.entity.WeeklyReviewEntity
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.testing.TestGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The weekly review end to end (GD §14.4, vectors 72–73). The test clock starts on Sunday 2026-10-18 (ISO week 42). */
@RunWith(AndroidJUnit4::class)
class WeeklyReviewUseCasesTest {
    private val graph = TestGraph()

    @After
    fun tearDown() = graph.close()

    private val input = WeeklyReviewInput(
        wentWell = "  Three runs\n before work ",
        slipped = "",
        focus = listOf(Stat.VIT, Stat.WIL),
        intention = "Lights out by 23:00",
    )

    @Test
    fun savingStoresCleanTextSetsNextWeeksFocusAndAwardsFirstLook() = runTest {
        graph.seedHunter()
        val model = checkNotNull(graph.loadReview())
        assertThat(model.isoWeek).isEqualTo("2026-W42")
        assertThat(model.focus).containsExactly(Stat.STR, Stat.INT).inOrder()

        val result = graph.submitReview(input)

        assertThat((result as ReviewResult.Saved).unlocked).contains("review1")
        val profile = checkNotNull(graph.db.hunterDao().getProfile())
        assertThat(profile.focusStats).containsExactly(Stat.VIT, Stat.WIL).inOrder()
        assertThat(graph.db.weeklyReviewDao().submittedWeeks()).containsExactly("2026-W42")
        // Once saved, the review is no longer offered for the week.
        assertThat(graph.loadReview()).isNull()
    }

    @Test
    fun vector72SubmittingTwiceIsANoOp() = runTest {
        graph.seedHunter()
        graph.submitReview(input)

        val second = graph.submitReview(input.copy(focus = listOf(Stat.CHA, Stat.STR)))

        assertThat(second).isEqualTo(ReviewResult.AlreadySubmitted)
        assertThat(graph.db.weeklyReviewDao().count()).isEqualTo(1)
        assertThat(checkNotNull(graph.db.hunterDao().getProfile()).focusStats).containsExactly(Stat.VIT, Stat.WIL).inOrder()
        val reviewGold = graph.db.ledgerDao().all().filter { it.sourceType == SourceType.ACHIEVEMENT && it.sourceId == "review1" }
        assertThat(reviewGold).hasSize(1)
    }

    @Test
    fun vector73TheFourthReviewPaysFiftyGoldAndNoReviewEverWritesXp() = runTest {
        graph.seedHunter()
        listOf("2026-W39", "2026-W40", "2026-W41").forEach { week ->
            graph.db.weeklyReviewDao().insert(
                WeeklyReviewEntity(id = week, isoWeek = week, submittedAt = Instant.EPOCH, focusStat1 = Stat.STR, focusStat2 = Stat.INT),
            )
        }

        val result = graph.submitReview(input)

        assertThat((result as ReviewResult.Saved).unlocked).containsAtLeast("review1", "review4")
        val rows = graph.db.ledgerDao().all()
        assertThat(rows.filter { it.sourceType == SourceType.ACHIEVEMENT && it.sourceId == "review4" }.map { it.delta }).containsExactly(50L)
        assertThat(rows.filter { it.kind == LedgerKind.XP_TOTAL || it.kind == LedgerKind.XP_STAT }).isEmpty()
    }

    @Test
    fun theReviewIsClosedMidWeek() = runTest {
        graph.seedHunter()
        graph.clock.advance(Duration.ofDays(3)) // Wednesday

        assertThat(graph.loadReview()).isNull()
        assertThat(graph.submitReview(input)).isEqualTo(ReviewResult.Closed)
    }

    @Test
    fun theSundayDirectiveNotificationMentionsAnOpenReview() = runTest {
        graph.seedHunter()
        val profile = checkNotNull(graph.db.hunterDao().getProfile())
        graph.db.hunterDao().upsertProfile(profile.copy(reminderTime = "07:00"))

        val open = graph.buildReminder.directive() as ReminderContent.Directive
        graph.submitReview(input)
        val done = graph.buildReminder.directive() as ReminderContent.Directive

        assertThat(open.reviewOpen).isTrue()
        assertThat(done.reviewOpen).isFalse()
    }
}
