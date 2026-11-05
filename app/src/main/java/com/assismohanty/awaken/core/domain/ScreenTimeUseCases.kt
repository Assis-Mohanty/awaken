package com.assismohanty.awaken.core.domain

import android.content.Context
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.core.data.db.entity.AppRuleEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.engine.AppCategory
import com.assismohanty.awaken.engine.AppLimitState
import com.assismohanty.awaken.engine.AppRule
import com.assismohanty.awaken.engine.DisciplineState
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.ScreenTimeRules
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.VerificationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

/** Localised words for the DISCIPLINE quest; the engine only decides its numbers (ENGINE_DECISIONS #89). */
class DisciplineText @Inject constructor(@ApplicationContext private val context: Context) {
    val title: String get() = context.getString(R.string.discipline_quest_title)
    val description: String get() = context.getString(R.string.discipline_quest_description)
}

internal fun AppRuleEntity.toRule() = AppRule(packageName, category, dailyLimitMinutes, effectiveFrom)

/** Rules, the on/off state and today's DISCIPLINE quest, read the same way by every screen and worker (GD §6.7). */
class ScreenTimeReader @Inject constructor(
    private val screenTimeDao: ScreenTimeDao,
    private val questDao: QuestDao,
    private val usage: UsageGateway,
    private val clock: Clock,
) {
    suspend fun rulesInForce(gameDate: LocalDate): Map<String, AppRule> =
        ScreenTimeRules.rulesFor(gameDate, screenTimeDao.allRules().map { it.toRule() })

    /** Screen Discipline runs on [gameDate]: on since then, usage access granted, and at least one limit to defend. */
    suspend fun runsOn(profile: HunterProfileEntity, gameDate: LocalDate): Boolean {
        val from = profile.screenTimeFrom ?: return false
        if (gameDate.isBefore(from) || !usage.hasAccess()) return false
        return ScreenTimeRules.evaluate(emptyMap(), rulesInForce(gameDate)).hasStake
    }

    suspend fun disciplineQuest(gameDate: LocalDate): QuestInstanceEntity? =
        questDao.getForDate(gameDate).firstOrNull { it.type == QuestType.DISCIPLINE }

    /** Usage over [gameDate] so far (or the whole day once it has ended). Only [UsageRead.Tracked] is real data. */
    suspend fun usageOn(profile: HunterProfileEntity, gameDate: LocalDate): UsageRead {
        val start = GameDay.startOf(gameDate, clock.zone(), profile.dayStartHour)
        val end = minOf(GameDay.endOf(gameDate, clock.zone(), profile.dayStartHour), clock.now())
        return usage.read(start, end)
    }
}

/**
 * Turning Screen Discipline on starts it tomorrow and gives every launcher app its default category (GD §6.7,
 * ENGINE_DECISIONS #88). Turning it off stops it at once; an open DISCIPLINE quest for today simply expires.
 */
class SetScreenTimeEnabledUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val seedRules: SeedAppRulesUseCase,
    private val reader: ScreenTimeReader,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(enabled: Boolean) {
        val profile = hunterDao.getProfile() ?: return
        val today = clock.gameDate(profile.dayStartHour)
        if (enabled) {
            if (profile.screenTimeFrom != null) return
            seedRules()
            hunterDao.setScreenTimeFrom(ScreenTimeRules.effectiveFromForEdit(today))
        } else {
            transactions.run {
                hunterDao.setScreenTimeFrom(null)
                reader.disciplineQuest(today)?.takeIf { it.isOpen }?.let { questDao.setStatus(it.id, QuestStatus.EXPIRED, completedAt = null) }
            }
        }
    }
}

/** Every launcher app without a rule gets its default category and limit, effective tomorrow (GD §6.7). */
class SeedAppRulesUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val screenTimeDao: ScreenTimeDao,
    private val usage: UsageGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val profile = hunterDao.getProfile() ?: return
        val from = ScreenTimeRules.effectiveFromForEdit(clock.gameDate(profile.dayStartHour))
        val ruled = screenTimeDao.ruledPackages().toSet()
        val fresh = usage.launcherApps().filter { it.packageName !in ruled }.map { app ->
            val category = ScreenTimeRules.defaultCategory(app.packageName, app.kind)
            AppRuleEntity(app.packageName, from, category, ScreenTimeRules.defaultLimit(category))
        }
        if (fresh.isNotEmpty()) screenTimeDao.upsertRules(fresh)
    }
}

sealed interface SetAppRuleResult {
    data object Saved : SetAppRuleResult
    data object InvalidLimit : SetAppRuleResult
}

/** A category or limit change, written as a new rule version for tomorrow; today never changes (ENGINE_DECISIONS #88). */
class SetAppRuleUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val screenTimeDao: ScreenTimeDao,
    private val clock: Clock,
) {
    suspend operator fun invoke(packageName: String, category: AppCategory, dailyLimitMinutes: Int?): SetAppRuleResult {
        val limit = if (category == AppCategory.DISTRACTING) dailyLimitMinutes else null
        if (limit != null && !ScreenTimeRules.isValidLimit(limit)) return SetAppRuleResult.InvalidLimit
        val profile = hunterDao.getProfile() ?: return SetAppRuleResult.Saved
        val from = ScreenTimeRules.effectiveFromForEdit(clock.gameDate(profile.dayStartHour))
        screenTimeDao.upsertRules(listOf(AppRuleEntity(packageName, from, category, limit)))
        return SetAppRuleResult.Saved
    }
}

/** Today's limits, what's at stake and what changed in this check. */
data class DisciplineCheck(
    val gameDate: LocalDate,
    val state: DisciplineState,
    val usageMillis: Map<String, Long>,
    /** False when usage couldn't be read: nothing is forfeited, warned about or paid on a guess. */
    val tracked: Boolean,
    val xpAtStake: Int,
    val quest: QuestInstanceEntity?,
    /** Limits with ≤ 5 min left that haven't been crossed: the caller warns once per app per day. */
    val warnings: List<AppLimitState>,
    /** True only for the check that forfeited the quest. */
    val forfeitedNow: Boolean,
)

/**
 * The live check (worker every 15 min + app foreground): reads today's usage, and on the first crossing forfeits the
 * DISCIPLINE quest in one transaction (GD §6.7). Losing usage access also expires it; usage that can't be read is
 * neither forfeited nor warned about (settlement then pays nothing). Never writes the ledger.
 */
class DisciplineSyncUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val reader: ScreenTimeReader,
    private val gameState: GameStateReader,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** Null when there is no hunter or Screen Discipline is off. */
    suspend operator fun invoke(): DisciplineCheck? {
        val profile = hunterDao.getProfile() ?: return null
        if (profile.screenTimeFrom == null) return null
        val today = clock.gameDate(profile.dayStartHour)
        val read = reader.usageOn(profile, today)
        val usageMillis = read.trackedMillis.orEmpty()
        val tracked = read is UsageRead.Tracked
        val state = ScreenTimeRules.evaluate(usageMillis, reader.rulesInForce(today))
        val streak = gameState.hunterContext(profile, today).streak
        val xp = XpCalculator.questXp(ScreenTimeRules.DISCIPLINE_DIFFICULTY, streak, VerificationSource.USAGE)
        var forfeited = false
        val quest = transactions.run {
            val open = reader.disciplineQuest(today)
            val crossed = tracked && state.breached
            if (open != null && open.isOpen && (crossed || read == UsageRead.NoAccess)) {
                questDao.setStatus(open.id, QuestStatus.EXPIRED, completedAt = null)
                forfeited = crossed
            }
            reader.disciplineQuest(today)
        }
        val warnings = if (tracked && quest?.isOpen == true) ScreenTimeRules.warningsDue(state) else emptyList()
        return DisciplineCheck(today, state, usageMillis, tracked, xp, quest, warnings, forfeited)
    }
}

/**
 * Settles a finished day's DISCIPLINE quest before the day is finalized (GD §6.7): held → completed through the normal
 * pipeline (priced on its own day, ENGINE_DECISIONS #91); crossed → expired. Fail closed: without usage access, or
 * when the day's usage can't be read, the quest is left open and finalization expires it unpaid.
 */
class SettleDisciplineUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val reader: ScreenTimeReader,
    private val completeQuest: CompleteQuestUseCase,
) {
    suspend operator fun invoke(gameDate: LocalDate): CompleteQuestResult? {
        val profile = hunterDao.getProfile() ?: return null
        val quest = reader.disciplineQuest(gameDate)?.takeIf { it.isOpen } ?: return null
        val usageMillis = reader.usageOn(profile, gameDate).trackedMillis ?: return null
        val state = ScreenTimeRules.evaluate(usageMillis, reader.rulesInForce(gameDate))
        if (state.breached) {
            questDao.setStatus(quest.id, QuestStatus.EXPIRED, completedAt = null)
            return null
        }
        return completeQuest.completeVerified(quest.id, VerificationSource.USAGE)
    }
}

/** The DISCIPLINE quest draft for [gameDate], or null when Screen Discipline doesn't run that day. */
class DisciplineQuestPlanner @Inject constructor(
    private val reader: ScreenTimeReader,
    private val text: DisciplineText,
) {
    suspend fun draftFor(profile: HunterProfileEntity, gameDate: LocalDate): QuestDraft? =
        if (reader.runsOn(profile, gameDate)) {
            ScreenTimeRules.disciplineQuest(profile.hunterId, gameDate, text.title, text.description)
        } else {
            null
        }
}
