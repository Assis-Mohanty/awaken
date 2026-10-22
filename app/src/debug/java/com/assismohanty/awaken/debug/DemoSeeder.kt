package com.assismohanty.awaken.debug

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.data.db.dao.AchievementDao
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.PathDao
import com.assismohanty.awaken.core.data.db.dao.TrialDao
import com.assismohanty.awaken.core.data.db.entity.AchievementUnlockEntity
import com.assismohanty.awaken.core.data.db.entity.CompletionEntity
import com.assismohanty.awaken.core.data.db.entity.DayRecordEntity
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.PathEntity
import com.assismohanty.awaken.core.data.db.entity.PathMilestoneEntity
import com.assismohanty.awaken.core.data.db.entity.PathSlotEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekEntity
import com.assismohanty.awaken.core.data.db.entity.PathWeekStatus
import com.assismohanty.awaken.core.data.db.entity.TrialEntity
import com.assismohanty.awaken.core.data.di.IoDispatcher
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.domain.CreateHunterUseCase
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.GrantAiConsentUseCase
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.LevelCurve
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.model.VerificationSource
import com.assismohanty.awaken.engine.path.Feedback
import com.assismohanty.awaken.engine.path.PathMode
import com.assismohanty.awaken.engine.path.PathScheduler
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.PathUnit
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Debug-only "Screenshot mode": wipes the database and builds a realistic demo hunter for Play Store screenshots
 * (LV 24, C-rank, 12-day streak, varied stats, achievements, two active Paths with a charted week, one milestone cleared).
 *
 * Everything an economy balance depends on is written as ledger rows (invariant 1); the rest are plain history rows. The
 * demo data is never produced by release code.
 */
class DemoSeeder @Inject constructor(
    private val db: AwakenDatabase,
    private val settings: DebugSettings,
    private val clock: Clock,
    private val createHunter: CreateHunterUseCase,
    private val grantAiConsent: GrantAiConsentUseCase,
    private val hunterDao: HunterDao,
    private val dayRecordDao: DayRecordDao,
    private val completionDao: CompletionDao,
    private val achievementDao: AchievementDao,
    private val trialDao: TrialDao,
    private val pathDao: PathDao,
    private val ledger: LedgerRepository,
    private val dayRefresher: DayRefresher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): String {
        withContext(ioDispatcher) { db.clearAllTables() }
        settings.reset()
        grantAiConsent()

        val profile = createHunter(name = DEMO_NAME, focusStats = listOf(Stat.INT, Stat.STR))
        val today = clock.gameDate(profile.dayStartHour)
        val now = clock.now()
        hunterDao.upsertProfile(profile.copy(firstGameDate = today.minusDays(HISTORY_DAYS), equippedTitleId = "rising_hunter"))

        seedHistory(today, now)
        seedRankAndAchievements(today, now)
        seedPaths(today, now)
        ledger.rebuildBalancesFromLedger()
        dayRefresher.refresh()
        return "Demo hunter ready: LV $DEMO_LEVEL, C-rank, $STREAK_DAYS-day streak, ${DEMO_PATHS.size} Paths."
    }

    /** A baseline of older XP, then [STREAK_DAYS] cleared days with 1–4 completions each (heatmap) and steady XP. */
    private suspend fun seedHistory(today: LocalDate, now: Instant) {
        val baselineDate = today.minusDays(HISTORY_DAYS)
        val rows = mutableListOf<LedgerEntryEntity>()
        fun row(kind: LedgerKind, delta: Long, id: String, date: LocalDate, stat: Stat? = null, xpSource: String = "") =
            LedgerEntryEntity(
                kind = kind, stat = stat?.name.orEmpty(), delta = delta, sourceType = SourceType.QUEST,
                sourceId = "demo:$id", gameDate = date, xpSource = xpSource, createdAt = now,
            )

        val perDayXp = DAY_XP.toLong()
        val totalTarget = LevelCurve.cumulative(DEMO_LEVEL) + LevelCurve.xpToNext(DEMO_LEVEL) * PROGRESS_PERCENT / 100
        rows += row(LedgerKind.XP_TOTAL, totalTarget - perDayXp * (STREAK_DAYS + 1), "baseline", baselineDate)
        STAT_XP.forEach { (stat, xp) -> rows += row(LedgerKind.XP_STAT, xp - perDayXp * (STREAK_DAYS + 1) / STAT_XP.size, "baseline-${stat.name}", baselineDate, stat) }

        val completions = mutableListOf<CompletionEntity>()
        for (offset in 1..STREAK_DAYS + 1) {
            val date = today.minusDays(offset.toLong())
            val count = HEATMAP_PATTERN[offset % HEATMAP_PATTERN.size]
            val stat = Stat.entries[offset % Stat.entries.size]
            rows += row(LedgerKind.XP_TOTAL, perDayXp, "day-$offset", date, xpSource = "SELF")
            rows += row(LedgerKind.XP_STAT, perDayXp, "day-$offset-${stat.name}", date, stat)
            rows += row(LedgerKind.GOLD, GOLD_PER_DAY, "day-$offset-gold", date)
            repeat(count) { n ->
                completions += CompletionEntity(
                    id = "demo-$offset-$n#1", questInstanceId = "demo-$offset-$n", attempt = 1, gameDate = date,
                    questType = if (n == 0) QuestType.MAIN else QuestType.SIDE, stat = stat,
                    verificationSource = VerificationSource.SELF, crit = offset == CRIT_DAY && n == 0,
                    xp = (perDayXp / count).toInt(), completedAt = now,
                )
            }
            // Offset STREAK_DAYS + 1 is a BROKEN day, so the streak counts exactly STREAK_DAYS cleared days since it.
            dayRecordDao.upsert(
                DayRecordEntity(
                    gameDate = date,
                    cleared = offset <= STREAK_DAYS,
                    outcome = if (offset <= STREAK_DAYS) DayOutcome.CLEARED else DayOutcome.BROKEN,
                    finalizedAt = now,
                ),
            )
        }
        rows += row(LedgerKind.GOLD, BASE_GOLD, "gold", baselineDate)
        rows += row(LedgerKind.FRAGMENT, FRAGMENTS, "fragments", baselineDate)
        rows += LedgerEntryEntity(
            kind = LedgerKind.ITEM, itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID, delta = SHIELDS,
            sourceType = SourceType.SHOP, sourceId = "demo:shields", gameDate = baselineDate, createdAt = now,
        )
        ledger.insertIdempotent(rows)
        completions.forEach { completionDao.insert(it) }
    }

    private suspend fun seedRankAndAchievements(today: LocalDate, now: Instant) {
        listOf(Rank.D, Rank.C).forEach { rank ->
            trialDao.upsert(
                TrialEntity(
                    id = "demo-trial-${rank.name}", targetRank = rank, startedGameDate = today.minusDays(rank.minLevel.toLong()),
                    expiresGameDate = RankRules.trialExpiresOn(today.minusDays(rank.minLevel.toLong())),
                    status = RankRules.TrialStatus.PASSED, endedAt = now,
                ),
            )
        }
        ACHIEVEMENTS.forEach { achievementDao.insert(AchievementUnlockEntity(it, now)) }
    }

    /** Two ACTIVE Paths with a READY week. Slots earlier in the week are done; today's and later ones are PENDING. */
    private suspend fun seedPaths(today: LocalDate, now: Instant) {
        val isoWeek = PathScheduler.isoWeek(today)
        val monday = today.with(DayOfWeek.MONDAY)
        DEMO_PATHS.forEachIndexed { index, demo ->
            val pathId = "demo-path-$index"
            pathDao.upsert(
                PathEntity(
                    id = pathId, goal = demo.goal, currentLevel = demo.currentLevel, notes = "",
                    primaryStat = demo.stat, secondaryStat = null,
                    activeDaysMask = demo.days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
                    questsPerDay = 1, weeklyMinutes = demo.weeklyMinutes, status = PathStatus.ACTIVE,
                    currentMilestoneIndex = demo.milestoneIndex,
                    milestoneStartedGameDate = today.minusDays(demo.milestoneIndex * MILESTONE_DAYS + 1L),
                    createdAt = now, createdGameDate = today.minusDays(PATH_AGE_DAYS),
                ),
            )
            pathDao.insertMilestones(
                demo.milestones.mapIndexed { idx, (title, summary, exit) ->
                    PathMilestoneEntity(pathId, idx, title, summary, exit, planVersion = 1)
                },
            )
            pathDao.upsertWeek(
                PathWeekEntity(
                    pathId = pathId, isoWeek = isoWeek, status = PathWeekStatus.READY, progressionSignal = ProgressionSignal.UP,
                    promptVersion = "demo", responseSha256 = "demo", requestedAt = now, receivedAt = now,
                ),
            )
            val dates = demo.days.map { monday.plusDays((it.value - 1).toLong()) }.sorted()
            pathDao.insertSlots(
                demo.slots.mapIndexed { slotIndex, slot ->
                    val date = dates.getOrNull(slotIndex)
                    val done = date != null && date < today
                    PathSlotEntity(
                        id = PathScheduler.slotId(pathId, isoWeek, slotIndex), pathId = pathId, isoWeek = isoWeek,
                        blueprintIndex = slotIndex, assignedGameDate = date, carriedOver = false,
                        title = slot.title, description = slot.description, stat = demo.stat, unit = slot.unit,
                        target = slot.target, estMinutes = slot.minutes, mode = slot.mode,
                        pricedDifficulty = slot.difficulty, pricedVerification = if (slot.mode == PathMode.GATE) Verification.TIMER else Verification.SELF,
                        gateRank = null, minDurationSec = slot.minutes * SECONDS_PER_MINUTE, tagsJson = "[]",
                        rationale = slot.rationale, outcome = if (done) SlotOutcome.COMPLETED else SlotOutcome.PENDING,
                        feedback = if (done) Feedback.RIGHT else null,
                    )
                },
            )
        }
    }

    private data class DemoSlot(
        val title: String,
        val description: String,
        val unit: PathUnit,
        val target: Int,
        val minutes: Int,
        val difficulty: Difficulty,
        val rationale: String,
        val mode: PathMode = PathMode.SELF,
    )

    private data class DemoPath(
        val goal: String,
        val currentLevel: String,
        val stat: Stat,
        val days: List<DayOfWeek>,
        val weeklyMinutes: Int,
        val milestoneIndex: Int,
        val milestones: List<Triple<String, String, String>>,
        val slots: List<DemoSlot>,
    )

    private companion object {
        const val DEMO_NAME = "Ryu"
        const val DEMO_LEVEL = 24
        const val PROGRESS_PERCENT = 40
        const val STREAK_DAYS = 12
        const val HISTORY_DAYS = 40L
        const val DAY_XP = 340
        const val CRIT_DAY = 3
        const val BASE_GOLD = 1_850L
        const val GOLD_PER_DAY = 60L
        const val FRAGMENTS = 3L
        const val SHIELDS = 2L
        const val MILESTONE_DAYS = 9L
        const val PATH_AGE_DAYS = 24L
        const val SECONDS_PER_MINUTE = 60

        /** Completions per cleared day (cycled by day offset); the heatmap caps intensity at 4. */
        val HEATMAP_PATTERN = intArrayOf(3, 2, 4, 1, 3, 4, 2)

        /** Varied stat XP: statLevel = floor(sqrt(xp / 40)) + 1 — INT 14, STR 13, WIL 9, VIT 11, CHA 6. */
        val STAT_XP = mapOf(Stat.INT to 7_400L, Stat.STR to 6_200L, Stat.WIL to 2_800L, Stat.VIT to 4_100L, Stat.CHA to 1_100L)

        val ACHIEVEMENTS = listOf(
            "awakened", "first_blood", "lvl5", "lvl10", "streak3", "streak7", "crit1", "rank_d", "rank_c",
        )

        val DEMO_PATHS = listOf(
            DemoPath(
                goal = "Reach 1900 on Codeforces",
                currentLevel = "Rated 1650, weak at graphs and DP",
                stat = Stat.INT,
                days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                weeklyMinutes = 240,
                milestoneIndex = 1,
                milestones = listOf(
                    Triple("Solid 1700 fundamentals", "Clean up implementation speed on Div 2 C.", "Solve 15 rated 1500-1700 problems."),
                    Triple("Graph traversal fluency", "BFS, DFS, shortest paths without notes.", "Solve 10 graph problems rated 1700-1900."),
                    Triple("Dynamic programming core", "Knapsack, intervals, bitmask basics.", "Solve 10 DP problems rated 1700-1900."),
                    Triple("Contest stamina", "Two full contests per week under time.", "Finish 4 contests with 3+ problems."),
                    Triple("Break 1900", "Convert practice into rating.", "Reach 1900 in a rated contest."),
                ),
                slots = listOf(
                    DemoSlot("Solve 2 graph problems", "Two Codeforces problems rated 1700-1800 on BFS or DFS.", PathUnit.PROBLEMS, 2, 60, Difficulty.NORMAL, "Graphs are your weakest topic."),
                    DemoSlot("Review one editorial", "Read the editorial of a problem you failed and rewrite the solution.", PathUnit.ITEMS, 1, 30, Difficulty.NORMAL, "Learning from misses lifts rating fastest."),
                    DemoSlot("Timed practice round", "One 45-minute virtual contest, no hints.", PathUnit.MINUTES, 45, 45, Difficulty.NORMAL, "Builds contest stamina.", PathMode.GATE),
                    DemoSlot("Solve 1 DP problem", "A DP problem rated 1700; write the recurrence first.", PathUnit.PROBLEMS, 1, 45, Difficulty.NORMAL, "Starts the DP milestone early."),
                    DemoSlot("Upsolve contest problem D", "Finish the problem you did not solve live.", PathUnit.PROBLEMS, 1, 40, Difficulty.NORMAL, "Upsolving converts practice into rating."),
                ),
            ),
            DemoPath(
                goal = "Run a half marathon in under 2:00",
                currentLevel = "Can run 5 km in 32 minutes",
                stat = Stat.VIT,
                days = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                weeklyMinutes = 200,
                milestoneIndex = 0,
                milestones = listOf(
                    Triple("Run 10 km comfortably", "Build the aerobic base with easy runs.", "Run 10 km without stopping."),
                    Triple("Tempo and long runs", "Add one tempo session and a growing long run.", "Run 16 km at easy pace."),
                    Triple("Race-pace confidence", "Practice 5:40 per km for sustained blocks.", "Hold 5:40 per km for 10 km."),
                    Triple("Taper and race", "Cut volume and arrive fresh.", "Finish the half marathon under 2:00."),
                ),
                slots = listOf(
                    DemoSlot("Easy 5 km run", "Conversational pace, no watch pressure.", PathUnit.KM, 5, 35, Difficulty.EASY, "Base building starts easy."),
                    DemoSlot("Long run, 8 km", "Slow and steady; walk if you must.", PathUnit.KM, 8, 55, Difficulty.NORMAL, "Long runs build the engine."),
                    DemoSlot("Mobility and strength", "Hips, calves and core circuit.", PathUnit.MINUTES, 20, 20, Difficulty.EASY, "Keeps injuries away."),
                    DemoSlot("Tempo intervals", "Four times 5 minutes comfortably hard.", PathUnit.SESSIONS, 1, 40, Difficulty.NORMAL, "Raises your threshold."),
                ),
            ),
        )
    }
}
