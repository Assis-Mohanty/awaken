package com.assismohanty.awaken.engine

import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.Stat
import java.time.LocalTime

/**
 * Everything achievements depend on, derived from ledger/state by the app after each write (GD §10).
 * @param dawnRaidDays distinct game-days with a completion where [AchievementEvaluator.isDawnRaid] holds.
 * @param midnightGates Focus Gates cleared where [AchievementEvaluator.isMidnightGate] holds.
 * @param comebacks penalty quests completed on the day they were issued (GD §7.3).
 */
data class AchievementSnapshot(
    val questsCompleted: Int = 0,
    val onboardingDone: Boolean = false,
    val level: Int = 1,
    val bestStreak: Int = 0,
    val crits: Int = 0,
    val gatesCleared: Int = 0,
    val gateMinutesCleared: Long = 0,
    val healthVerifiedCompleted: Int = 0,
    val comebacks: Int = 0,
    val statXp: Map<Stat, Long> = emptyMap(),
    val passedTrials: Set<Rank> = emptySet(),
    val dawnRaidDays: Int = 0,
    val midnightGates: Int = 0,
    val cachesOpened: Int = 0,
    val alreadyUnlocked: Set<String> = emptySet(),
) {
    fun statLevel(stat: Stat): Int = StatLevel.level(statXp[stat] ?: 0L)
}

/** [titleId] is a stable key; display text lives in the app's strings.xml. */
data class Achievement(
    val id: String,
    val gold: Int,
    val titleId: String? = null,
    val condition: (AchievementSnapshot) -> Boolean,
)

/** The launch achievement set (GD §10) as a pure function: snapshot → newly unlocked ids. */
object AchievementEvaluator {
    private val DAWN_RAID_END: LocalTime = LocalTime.of(7, 0)
    private val MIDNIGHT_GATE_START: LocalTime = LocalTime.of(23, 0)
    private const val MINUTES_PER_HOUR = 60L

    private val statMasteryTitles = mapOf(
        Stat.STR to "ironborn",
        Stat.INT to "sage",
        Stat.WIL to "stoic",
        Stat.VIT to "evergreen",
        Stat.CHA to "silver_tongue",
    )

    val all: List<Achievement> = buildList {
        add(Achievement("first_blood", 10) { it.questsCompleted >= 1 })
        add(Achievement("awakened", 10, "the_awakened") { it.onboardingDone })
        add(Achievement("lvl5", 25) { it.level >= 5 })
        add(Achievement("lvl10", 50, "rising_hunter") { it.level >= 10 })
        add(Achievement("lvl25", 100) { it.level >= 25 })
        add(Achievement("lvl50", 200, "veteran") { it.level >= 50 })
        add(Achievement("streak3", 20) { it.bestStreak >= 3 })
        add(Achievement("streak7", 50) { it.bestStreak >= 7 })
        add(Achievement("streak30", 150, "unbroken") { it.bestStreak >= 30 })
        add(Achievement("streak100", 200, "the_relentless") { it.bestStreak >= 100 })
        add(Achievement("crit1", 15) { it.crits >= 1 })
        add(Achievement("crit25", 100, "fortunes_favoured") { it.crits >= 25 })
        add(Achievement("gate_first", 20) { it.gatesCleared >= 1 })
        add(Achievement("gate_hours10", 100) { it.gateMinutesCleared >= 10 * MINUTES_PER_HOUR })
        add(Achievement("gate_hours100", 200, "gate_sovereign") { it.gateMinutesCleared >= 100 * MINUTES_PER_HOUR })
        add(Achievement("verified10", 30) { it.healthVerifiedCompleted >= 10 })
        add(Achievement("verified100", 150, "proven") { it.healthVerifiedCompleted >= 100 })
        add(Achievement("redeemed", 50, "the_redeemed") { it.comebacks >= 3 })
        add(Achievement("balanced", 100, "balanced") { s -> Stat.entries.all { s.statLevel(it) >= 5 } })
        for (stat in Stat.entries) {
            add(Achievement("${stat.name.lowercase()}20", 100, statMasteryTitles.getValue(stat)) { it.statLevel(stat) >= 20 })
        }
        // Promotion gold is the Rank Trial's 500 (GD §5), so these award none of their own.
        for (rank in Rank.entries.drop(1)) {
            val id = rank.name.lowercase()
            add(Achievement("rank_$id", 0, "${id}_rank_hunter") { rank in it.passedTrials })
        }
        add(Achievement("early_bird", 50) { it.dawnRaidDays >= 5 })
        add(Achievement("night_owl", 20) { it.midnightGates >= 1 })
        add(Achievement("collector", 50) { it.cachesOpened >= 5 })
    }

    val byId: Map<String, Achievement> = all.associateBy { it.id }

    /** Ids newly unlocked by [snapshot], in definition order. */
    fun evaluate(snapshot: AchievementSnapshot): List<String> =
        all.filter { it.id !in snapshot.alreadyUnlocked && it.condition(snapshot) }.map { it.id }

    /** "Before 07:00" within the game-day: from `dayStartHour:00` up to 06:59 local. */
    fun isDawnRaid(localTime: LocalTime, dayStartHour: Int = GameDay.DEFAULT_DAY_START_HOUR): Boolean =
        localTime.hour >= dayStartHour && localTime < DAWN_RAID_END

    /** "After 23:00": from 23:00 until the game-day rolls over at `dayStartHour`. */
    fun isMidnightGate(localTime: LocalTime, dayStartHour: Int = GameDay.DEFAULT_DAY_START_HOUR): Boolean =
        localTime >= MIDNIGHT_GATE_START || localTime.hour < dayStartHour
}
