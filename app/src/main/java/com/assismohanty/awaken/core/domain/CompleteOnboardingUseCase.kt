package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime
import javax.inject.Inject

sealed interface OnboardingResult {
    /** [unlocked] holds the achievement ids granted on completion (at least "awakened"). */
    data class Done(val unlocked: List<String>) : OnboardingResult
    data object InvalidName : OnboardingResult
    data object InvalidFocus : OnboardingResult
}

/**
 * Awakening (DESIGN_SYSTEM §4.1): creates the hunter with calibrated baseline tiers, grants "Awakened", then runs the
 * first rollover, which generates the first directive (with its Easy starter quest, GD §6.3). Health is enabled before
 * that rollover, so day one can already offer verified quests.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val createHunter: CreateHunterUseCase,
    private val achievements: AchievementUnlocker,
    private val rollover: DayRolloverUseCase,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        name: String,
        focusStats: Set<Stat>,
        training: Awakening.Answer,
        study: Awakening.Answer,
        sleep: Awakening.Answer,
        reminderTime: LocalTime,
        /** All three Health Connect read permissions were granted during step 5 (ARCH §3.4). */
        healthEnabled: Boolean = false,
    ): OnboardingResult {
        if (!Awakening.isValidName(name)) return OnboardingResult.InvalidName
        if (focusStats.size != 2) return OnboardingResult.InvalidFocus
        val unlocked = transactions.run {
            val profile = createHunter(
                name = name.trim(),
                focusStats = Stat.entries.filter { it in focusStats },
                baselineTiers = Awakening.baselineTiers(training, study, sleep),
                reminderTime = reminderTime,
                healthEnabled = healthEnabled,
            )
            achievements.unlockNew(profile, clock.gameDate(profile.dayStartHour), clock.now())
        }
        rollover()
        return OnboardingResult.Done(unlocked)
    }
}
