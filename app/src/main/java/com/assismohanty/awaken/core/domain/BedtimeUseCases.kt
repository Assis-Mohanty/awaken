package com.assismohanty.awaken.core.domain

import android.content.Context
import com.assismohanty.awaken.R
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.CompletionDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.db.dao.ScreenTimeDao
import com.assismohanty.awaken.core.data.db.entity.BedtimeRuleEntity
import com.assismohanty.awaken.core.data.db.entity.HunterProfileEntity
import com.assismohanty.awaken.core.data.db.entity.QuestInstanceEntity
import com.assismohanty.awaken.core.data.repo.GameStateReader
import com.assismohanty.awaken.core.data.usage.EventsRead
import com.assismohanty.awaken.core.data.usage.UsageGateway
import com.assismohanty.awaken.core.data.usage.UsageRead
import com.assismohanty.awaken.core.data.usage.trackedMillis
import com.assismohanty.awaken.engine.BedtimeRules
import com.assismohanty.awaken.engine.BedtimeSetting
import com.assismohanty.awaken.engine.BedtimeVerdict
import com.assismohanty.awaken.engine.BedtimeWindow
import com.assismohanty.awaken.engine.XpCalculator
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.VerificationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Localised words for the BEDTIME quest; the engine only decides its numbers (GD §16.12). */
class BedtimeText @Inject constructor(@ApplicationContext private val context: Context) {
    val title: String get() = context.getString(R.string.bedtime_quest_title)
    val description: String get() = context.getString(R.string.bedtime_quest_description)
}

internal fun BedtimeRuleEntity.toSetting() = BedtimeSetting(effectiveFrom, enabled, bedtimeMin, wakeMin)

/** How far before a night the event log is probed to tell "an idle phone" from "an unreadable log". */
private val LOG_PROBE = Duration.ofHours(24)

/** Reads the phone-down night rules, windows and verdicts the same way for every screen and worker (GD §16.12). */
class BedtimeReader @Inject constructor(
    private val screenTimeDao: ScreenTimeDao,
    private val questDao: QuestDao,
    private val usage: UsageGateway,
    private val clock: Clock,
) {
    suspend fun settings(): List<BedtimeSetting> = screenTimeDao.allBedtime().map { it.toSetting() }

    fun observeSettings(): Flow<List<BedtimeSetting>> = screenTimeDao.observeBedtime().map { rows -> rows.map { it.toSetting() } }

    suspend fun settingFor(wakeDay: LocalDate): BedtimeSetting? = BedtimeRules.settingFor(wakeDay, settings())

    suspend fun windowFor(wakeDay: LocalDate): BedtimeWindow? =
        settingFor(wakeDay)?.let { BedtimeRules.window(wakeDay, it, clock.zone()) }

    suspend fun quest(wakeDay: LocalDate): QuestInstanceEntity? =
        questDao.getForDate(wakeDay).firstOrNull { it.type == QuestType.BEDTIME }

    /**
     * The verdict on the night that ended on [wakeDay]'s morning; null while the night is not over (or no night is set).
     * Fail closed (GD §16.12): no usage access, or a log that cannot be read, is NO_DATA and is never paid. A night so
     * quiet that the window holds no events at all counts as kept only when the log itself is demonstrably alive just
     * before it; otherwise an idle phone and a broken log would look the same.
     */
    suspend fun verdict(wakeDay: LocalDate): BedtimeVerdict? {
        val window = windowFor(wakeDay) ?: return null
        if (clock.now().isBefore(window.end)) return null
        val used: Map<String, Long>? = when (val read = usage.read(window.start, window.end)) {
            UsageRead.NoAccess -> null
            is UsageRead.Tracked -> read.millis
            UsageRead.Unavailable ->
                if (usage.events(window.start.minus(LOG_PROBE), window.start) is EventsRead.Tracked) emptyMap() else null
        }
        return BedtimeRules.verdict(used?.let { BedtimeRules.nonAllowedMillis(it, usage.nightAllowedPackages()) })
    }
}

/** The BEDTIME quest draft for [wakeDay], or null when no night is set that day or usage access is missing (GD §16.12). */
class BedtimePlanner @Inject constructor(
    private val reader: BedtimeReader,
    private val usage: UsageGateway,
    private val text: BedtimeText,
) {
    suspend fun draftFor(profile: HunterProfileEntity, wakeDay: LocalDate): QuestDraft? =
        if (reader.settingFor(wakeDay) != null && usage.hasAccess()) {
            BedtimeRules.bedtimeQuest(profile.hunterId, wakeDay, text.title, text.description)
        } else {
            null
        }
}

/**
 * Settles the night that ended on [wakeDay]'s morning (GD §16.12): kept → completed through the normal pipeline; broken
 * or unreadable → expired, nothing deducted. Does nothing while the night is still running or when the quest is already
 * settled, so the foreground check, the 15-minute worker and the rollover can all call it.
 */
class SettleBedtimeUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val reader: BedtimeReader,
    private val completeQuest: CompleteQuestUseCase,
) {
    suspend operator fun invoke(wakeDay: LocalDate): CompleteQuestResult? {
        val quest = reader.quest(wakeDay)?.takeIf { it.isOpen } ?: return null
        val verdict = reader.verdict(wakeDay) ?: return null
        if (verdict != BedtimeVerdict.KEPT) {
            questDao.expireNight(quest.id, verdict)
            return null
        }
        return completeQuest.completeVerified(quest.id, VerificationSource.USAGE)
    }
}

sealed interface SetBedtimeResult {
    data class Saved(val effectiveFrom: LocalDate) : SetBedtimeResult
    data object InvalidWindow : SetBedtimeResult
}

/**
 * Changes the phone-down night (GD §16.12). A change never reaches the night already ahead: turning it on counts tonight,
 * but every edit applies from the night after, and turning it off stops tonight. Rows are versions, never rewritten.
 */
class SetBedtimeUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val screenTimeDao: ScreenTimeDao,
    private val reader: BedtimeReader,
    private val clock: Clock,
) {
    suspend fun enableOrEdit(bedtimeMinute: Int, wakeMinute: Int): SetBedtimeResult {
        val profile = hunterDao.getProfile() ?: return SetBedtimeResult.InvalidWindow
        if (!BedtimeRules.isValidWindow(bedtimeMinute, wakeMinute, profile.dayStartHour)) return SetBedtimeResult.InvalidWindow
        val today = clock.gameDate(profile.dayStartHour)
        val governsTonight = BedtimeRules.settingFor(today.plusDays(1), reader.settings()) != null
        val from = if (governsTonight) BedtimeRules.effectiveFromForEdit(today) else BedtimeRules.effectiveFromForEnable(today)
        screenTimeDao.upsertBedtime(BedtimeRuleEntity(from, enabled = true, bedtimeMin = bedtimeMinute, wakeMin = wakeMinute))
        return SetBedtimeResult.Saved(from)
    }

    suspend fun disable() {
        val profile = hunterDao.getProfile() ?: return
        val from = BedtimeRules.effectiveFromForEnable(clock.gameDate(profile.dayStartHour))
        val current = reader.settings().lastOrNull()
        // Later versions would switch it back on, so a pending change is dropped with the rest.
        screenTimeDao.deleteBedtimeAfter(from)
        screenTimeDao.upsertBedtime(
            BedtimeRuleEntity(from, enabled = false, bedtimeMin = current?.bedtimeMinute ?: BedtimeRules.DEFAULT_BEDTIME, wakeMin = current?.wakeMinute ?: BedtimeRules.DEFAULT_WAKE),
        )
    }
}

/** The result of last night, shown on Today in the morning. */
sealed interface LastNight {
    /** Quest exists and the night is still running. */
    data class InProgress(val window: BedtimeWindow) : LastNight

    /** The night is over but not settled yet (the next check settles it). */
    data object Pending : LastNight
    data class Kept(val xp: Int) : LastNight
    data object Broken : LastNight
    data object NoData : LastNight
}

/** Tonight's window for the evening card. [windDownFrom] is when the wind-down opens. */
data class TonightNight(val window: BedtimeWindow, val xpAtStake: Int, val windDownFrom: Instant)

/** Everything Today needs for the phone-down card. */
data class BedtimeToday(
    /** A night is set but usage access is missing: nothing can be verified, so nothing is offered. */
    val needsAccess: Boolean,
    val lastNight: LastNight?,
    val tonight: TonightNight?,
    val now: Instant,
)

/** Builds [BedtimeToday] for the current game-day. Read-only: never settles or pays. */
class LoadBedtimeTodayUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val completionDao: CompletionDao,
    private val reader: BedtimeReader,
    private val usage: UsageGateway,
    private val gameState: GameStateReader,
    private val clock: Clock,
) {
    /** Null when there is no hunter or no night is set for last night or tonight. */
    suspend operator fun invoke(): BedtimeToday? {
        val profile = hunterDao.getProfile() ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val now = clock.now()
        val tonightSetting = reader.settingFor(today.plusDays(1))
        val quest = reader.quest(today)
        if (tonightSetting == null && quest == null) return null
        val streak = gameState.hunterContext(profile, today).streak
        val tonight = tonightSetting?.let {
            val window = BedtimeRules.window(today.plusDays(1), it, clock.zone())
            TonightNight(window, XpCalculator.questXp(BedtimeRules.BEDTIME_DIFFICULTY, streak, VerificationSource.USAGE, crit = false), BedtimeRules.windDownOpensAt(window))
        }
        val last = quest?.let { lastNight(it, today, now) }
        return BedtimeToday(needsAccess = !usage.hasAccess(), lastNight = last, tonight = tonight, now = now)
    }

    private suspend fun lastNight(quest: QuestInstanceEntity, today: LocalDate, now: Instant): LastNight = when (quest.status) {
        QuestStatus.COMPLETED -> LastNight.Kept(completionDao.latestActive(quest.id)?.xp ?: 0)
        // The verdict stored at settlement wins; nights settled before it was stored are read again.
        QuestStatus.EXPIRED, QuestStatus.MISSED ->
            if ((quest.nightVerdict ?: reader.verdict(today)) == BedtimeVerdict.BROKEN) LastNight.Broken else LastNight.NoData
        else -> reader.windowFor(today)?.takeIf { now.isBefore(it.end) }?.let { LastNight.InProgress(it) } ?: LastNight.Pending
    }
}

/** Settles the night that ended this morning, if any. Called from the foreground check and the 15-minute worker. */
class SettleCurrentBedtimeUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val settle: SettleBedtimeUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): CompleteQuestResult? {
        val profile = hunterDao.getProfile() ?: return null
        return settle(clock.gameDate(profile.dayStartHour))
    }
}

/** True when any version of the phone-down night is switched on, so the periodic check has a reason to run. */
fun List<BedtimeRuleEntity>.anyEnabled(): Boolean = any { it.enabled }
