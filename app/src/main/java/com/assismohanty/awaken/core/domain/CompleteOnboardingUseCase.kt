package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.Awakening
import com.assismohanty.awaken.engine.IdentityCatalog
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime
import javax.inject.Inject

sealed interface OnboardingResult {
    data object Done : OnboardingResult
    data object InvalidName : OnboardingResult
    data object InvalidFocus : OnboardingResult
}

/**
 * Getting started (DESIGN_SYSTEM §4.1): creates the profile with calibrated baseline tiers, then runs the first
 * rollover, which generates the first plan (with its Easy starter quest, GD §6.3). Health is enabled before that
 * rollover, so day one can already offer verified quests.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val createHunter: CreateHunterUseCase,
    private val rollover: DayRolloverUseCase,
    private val transactions: TransactionRunner,
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
        /** "Who are you becoming?" picks in order (GD §14.7); empty when skipped. */
        identities: List<String> = emptyList(),
    ): OnboardingResult {
        if (!Awakening.isValidName(name)) return OnboardingResult.InvalidName
        if (focusStats.size != 2) return OnboardingResult.InvalidFocus
        transactions.run {
            createHunter(
                name = name.trim(),
                focusStats = Stat.entries.filter { it in focusStats },
                baselineTiers = Awakening.baselineTiers(training, study, sleep),
                reminderTime = reminderTime,
                healthEnabled = healthEnabled,
                identities = identities.take(IdentityCatalog.MAX_PICKS),
            )
        }
        rollover()
        return OnboardingResult.Done
    }
}
