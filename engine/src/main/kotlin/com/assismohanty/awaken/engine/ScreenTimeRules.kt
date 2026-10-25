package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.QuestDraft
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import java.time.LocalDate

/** How the hunter treats an app (GD §6.7). Only DISTRACTING apps can carry a daily limit. */
enum class AppCategory { DISTRACTING, PRODUCTIVE, NEUTRAL }

/** The platform's own app category, mapped by `:app` from Android's `ApplicationInfo.category`. */
enum class AppKind { SOCIAL, VIDEO, GAME, NEWS, PRODUCTIVITY, OTHER }

/**
 * One rule version for one app. A change never edits a rule: it adds a new version effective from the next game-day
 * ([ScreenTimeRules.effectiveFromForEdit]), so a limit can't be raised at 59/60 minutes to dodge a forfeit.
 * [dailyLimitMinutes] null = tracked, no limit.
 */
data class AppRule(
    val packageName: String,
    val category: AppCategory,
    val dailyLimitMinutes: Int?,
    val effectiveFrom: LocalDate,
)

/** Usage against one limit for the current game-day. */
data class AppLimitState(val packageName: String, val usedMinutes: Int, val limitMinutes: Int) {
    val remainingMinutes: Int get() = (limitMinutes - usedMinutes).coerceAtLeast(0)
    /** Crossed means strictly over: using exactly the limit still holds (GD §6.7). */
    val breached: Boolean get() = usedMinutes > limitMinutes
}

data class DisciplineState(val limits: List<AppLimitState>) {
    val breached: Boolean get() = limits.any { it.breached }
    /** True when there is something to defend today: at least one distracting app with a limit. */
    val hasStake: Boolean get() = limits.isNotEmpty()
}

/**
 * Screen Discipline (GD §6.7). The device measures foreground time per app; the engine alone decides categories,
 * limits, breaches and the daily quest. Usage never leaves the phone and never mints anything by itself: the only reward
 * is the DISCIPLINE quest, paid through the normal completion pipeline at rollover. Crossing a limit forfeits that
 * quest; earned XP is never taken (GD §1 pillar 3).
 */
object ScreenTimeRules {
    const val DEFAULT_LIMIT_MINUTES = 30
    const val MIN_LIMIT_MINUTES = 5
    const val MAX_LIMIT_MINUTES = 240
    const val LIMIT_STEP_MINUTES = 5
    /** A warning goes out once per app per day when this many minutes or fewer remain. */
    const val WARNING_MINUTES = 5
    const val DISCIPLINE_SLOT = "discipline"
    val DISCIPLINE_STAT = Stat.WIL
    val DISCIPLINE_DIFFICULTY = Difficulty.NORMAL

    /** Bundled defaults (GD §6.7); matched before the platform category. The hunter can change any of them. */
    val DEFAULT_DISTRACTING_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar",
        "com.instagram.barcelona",
        "com.pinterest",
        "com.discord",
    )

    val DEFAULT_PRODUCTIVE_PACKAGES: Set<String> = setOf(
        "com.google.android.calendar",
        "com.google.android.keep",
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.microsoft.office.outlook",
        "com.microsoft.teams",
        "notion.id",
        "com.todoist",
        "com.duolingo",
    )

    fun defaultCategory(packageName: String, kind: AppKind): AppCategory = when {
        packageName in DEFAULT_DISTRACTING_PACKAGES -> AppCategory.DISTRACTING
        packageName in DEFAULT_PRODUCTIVE_PACKAGES -> AppCategory.PRODUCTIVE
        kind == AppKind.SOCIAL || kind == AppKind.VIDEO || kind == AppKind.GAME -> AppCategory.DISTRACTING
        kind == AppKind.PRODUCTIVITY -> AppCategory.PRODUCTIVE
        else -> AppCategory.NEUTRAL
    }

    /** The limit a newly categorised app starts with: distracting apps get the default, others none. */
    fun defaultLimit(category: AppCategory): Int? = if (category == AppCategory.DISTRACTING) DEFAULT_LIMIT_MINUTES else null

    /** Every edit (category or limit) applies from the next game-day. */
    fun effectiveFromForEdit(today: LocalDate): LocalDate = today.plusDays(1)

    /** Allowed limits: 5–240 min in 5-min steps. */
    fun isValidLimit(minutes: Int): Boolean =
        minutes in MIN_LIMIT_MINUTES..MAX_LIMIT_MINUTES && minutes % LIMIT_STEP_MINUTES == 0

    /** The rule in force per package on [gameDate]: the latest version with `effectiveFrom ≤ gameDate`. */
    fun rulesFor(gameDate: LocalDate, rules: List<AppRule>): Map<String, AppRule> =
        rules.asSequence()
            .filter { !it.effectiveFrom.isAfter(gameDate) }
            .groupBy { it.packageName }
            .mapValues { (_, versions) -> versions.maxBy { it.effectiveFrom } }

    /**
     * Today's limits against [usageMillis] (foreground time per package over the game-day). Only DISTRACTING apps with
     * a limit count; usage is floored to whole minutes.
     */
    fun evaluate(usageMillis: Map<String, Long>, rulesInForce: Map<String, AppRule>): DisciplineState =
        DisciplineState(
            rulesInForce.values
                .filter { it.category == AppCategory.DISTRACTING && it.dailyLimitMinutes != null }
                .sortedBy { it.packageName }
                .map { rule ->
                    AppLimitState(
                        packageName = rule.packageName,
                        usedMinutes = ((usageMillis[rule.packageName] ?: 0L) / MILLIS_PER_MINUTE).toInt(),
                        limitMinutes = requireNotNull(rule.dailyLimitMinutes),
                    )
                },
        )

    /** Limits that should warn now: not yet crossed, [WARNING_MINUTES] or fewer left. The caller sends each once a day. */
    fun warningsDue(state: DisciplineState): List<AppLimitState> =
        state.limits.filter { !it.breached && it.remainingMinutes in 0..WARNING_MINUTES }

    /**
     * Today's DISCIPLINE quest (WIL, Normal, DEVICE_USAGE). The app supplies the localised [title] and [description];
     * the id is deterministic per `(hunterId, gameDate)` like every generated quest (GD §6.3).
     */
    fun disciplineQuest(hunterId: String, gameDate: LocalDate, title: String, description: String): QuestDraft = QuestDraft(
        questInstanceId = DirectiveGenerator.instanceId(hunterId, gameDate, DISCIPLINE_SLOT),
        gameDate = gameDate,
        type = QuestType.DISCIPLINE,
        templateId = null,
        stat = DISCIPLINE_STAT,
        secondaryStat = null,
        title = title,
        description = description,
        unit = DISCIPLINE_UNIT,
        target = 1,
        difficulty = DISCIPLINE_DIFFICULTY,
        verification = Verification.DEVICE_USAGE,
        minDurationSec = 0,
    )

    const val DISCIPLINE_UNIT = "day"
    private const val MILLIS_PER_MINUTE = 60_000L
}
