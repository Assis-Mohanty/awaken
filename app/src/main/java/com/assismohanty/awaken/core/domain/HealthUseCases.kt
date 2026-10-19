package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.clock.gameDate
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import com.assismohanty.awaken.core.data.health.HealthGateway
import com.assismohanty.awaken.core.data.health.HealthReadException
import com.assismohanty.awaken.core.data.health.HealthStatus
import com.assismohanty.awaken.engine.HealthRules
import com.assismohanty.awaken.engine.model.VerificationSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

sealed interface HealthSyncResult {
    data class Synced(val completed: List<CompletionOutcome>) : HealthSyncResult
    /** Verified quests are off; nothing was read. */
    data object Disabled : HealthSyncResult
    /** Health Connect went away or access was revoked: verified quests were switched off (ARCH §3.4: no nagging). */
    data object Lost : HealthSyncResult
    /** A read failed this time (e.g. app left the foreground); try again later. Nothing changed. */
    data object ReadFailed : HealthSyncResult
}

/**
 * Reads today's Health Connect data and completes the HEALTH quests it satisfies (ARCH §3.4). Foreground only
 * (ENGINE_DECISIONS #51). Matching is [HealthRules]; rewards go through [CompleteQuestUseCase.completeVerified], so the
 * ledger rows are keyed by completionId like any other completion (GD §9.3). A quest already completed is skipped by
 * its status, so syncing again is a no-op.
 */
@Singleton
class HealthSyncUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val questDao: QuestDao,
    private val gateway: HealthGateway,
    private val completeQuest: CompleteQuestUseCase,
    private val completionCenter: CompletionCenter,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(): HealthSyncResult = mutex.withLock {
        val profile = hunterDao.getProfile() ?: return@withLock HealthSyncResult.Disabled
        if (!profile.healthEnabled) return@withLock HealthSyncResult.Disabled
        if (gateway.status() != HealthStatus.AVAILABLE || !gateway.grantedPermissions().containsAll(gateway.requiredPermissions)) {
            hunterDao.setHealthEnabled(false)
            return@withLock HealthSyncResult.Lost
        }
        val today = clock.gameDate(profile.dayStartHour)
        val open = questDao.getForDate(today).filter { it.isOpen && it.verification.source == VerificationSource.HEALTH }
        if (open.isEmpty()) return@withLock HealthSyncResult.Synced(emptyList())

        val window = HealthRules.window(today, clock.zone(), profile.dayStartHour, clock.now())
        val day = try {
            gateway.readDay(window)
        } catch (e: HealthReadException) {
            Timber.w(e, "Health Connect read failed")
            return@withLock HealthSyncResult.ReadFailed
        }
        val completed = open
            .filter { HealthRules.isMet(it.verification, it.target, day, window) }
            .mapNotNull { quest ->
                (completeQuest.completeVerified(quest.id, VerificationSource.HEALTH) as? CompleteQuestResult.Completed)?.outcome
            }
        completed.forEach(completionCenter::completed)
        HealthSyncResult.Synced(completed)
    }
}

sealed interface EnableHealthResult {
    data object Enabled : EnableHealthResult
    /** The hunter declined some of the three read permissions; verified quests stay off. */
    data object PermissionsMissing : EnableHealthResult
    data object Unavailable : EnableHealthResult
}

/** Turns verified quests on after the permission sheet (onboarding step 5 or Settings), then syncs once. */
class EnableHealthUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val gateway: HealthGateway,
    private val sync: HealthSyncUseCase,
) {
    suspend operator fun invoke(): EnableHealthResult {
        if (gateway.status() != HealthStatus.AVAILABLE) return EnableHealthResult.Unavailable
        if (!gateway.grantedPermissions().containsAll(gateway.requiredPermissions)) return EnableHealthResult.PermissionsMissing
        hunterDao.setHealthEnabled(true)
        sync()
        return EnableHealthResult.Enabled
    }
}

/** Turns verified quests off and gives the permissions back to Health Connect (best effort). */
class DisableHealthUseCase @Inject constructor(
    private val hunterDao: HunterDao,
    private val gateway: HealthGateway,
) {
    suspend operator fun invoke() {
        hunterDao.setHealthEnabled(false)
        gateway.revokeAll()
    }
}
