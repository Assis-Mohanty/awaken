package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.repo.TemplateRepository
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.SettingsRules
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the Settings screen edits (DS §4.7). */
data class SettingsModel(
    val name: String,
    val focusStats: List<Stat>,
    val dayStartHour: Int,
    val reminder: LocalTime?,
    val equipment: Set<String>,
    /** Every equipment id the quest library knows (the template vocabulary). */
    val equipmentOptions: List<String>,
    val injuryMode: Boolean,
    val injuryTags: Set<String>,
    /** Verified quests are on (Health Connect connected and granted). */
    val healthEnabled: Boolean = false,
)

class ObserveSettingsUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val templates: TemplateRepository,
) {
    operator fun invoke(): Flow<SettingsModel?> = hunterDao.observeProfile().map { profile ->
        profile?.let {
            SettingsModel(
                name = it.name,
                focusStats = it.focusStats,
                dayStartHour = it.dayStartHour,
                reminder = it.reminderTime?.let(LocalTime::parse),
                equipment = it.ownedEquipment,
                equipmentOptions = templates.library().equipment,
                injuryMode = it.injuryMode,
                injuryTags = it.injuryTags,
                healthEnabled = it.healthEnabled,
            )
        }
    }
}

sealed interface SettingsResult {
    data object Saved : SettingsResult
    data object InvalidName : SettingsResult
    data object InvalidFocus : SettingsResult
    /** The hour isn't offered, or the change would move today back into a finalized day (ENGINE_DECISIONS #47). */
    data object DayStartBlocked : SettingsResult
    data object NoHunter : SettingsResult
}

/**
 * Profile edits from Settings. These change the profile row only (never the ledger); directive-shaping edits (focus,
 * equipment, injury) apply from the next generated directive, and today's quests stay as they are.
 */
class UpdateSettingsUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val templates: TemplateRepository,
    private val clock: Clock,
) {
    suspend fun rename(name: String): SettingsResult {
        if (!Awakening.isValidName(name)) return SettingsResult.InvalidName
        return withProfile { hunterDao.setName(name.trim()) }
    }

    suspend fun setFocusStats(first: Stat, second: Stat): SettingsResult {
        if (first == second) return SettingsResult.InvalidFocus
        return withProfile { hunterDao.setFocusStats(first, second) }
    }

    suspend fun setDayStartHour(hour: Int): SettingsResult {
        val profile = hunterDao.getProfile() ?: return SettingsResult.NoHunter
        if (hour == profile.dayStartHour) return SettingsResult.Saved
        if (!SettingsRules.canChangeDayStart(clock.now(), clock.zone(), profile.dayStartHour, hour)) {
            return SettingsResult.DayStartBlocked
        }
        hunterDao.setDayStartHour(hour)
        return SettingsResult.Saved
    }

    /** Null clears the reminder. */
    suspend fun setReminder(time: LocalTime?): SettingsResult = withProfile { hunterDao.setReminderTime(time?.toString()) }

    suspend fun setEquipment(owned: Set<String>): SettingsResult =
        withProfile { hunterDao.setOwnedEquipment(SettingsRules.validEquipment(owned, templates.library().equipment)) }

    suspend fun setInjury(enabled: Boolean, tags: Set<String>): SettingsResult =
        withProfile { hunterDao.setInjury(enabled, SettingsRules.normalizeInjuryTags(tags)) }

    private suspend fun withProfile(write: suspend () -> Unit): SettingsResult {
        if (hunterDao.getProfile() == null) return SettingsResult.NoHunter
        write()
        return SettingsResult.Saved
    }
}
