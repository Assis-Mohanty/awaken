package com.assismohanty.awaken.engine

import java.time.Instant
import java.time.ZoneId

/** Rules behind the Settings screen (DS §4.7; ENGINE_DECISIONS #46–#47). */
object SettingsRules {
    /** Day-start choices: midnight to 06:00 (a "day" never starts mid-morning). */
    val DAY_START_HOURS: IntRange = 0..6

    /**
     * Whether the day start may change to [newHour] right now. It must be a listed hour, and it must not move the
     * current game-day backwards: finalized days are never re-opened (ARCH §3.2). Moving the start earlier is fine;
     * it just ends yesterday sooner.
     */
    fun canChangeDayStart(now: Instant, zone: ZoneId, currentHour: Int, newHour: Int): Boolean =
        newHour in DAY_START_HOURS &&
            GameDay.of(now, zone, newHour) >= GameDay.of(now, zone, currentHour)

    /** Injury tags are a subset of [DirectiveGenerator.INJURY_TAGS]; an empty selection falls back to the default pair. */
    fun normalizeInjuryTags(tags: Set<String>): Set<String> =
        tags.filter { it in DirectiveGenerator.INJURY_TAGS }.toSet().ifEmpty { DirectiveGenerator.DEFAULT_INJURY_TAGS }

    /** Equipment ids must come from the template vocabulary, so a typo can't silently filter every template. */
    fun validEquipment(owned: Set<String>, vocabulary: Collection<String>): Set<String> = owned.filter { it in vocabulary }.toSet()
}
