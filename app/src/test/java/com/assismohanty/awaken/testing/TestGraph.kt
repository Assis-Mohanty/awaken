package com.assismohanty.awaken.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import com.assismohanty.awaken.core.data.db.entity.LedgerEntryEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.LedgerRepository
import com.assismohanty.awaken.core.data.repo.NarrativeRepository
import com.assismohanty.awaken.core.data.repo.NoticeStore
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.domain.AchievementUnlocker
import com.assismohanty.awaken.core.domain.AdvanceTrialUseCase
import com.assismohanty.awaken.core.domain.CompleteOnboardingUseCase
import com.assismohanty.awaken.core.domain.CompleteQuestUseCase
import com.assismohanty.awaken.core.domain.CompletionCenter
import com.assismohanty.awaken.core.domain.CreateHunterUseCase
import com.assismohanty.awaken.core.domain.DayRefresher
import com.assismohanty.awaken.core.domain.DayRolloverUseCase
import com.assismohanty.awaken.core.domain.GenerateDirectiveUseCase
import com.assismohanty.awaken.core.domain.ObserveHunterStateUseCase
import com.assismohanty.awaken.core.domain.ObserveTodayUseCase
import com.assismohanty.awaken.core.domain.RerollQuestUseCase
import com.assismohanty.awaken.core.domain.RunRolloverUseCase
import com.assismohanty.awaken.core.domain.SetRecoveryModeUseCase
import com.assismohanty.awaken.core.domain.SetRestDayUseCase
import com.assismohanty.awaken.core.domain.StartTrialUseCase
import com.assismohanty.awaken.core.domain.TrialReader
import com.assismohanty.awaken.core.domain.UndoCompletionUseCase
import com.assismohanty.awaken.engine.CritRoller
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FakeClock(var instant: Instant, private val zoneId: ZoneId = ZoneOffset.UTC) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }
}

/** Real Room (in-memory) + real assets + FakeClock, wired by hand the way Hilt wires production. */
class TestGraph(val clock: FakeClock = FakeClock(Instant.parse("2026-10-18T10:00:00Z"))) {
    private val context: Context = ApplicationProvider.getApplicationContext()

    val db: AwakenDatabase = Room.inMemoryDatabaseBuilder(context, AwakenDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val transactions = TransactionRunner(db)
    val ledger = LedgerRepository(db.ledgerDao(), transactions)
    val templates = TemplateRepository(context, Dispatchers.Unconfined)
    val reader = GameStateReader(
        db.hunterDao(), db.questDao(), db.completionDao(), db.ledgerDao(), db.dayRecordDao(),
        db.gateRunDao(), db.trialDao(), db.achievementDao(), clock,
    )

    val createHunter = CreateHunterUseCase(db.hunterDao(), transactions, clock)
    val generateDirective = GenerateDirectiveUseCase(
        db.hunterDao(), db.questDao(), db.habitDao(), reader, templates, transactions, clock,
    )
    val trialReader = TrialReader(db.questDao())
    val achievements = AchievementUnlocker(db.questDao(), db.achievementDao(), ledger, reader)
    val completeQuest = CompleteQuestUseCase(
        db.hunterDao(), db.questDao(), db.completionDao(), achievements, db.trialDao(), trialReader,
        ledger, reader, transactions, clock,
    )
    val undoCompletion = UndoCompletionUseCase(
        db.hunterDao(), db.questDao(), db.trialDao(), db.completionDao(), db.ledgerDao(), ledger, transactions, clock,
    )
    val rerollQuest = RerollQuestUseCase(db.hunterDao(), db.questDao(), ledger, reader, templates, transactions, clock)
    val advanceTrial = AdvanceTrialUseCase(db.questDao(), db.trialDao(), trialReader, reader, templates, transactions, clock)
    val rollover = DayRolloverUseCase(
        db.hunterDao(), db.questDao(), db.dayRecordDao(), ledger, reader, templates, generateDirective, advanceTrial,
        transactions, clock,
    )
    val startTrial = StartTrialUseCase(db.hunterDao(), db.questDao(), db.trialDao(), ledger, reader, templates, transactions, clock)

    /** A fresh DataStore file per graph, so notices never leak between tests. */
    private val noticeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val notices = NoticeStore(
        PreferenceDataStoreFactory.create(scope = noticeScope) { File(context.filesDir, "notices-${UUID.randomUUID()}.preferences_pb") },
    )
    val runRollover = RunRolloverUseCase(rollover, notices)
    val completionCenter = CompletionCenter(completeQuest, undoCompletion)
    val completeOnboarding = CompleteOnboardingUseCase(createHunter, achievements, rollover, transactions, clock)
    val narrative = NarrativeRepository(context, Dispatchers.Unconfined)
    val observeHunterState = ObserveHunterStateUseCase(
        db.hunterDao(), db.questDao(), db.dayRecordDao(), db.trialDao(), ledger, clock,
    )
    val observeToday = ObserveTodayUseCase(
        observeHunterState, db.hunterDao(), db.questDao(), reader, db.trialDao(), trialReader, narrative, clock,
    )
    val dayRefresher = DayRefresher(runRollover)
    val setRestDay = SetRestDayUseCase(db.hunterDao(), db.dayRecordDao(), transactions, clock)
    val setRecoveryMode = SetRecoveryModeUseCase(db.hunterDao(), db.dayRecordDao(), transactions, clock)

    fun today(): LocalDate = clock.gameDate(GameDay.DEFAULT_DAY_START_HOUR)

    suspend fun seedHunter() {
        createHunter(name = "Test Hunter", focusStats = listOf(Stat.STR, Stat.INT))
    }

    /** Inserts a quest for today with exact properties; [crit] picks an id whose fnv1a64 roll matches. */
    suspend fun fixtureQuest(
        name: String,
        difficulty: Difficulty = Difficulty.NORMAL,
        verification: Verification = Verification.SELF,
        type: QuestType = QuestType.SIDE,
        crit: Boolean = false,
    ): QuestInstanceEntity {
        val id = generateSequence(0) { it + 1 }.map { "$name-$it" }.first { CritRoller.isCrit(it) == crit }
        val quest = QuestInstanceEntity(
            id = id, gameDate = today(), type = type, templateId = null, habitId = null, stat = Stat.STR,
            secondaryStat = null, title = name, description = name, unit = "reps", target = 10, difficulty = difficulty,
            verification = verification, minDurationSec = 0, status = QuestStatus.ACTIVE, createdAt = clock.now(),
        )
        db.questDao().insertAll(listOf(quest))
        return quest
    }

    suspend fun grantShield() {
        ledger.insertIdempotent(
            listOf(
                LedgerEntryEntity(
                    kind = LedgerKind.ITEM, itemId = LedgerRepository.STREAK_SHIELD_ITEM_ID, delta = 1,
                    sourceType = SourceType.SHOP, sourceId = "test-shield", gameDate = today(), createdAt = clock.now(),
                ),
            ),
        )
    }

    fun close() {
        noticeScope.cancel()
        db.close()
    }
}
