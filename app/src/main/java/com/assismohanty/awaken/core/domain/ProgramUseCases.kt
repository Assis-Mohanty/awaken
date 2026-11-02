package com.assismohanty.awaken.core.domain

import android.content.Context
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CampaignDao
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HabitDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.ProgramDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.core.data.db.entity.HabitEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramEntity
import com.assismohanty.awaken.core.data.db.entity.ProgramMarkEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.ProgramBreakChoice
import com.assismohanty.awaken.engine.ProgramDay
import com.assismohanty.awaken.engine.ProgramRuleKey
import com.assismohanty.awaken.engine.ProgramRules
import com.assismohanty.awaken.engine.ProgramSpec
import com.assismohanty.awaken.engine.ProgramStart
import com.assismohanty.awaken.engine.ProgramStatus
import com.assismohanty.awaken.engine.QuestRules
import com.assismohanty.awaken.engine.BedtimeVerdict
import com.assismohanty.awaken.engine.model.HabitType
import com.assismohanty.awaken.engine.model.HunterContext
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.VerificationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Localised words for a program's rules and name; the engine decides targets and rules (GD §16.15). */
class ProgramText @Inject constructor(@ApplicationContext private val context: Context) {
    fun ruleTitle(key: ProgramRuleKey, plain: Boolean): String = context.getString(
        when (key) {
            ProgramRuleKey.WALK -> if (plain) R.string.program_rule_walk_plain else R.string.program_rule_walk
            ProgramRuleKey.READ -> R.string.program_rule_read
            ProgramRuleKey.FOCUS_15 -> R.string.program_rule_focus_15
            ProgramRuleKey.PLAN -> R.string.program_rule_plan
            ProgramRuleKey.WORKOUT -> if (plain) R.string.program_rule_workout_plain else R.string.program_rule_workout
            ProgramRuleKey.FOCUS_25 -> R.string.program_rule_focus_25
            ProgramRuleKey.STEPS_10K -> if (plain) R.string.program_rule_steps_10k_plain else R.string.program_rule_steps_10k
        },
    )
}

/**
 * A program's Health rule on a day whose Health check is unavailable (Health off, or that permission gone) becomes its plain
 * SELF form for that day, so the run is not broken by a check nobody can pass (ENGINE_DECISIONS #148). Every other habit is
 * returned unchanged; the stored habit is never rewritten, so the Health form returns once Health does.
 */
class ProgramHabitForDay @Inject constructor(
    private val programDao: ProgramDao,
    private val text: ProgramText,
) {
    suspend fun adapt(habit: HabitEntity, context: HunterContext): HabitEntity {
        val programId = habit.programId ?: return habit
        if (habit.verification.source != VerificationSource.HEALTH) return habit
        if (context.healthAvailable && habit.verification !in context.unavailableHealth) return habit
        val spec = programDao.get(programId)?.let { ProgramRules.spec(it.programKey) } ?: return habit
        val rule = ProgramRules.healthRuleFor(spec, habit.verification, habit.target) ?: return habit
        val plain = ProgramRules.effective(rule, healthOn = false)
        return habit.copy(title = text.ruleTitle(rule.key, plain = true), verification = plain.verification, target = plain.target, unit = plain.unit)
    }
}

sealed interface StartProgramResult {
    data class Started(val programId: String) : StartProgramResult
    data class Refused(val reason: ProgramStart) : StartProgramResult
    data object UnknownProgram : StartProgramResult
}

/**
 * Starts a program (GD §16.15): the run row and one read-only habit per rule, in one transaction, then today's directive is
 * regenerated so the rules appear at once. Needs enough free habit slots, and no other program or Campaign (Q8).
 */
class StartProgramUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val habitDao: HabitDao,
    private val programDao: ProgramDao,
    private val campaignDao: CampaignDao,
    private val generateDirective: GenerateDirectiveUseCase,
    private val text: ProgramText,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(key: String): StartProgramResult {
        val spec = ProgramRules.spec(key) ?: return StartProgramResult.UnknownProgram
        val profile = hunterDao.getProfile() ?: return StartProgramResult.UnknownProgram
        val today = clock.gameDate(profile.dayStartHour)
        val result = transactions.run {
            val free = QuestRules.MAX_ACTIVE_CUSTOM - habitDao.countActive()
            val check = ProgramRules.canStart(spec, free, campaignDao.active() != null, programDao.active() != null)
            if (check != ProgramStart.OK) return@run StartProgramResult.Refused(check)
            val now = clock.now()
            val id = UUID.randomUUID().toString()
            programDao.upsert(ProgramEntity(id, spec.key, runStartDate = today, status = ProgramStatus.ACTIVE, startedAt = now))
            spec.rules.forEach { rule ->
                val effective = ProgramRules.effective(rule, healthOn = profile.healthEnabled)
                val plain = effective.verification != rule.verification
                habitDao.upsert(
                    HabitEntity(
                        id = UUID.randomUUID().toString(),
                        title = text.ruleTitle(rule.key, plain),
                        stat = effective.stat,
                        difficulty = effective.difficulty,
                        verification = effective.verification,
                        daysOfWeekMask = ALL_DAYS,
                        target = effective.target,
                        unit = effective.unit,
                        active = true,
                        createdAt = now,
                        type = if (effective.timed) HabitType.TIMED else HabitType.CHECK,
                        programId = id,
                    ),
                )
            }
            StartProgramResult.Started(id)
        }
        if (result is StartProgramResult.Started) generateDirective(today)
        return result
    }

    private companion object {
        const val ALL_DAYS = 0b1111111
    }
}

/** How each day of the current run stands, for the calendar. */
enum class ProgramMark { KEPT, REST, BROKEN, TODAY, UPCOMING }

/** The Programs screen's picture of the current (or last) run. */
data class ProgramView(
    val program: ProgramEntity,
    val spec: ProgramSpec,
    /** Day k of today, or null once the run has ended. */
    val dayToday: Int?,
    /** One entry per day of the run, in order. */
    val marks: List<ProgramMark>,
    val brokenDays: Int,
    /** The most recent broken day nobody has answered yet: the app asks about it. */
    val pendingBreak: LocalDate?,
    val ruleTitles: List<String>,
)

/** Everything derived about a program run from the quests and day records (GD §16.15). Nothing is stored per day. */
class ProgramReader @Inject constructor(
    private val programDao: ProgramDao,
    private val questDao: QuestDao,
    private val dayRecordDao: DayRecordDao,
    private val screenTimeDao: ScreenTimeDao,
) {
    /** The verdict on a finished [date] of the run: every rule done → kept, else rest on a rest/recovery day, else broken. */
    suspend fun dayVerdict(program: ProgramEntity, spec: ProgramSpec, date: LocalDate): ProgramDay {
        val habits = programDao.habits(program.id)
        val quests = questDao.getForDate(date)
        val rulesDone = habits.all { habit -> quests.any { it.habitId == habit.id && it.status == QuestStatus.COMPLETED } }
        // The phone-down night counts only on days that have one (the feature is on and usage was readable that morning), and
        // a night whose usage could not be read when it settled is not asked either (ENGINE_DECISIONS #147).
        val night = if (spec.needsPhoneDownNight) quests.firstOrNull { it.type == QuestType.BEDTIME } else null
        val nightDone = night == null || night.status == QuestStatus.COMPLETED || night.nightVerdict == BedtimeVerdict.NO_DATA
        val outcome = dayRecordDao.get(date)?.outcome
        val restOrRecovery = outcome == DayOutcome.REST || outcome == DayOutcome.RECOVERY
        return ProgramRules.dayVerdict(allRulesDone = rulesDone && nightDone, restOrRecovery = restOrRecovery)
    }

    /** Verdicts of every finalized day from the run's start through [through]. */
    suspend fun verdicts(program: ProgramEntity, spec: ProgramSpec, through: LocalDate): Map<LocalDate, ProgramDay> {
        val last = minOf(through, ProgramRules.lastDate(program.runStartDate, spec))
        val result = LinkedHashMap<LocalDate, ProgramDay>()
        var date = program.runStartDate
        while (!date.isAfter(last)) {
            result[date] = dayVerdict(program, spec, date)
            date = date.plusDays(1)
        }
        return result
    }

    /** The newest broken day in the run that has no answer yet. */
    fun pendingBreak(verdicts: Map<LocalDate, ProgramDay>, marks: List<ProgramMarkEntity>): LocalDate? {
        val answered = marks.map { it.gameDate }.toSet()
        return verdicts.filter { it.value == ProgramDay.BROKEN && it.key !in answered }.keys.maxOrNull()
    }

    suspend fun lastFinalized(): LocalDate? = dayRecordDao.lastFinalizedDate()
}

/**
 * At each finished day (GD §16.15): a break nobody answered becomes Continue, then the day is judged; the last day ends the run
 * as COMPLETED (no broken day) or FINISHED, archives the program's habits, and unlocks the program's achievement. Nothing is
 * ever deducted. Idempotent: a mark or an ended program is never written twice.
 */
class ProgramRolloverUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val programDao: ProgramDao,
    private val habitDao: HabitDao,
    private val reader: ProgramReader,
    private val achievements: AchievementUnlocker,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate) = transactions.run {
        val program = programDao.active() ?: return@run
        val spec = ProgramRules.spec(program.programKey) ?: return@run
        if (date.isBefore(program.runStartDate)) return@run
        // Breaks from earlier days that went unanswered are Continue now (vector #133).
        val earlier = reader.verdicts(program, spec, date.minusDays(1))
        val marks = programDao.marks(program.id)
        earlier.filter { it.value == ProgramDay.BROKEN && marks.none { m -> m.gameDate == it.key } }.keys.forEach {
            programDao.insertMark(ProgramMarkEntity(program.id, it, ProgramRules.DEFAULT_BREAK))
        }
        if (!ProgramRules.isLastDay(program.runStartDate, spec, date)) return@run
        val broken = reader.verdicts(program, spec, date).values.count { it == ProgramDay.BROKEN }
        val status = ProgramRules.endStatus(broken)
        val now = clock.now()
        programDao.upsert(program.copy(status = status, endedAt = now))
        programDao.habits(program.id).forEach { habitDao.upsert(it.copy(active = false)) }
        if (status == ProgramStatus.COMPLETED) hunterDao.getProfile()?.let { achievements.unlockNew(it, date, now) }
    }
}

sealed interface ChooseBreakResult {
    data object Done : ChooseBreakResult
    data object NothingToAnswer : ChooseBreakResult
}

/** Answers the question after a broken day. Restart makes today day 1; Continue keeps the count going with the day marked. */
class ChooseProgramBreakUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val programDao: ProgramDao,
    private val reader: ProgramReader,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(choice: ProgramBreakChoice): ChooseBreakResult = transactions.run {
        val profile = hunterDao.getProfile() ?: return@run ChooseBreakResult.NothingToAnswer
        val program = programDao.active() ?: return@run ChooseBreakResult.NothingToAnswer
        val spec = ProgramRules.spec(program.programKey) ?: return@run ChooseBreakResult.NothingToAnswer
        val finalized = reader.lastFinalized() ?: return@run ChooseBreakResult.NothingToAnswer
        val pending = reader.pendingBreak(reader.verdicts(program, spec, finalized), programDao.marks(program.id))
            ?: return@run ChooseBreakResult.NothingToAnswer
        programDao.insertMark(ProgramMarkEntity(program.id, pending, choice))
        if (choice == ProgramBreakChoice.RESTART) programDao.upsert(program.copy(runStartDate = clock.gameDate(profile.dayStartHour)))
        ChooseBreakResult.Done
    }
}

/** Stops the run: the habits are archived, nothing earned is touched and nothing is deducted (GD §16.15). */
class AbandonProgramUseCase @Inject constructor(
    private val programDao: ProgramDao,
    private val habitDao: HabitDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke() = transactions.run {
        val program = programDao.active() ?: return@run
        programDao.upsert(program.copy(status = ProgramStatus.ABANDONED, endedAt = clock.now()))
        programDao.habits(program.id).forEach { habitDao.upsert(it.copy(active = false)) }
    }
}

/** The current (or last) run with its marks and any open question. Read-only. */
class ObserveProgramUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val programDao: ProgramDao,
    private val reader: ProgramReader,
    private val text: ProgramText,
    private val clock: Clock,
) {
    operator fun invoke(ticks: Flow<Any>): Flow<ProgramView?> =
        combine(programDao.observeCurrent(), ticks) { program, _ -> program?.let { view(it) } }

    private suspend fun view(program: ProgramEntity): ProgramView? {
        val spec = ProgramRules.spec(program.programKey) ?: return null
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val finalized = reader.lastFinalized()
        val verdicts = if (finalized == null) emptyMap() else reader.verdicts(program, spec, finalized)
        val running = program.status == ProgramStatus.ACTIVE
        val marks = (0 until spec.days).map { i ->
            val date = program.runStartDate.plusDays(i.toLong())
            when {
                verdicts[date] == ProgramDay.KEPT -> ProgramMark.KEPT
                verdicts[date] == ProgramDay.REST -> ProgramMark.REST
                verdicts[date] == ProgramDay.BROKEN -> ProgramMark.BROKEN
                running && date == today -> ProgramMark.TODAY
                else -> ProgramMark.UPCOMING
            }
        }
        val titles = programDao.habits(program.id).map { it.title }
        return ProgramView(
            program = program,
            spec = spec,
            dayToday = ProgramRules.dayNumber(program.runStartDate, today).takeIf { running && it in 1..spec.days },
            marks = marks,
            brokenDays = marks.count { it == ProgramMark.BROKEN },
            pendingBreak = if (running) reader.pendingBreak(verdicts, programDao.marks(program.id)) else null,
            ruleTitles = titles.ifEmpty { spec.rules.map { text.ruleTitle(it.key, plain = false) } },
        )
    }
}

/** For each program, whether it can start now and why not (GD §16.15). Read-only. */
class ProgramAvailabilityUseCase @Inject constructor(
    private val habitDao: HabitDao,
    private val campaignDao: CampaignDao,
    private val programDao: ProgramDao,
) {
    suspend operator fun invoke(): Map<String, ProgramStart> {
        val free = QuestRules.MAX_ACTIVE_CUSTOM - habitDao.countActive()
        val campaign = campaignDao.active() != null
        val program = programDao.active() != null
        return ProgramRules.CATALOG.associate { it.key to ProgramRules.canStart(it, free, campaign, program) }
    }
}
