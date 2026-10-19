package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.DayRecordDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.engine.ReminderRules
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import java.time.LocalTime
import javax.inject.Inject

/** What a daily notification says. The app turns it into text (plurals in strings.xml). */
sealed interface ReminderContent {
    /** "Your directive is ready: [count] quests." */
    data class Directive(val count: Int) : ReminderContent

    /** "[remaining] quests from clearing the day." */
    data class LastCall(val remaining: Int) : ReminderContent
}

/**
 * Decides, at fire time, whether there is anything worth a notification and what it says (ARCH §3.5). Everything
 * returns null when the reminder is off: reminder off silences both notifications (ENGINE_DECISIONS #53).
 */
class BuildReminderUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val dayRecordDao: DayRecordDao,
    private val runRollover: RunRolloverUseCase,
    private val clock: Clock,
) {
    /** Runs the rollover first, so today's directive exists even if the app hasn't been opened. */
    suspend fun directive(): ReminderContent? {
        runRollover()
        val profile = hunterDao.getProfile() ?: return null
        if (profile.reminderTime == null) return null
        val today = clock.gameDate(profile.dayStartHour)
        val count = questDao.getForDate(today).count { it.status != QuestStatus.REROLLED && it.type in DIRECTIVE_TYPES }
        return if (count > 0) ReminderContent.Directive(count) else null
    }

    suspend fun lastCall(): ReminderContent? {
        val profile = hunterDao.getProfile() ?: return null
        val reminder = profile.reminderTime?.let(LocalTime::parse) ?: return null
        val today = clock.gameDate(profile.dayStartHour)
        val quests = questDao.getForDate(today).filter { it.status != QuestStatus.REROLLED }
        val record = dayRecordDao.get(today)
        val day = quests.toDayRecord(today, restDay = record?.restDay ?: false, recovery = record?.recovery ?: false)
        if (!ReminderRules.shouldLastCall(reminder, day)) return null
        // Nothing left to do (everything expired or done but not enough to clear) is not worth a nudge.
        if (quests.none { it.isOpen }) return null
        val mainOpen = quests.any { it.type == QuestType.MAIN && it.isOpen }
        return ReminderContent.LastCall(ReminderRules.questsToClear(day, mainOpen))
    }

    private companion object {
        val DIRECTIVE_TYPES = setOf(QuestType.MAIN, QuestType.SIDE, QuestType.HABIT)
    }
}
