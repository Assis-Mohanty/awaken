package com.assismohanty.awaken.engine

import java.time.Duration
import java.time.Instant

/** The device events the Screen time screen reads (GD §16.10). */
enum class ScreenEventType { KEYGUARD_SHOWN, KEYGUARD_HIDDEN, SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE, FOREGROUND }

data class ScreenEvent(val type: ScreenEventType, val at: Instant)

/** What a count is called: unlocks, or screen-ons on a phone with no lock screen. */
enum class PickupKind { PICKUPS, SCREEN_ONS }

/** Today's phone habit. Every field except [count] can be missing; none of it is ever a reward. */
data class PickupSummary(
    val kind: PickupKind,
    val count: Int,
    val firstPickup: Instant?,
    /** The last app in the foreground before the previous game-day ended. */
    val lastUseYesterday: Instant?,
    /** The longest gap between a screen-off and the next pickup, from the first pickup until now. */
    val longestStretch: Duration?,
)

/** Pickups, first and last use, longest stretch (GD §16.10). Pure over events the device observed. */
object PickupRules {
    /**
     * [today] are the events from the start of the game-day to now; [yesterday] are the previous game-day's events.
     * Pickups are `KEYGUARD_HIDDEN` events; a window with no keyguard events at all counts `SCREEN_INTERACTIVE` instead
     * and calls them screen-ons.
     */
    fun summarize(today: List<ScreenEvent>, yesterday: List<ScreenEvent>): PickupSummary {
        val hasKeyguard = today.any { it.type == ScreenEventType.KEYGUARD_HIDDEN || it.type == ScreenEventType.KEYGUARD_SHOWN }
        val kind = if (hasKeyguard) PickupKind.PICKUPS else PickupKind.SCREEN_ONS
        val pickupType = if (hasKeyguard) ScreenEventType.KEYGUARD_HIDDEN else ScreenEventType.SCREEN_INTERACTIVE
        val pickups = today.filter { it.type == pickupType }.map { it.at }.sorted()
        val first = pickups.firstOrNull()
        return PickupSummary(
            kind = kind,
            count = pickups.size,
            firstPickup = first,
            lastUseYesterday = yesterday.filter { it.type == ScreenEventType.FOREGROUND }.maxOfOrNull { it.at },
            longestStretch = first?.let { longestStretch(today, pickups, it) },
        )
    }

    /** Closed gaps only: a screen-off at or after the first pickup, up to the next pickup. The night is never counted. */
    private fun longestStretch(today: List<ScreenEvent>, pickups: List<Instant>, first: Instant): Duration? =
        today.filter { it.type == ScreenEventType.SCREEN_NON_INTERACTIVE && !it.at.isBefore(first) }
            .mapNotNull { off -> pickups.firstOrNull { it.isAfter(off.at) }?.let { Duration.between(off.at, it) } }
            .maxOrNull()
}
