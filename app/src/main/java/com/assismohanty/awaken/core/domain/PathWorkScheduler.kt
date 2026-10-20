package com.assismohanty.awaken.core.domain

/**
 * Hands Path work to a background scheduler (WorkManager in production). The domain only says *what* must happen; the
 * request ids that make retries idempotent are created by the implementation when it enqueues (ARCH §3.7).
 */
interface PathWorkScheduler {
    /** Ask for the Path's milestone ladder; with [thenWeek], follow with the current week's quests (a new Path). */
    fun enqueuePlan(pathId: String, thenWeek: Boolean)

    /** This week's quests for [pathId]; [manual] marks a user-triggered retry (GD §6.6.7). Keeps queued work for the same week. */
    fun enqueueWeek(pathId: String, isoWeek: String, manual: Boolean)

    /** Sends any unsent reports. */
    fun enqueueReports()

    /** Consent revoked: nothing more may be requested. */
    fun cancelAllPathWork()
}
