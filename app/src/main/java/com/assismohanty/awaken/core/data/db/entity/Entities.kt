package com.assismohanty.awaken.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.assismohanty.awaken.engine.DayOutcome
import com.assismohanty.awaken.engine.GateCollapseReason
import com.assismohanty.awaken.engine.GateTiming
import com.assismohanty.awaken.engine.RankRules
import com.assismohanty.awaken.engine.model.Difficulty
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.LedgerKind
import com.assismohanty.awaken.engine.model.QuestStatus
import com.assismohanty.awaken.engine.model.QuestType
import com.assismohanty.awaken.engine.model.Rank
import com.assismohanty.awaken.engine.model.SourceType
import com.assismohanty.awaken.engine.model.Stat
import com.assismohanty.awaken.engine.model.Verification
import com.assismohanty.awaken.engine.path.Feedback
import com.assismohanty.awaken.engine.path.PathMode
import com.assismohanty.awaken.engine.path.PathStatus
import com.assismohanty.awaken.engine.path.PathUnit
import com.assismohanty.awaken.engine.path.ProgressionSignal
import com.assismohanty.awaken.engine.path.SlotOutcome
import com.assismohanty.awaken.engine.model.VerificationSource
import java.time.Instant
import java.time.LocalDate

// Schema: ARCHITECTURE.md §2. Column names are snake_case; Kotlin names follow the one-name-per-concept rule.

@Entity(tableName = "hunter_profile")
data class HunterProfileEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "hunter_id") val hunterId: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "first_game_date") val firstGameDate: LocalDate,
    @ColumnInfo(name = "focus_stat_1") val focusStat1: Stat,
    @ColumnInfo(name = "focus_stat_2") val focusStat2: Stat,
    @ColumnInfo(name = "day_start_hour") val dayStartHour: Int,
    @ColumnInfo(name = "reminder_time") val reminderTime: String? = null,
    @ColumnInfo(name = "equipped_title_id") val equippedTitleId: String? = null,
    @ColumnInfo(name = "theme_id") val themeId: String = DEFAULT_THEME_ID,
    @ColumnInfo(name = "equipped_frame_id") val equippedFrameId: String? = null,
    @ColumnInfo(name = "equipped_sigil_id") val equippedSigilId: String? = null,
    @ColumnInfo(name = "onboarding_done") val onboardingDone: Boolean,
    @ColumnInfo(name = "owned_equipment") val ownedEquipment: Set<String> = emptySet(),
    @ColumnInfo(name = "injury_mode") val injuryMode: Boolean = false,
    /** Template tags avoided while [injuryMode] is on (DS §4.7); defaults to impact + legs (ENGINE_DECISIONS #46). */
    @ColumnInfo(name = "injury_tags", defaultValue = "impact,legs") val injuryTags: Set<String> = setOf("impact", "legs"),
    @ColumnInfo(name = "health_enabled") val healthEnabled: Boolean = false,
    @ColumnInfo(name = "last_recalibrated_on") val lastRecalibratedOn: LocalDate? = null,
) {
    val focusStats: List<Stat> get() = listOf(focusStat1, focusStat2)

    companion object {
        const val SINGLETON_ID = "me"
        const val DEFAULT_THEME_ID = "default"
    }
}

@Entity(tableName = "stat_tier")
data class StatTierEntity(
    @PrimaryKey val stat: Stat,
    val tier: Difficulty,
)

@Entity(
    tableName = "quest_instance",
    indices = [Index("game_date"), Index("status"), Index("path_slot_id")],
)
data class QuestInstanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val type: QuestType,
    @ColumnInfo(name = "template_id") val templateId: String?,
    @ColumnInfo(name = "habit_id") val habitId: String?,
    val stat: Stat,
    @ColumnInfo(name = "secondary_stat") val secondaryStat: Stat?,
    val title: String,
    val description: String,
    val unit: String,
    val target: Int,
    val difficulty: Difficulty,
    val verification: Verification,
    @ColumnInfo(name = "min_duration_sec") val minDurationSec: Int,
    val status: QuestStatus,
    @ColumnInfo(name = "started_at") val startedAt: Instant? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** The Path slot this quest was scheduled from (ARCH §2); null for template, habit, penalty, trial and gate quests. */
    @ColumnInfo(name = "path_slot_id") val pathSlotId: String? = null,
)

/** One completion attempt; [id] is the completionId `questInstanceId#attemptN` (GD §9.3). Not a ledger table. */
@Entity(
    tableName = "completion",
    indices = [Index(value = ["quest_instance_id", "attempt"], unique = true), Index("game_date")],
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "quest_instance_id") val questInstanceId: String,
    val attempt: Int,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    @ColumnInfo(name = "quest_type") val questType: QuestType,
    val stat: Stat,
    @ColumnInfo(name = "verification_source") val verificationSource: VerificationSource,
    val crit: Boolean,
    val xp: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
    @ColumnInfo(name = "undone_at") val undoneAt: Instant? = null,
)

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val stat: Stat,
    val difficulty: Difficulty,
    val verification: Verification,
    /** Bit (dayOfWeek.value − 1) set = scheduled that day; Monday = bit 0. */
    @ColumnInfo(name = "days_of_week_mask") val daysOfWeekMask: Int,
    val target: Int,
    val unit: String,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/** THE LEDGER (append-only, GD §9.3). Never update or delete rows; `stat`/`item_id` use '' instead of NULL. */
@Entity(
    tableName = "ledger_entry",
    indices = [
        Index(value = ["source_type", "source_id", "kind", "stat", "item_id"], unique = true),
        Index(value = ["game_date", "xp_source"]),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LedgerKind,
    val stat: String = "",
    @ColumnInfo(name = "item_id") val itemId: String = "",
    val delta: Long,
    @ColumnInfo(name = "source_type") val sourceType: SourceType,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    /** SELF/TIMER/HEALTH on quest XP_TOTAL rows (daily caps), '' otherwise. */
    @ColumnInfo(name = "xp_source") val xpSource: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "breakdown_json") val breakdownJson: String? = null,
)

@Entity(tableName = "balance_cache")
data class BalanceCacheEntity(
    @PrimaryKey val key: String,
    val value: Long,
)

@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey @ColumnInfo(name = "game_date") val gameDate: LocalDate,
    val cleared: Boolean = false,
    @ColumnInfo(name = "rest_day") val restDay: Boolean = false,
    val recovery: Boolean = false,
    /** Set once finalized; the streak is replayed from these (ENGINE_DECISIONS #23). */
    val outcome: DayOutcome? = null,
    @ColumnInfo(name = "shield_used") val shieldUsed: Boolean = false,
    @ColumnInfo(name = "penalty_issued") val penaltyIssued: Boolean = false,
    @ColumnInfo(name = "finalized_at") val finalizedAt: Instant? = null,
)

enum class GateRunStatus { RUNNING, CLEARED, COLLAPSED, ABANDONED }

/**
 * One Focus Gate run (ARCH §3.3). Nothing ticks it: progress derives from these timestamps via `GateRunMath`. The single
 * pause is [pausedAt]..[resumedAt] (auto-resumes after 5 min). [lastStopAt]/[lastStopElapsedMs] mark the app going to the
 * background; they are cleared once that stretch is accounted on return. [bootCount] detects a reboot in between.
 */
@Entity(tableName = "gate_run", indices = [Index("status"), Index("quest_instance_id")])
data class GateRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "quest_instance_id") val questInstanceId: String,
    @ColumnInfo(name = "gate_rank") val gateRank: GateRank,
    val stat: Stat,
    val label: String?,
    @ColumnInfo(name = "planned_sec") val plannedSec: Int,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant? = null,
    @ColumnInfo(name = "background_ms") val backgroundMs: Long = 0,
    @ColumnInfo(name = "paused_at") val pausedAt: Instant? = null,
    @ColumnInfo(name = "resumed_at") val resumedAt: Instant? = null,
    @ColumnInfo(name = "last_stop_at") val lastStopAt: Instant? = null,
    @ColumnInfo(name = "last_stop_elapsed_ms") val lastStopElapsedMs: Long? = null,
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    val status: GateRunStatus,
    @ColumnInfo(name = "collapse_reason") val collapseReason: GateCollapseReason? = null,
) {
    val timing: GateTiming get() = GateTiming(startedAt, plannedSec, pausedAt, resumedAt, backgroundMs)
}

@Entity(tableName = "trial")
data class TrialEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "target_rank") val targetRank: Rank,
    @ColumnInfo(name = "started_game_date") val startedGameDate: LocalDate,
    @ColumnInfo(name = "expires_game_date") val expiresGameDate: LocalDate,
    val status: RankRules.TrialStatus,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
)

@Entity(tableName = "achievement_unlock")
data class AchievementUnlockEntity(
    @PrimaryKey @ColumnInfo(name = "achievement_id") val achievementId: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Instant,
)

@Entity(tableName = "owned_cosmetic")
data class OwnedCosmeticEntity(
    @PrimaryKey @ColumnInfo(name = "cosmetic_id") val cosmeticId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Instant,
    val source: String,
)

@Entity(tableName = "health_sync_state")
data class HealthSyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "last_change_token") val lastChangeToken: String?,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Instant?,
)

// ---- AI Paths (GAME_DESIGN §6.6, ARCH §2). None of these tables moves a balance: rewards flow only through
// quest_instance → CompleteQuest → ledger.

@Entity(tableName = "path")
data class PathEntity(
    @PrimaryKey val id: String,
    val goal: String,
    @ColumnInfo(name = "current_level") val currentLevel: String,
    val notes: String,
    @ColumnInfo(name = "primary_stat") val primaryStat: Stat,
    @ColumnInfo(name = "secondary_stat") val secondaryStat: Stat?,
    /** Bit (dayOfWeek.value − 1) set = active that day; Monday = bit 0 (same as habits). */
    @ColumnInfo(name = "active_days_mask") val activeDaysMask: Int,
    @ColumnInfo(name = "quests_per_day") val questsPerDay: Int,
    @ColumnInfo(name = "weekly_minutes") val weeklyMinutes: Int,
    val status: PathStatus,
    @ColumnInfo(name = "current_milestone_index") val currentMilestoneIndex: Int = 0,
    @ColumnInfo(name = "milestone_started_game_date") val milestoneStartedGameDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "created_game_date") val createdGameDate: LocalDate,
    /** ISO week of the last user "Re-plan" (max one per week per Path, GD §6.6.2); null = never. */
    @ColumnInfo(name = "last_replan_iso_week") val lastReplanIsoWeek: String? = null,
)

/** One rung of a plan. A re-plan writes a new [planVersion]; the highest version is the current ladder. */
@Entity(tableName = "path_milestone", primaryKeys = ["path_id", "plan_version", "idx"])
data class PathMilestoneEntity(
    @ColumnInfo(name = "path_id") val pathId: String,
    val idx: Int,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "exit_criterion") val exitCriterion: String,
    @ColumnInfo(name = "plan_version") val planVersion: Int,
)

enum class PathWeekStatus { REQUESTED, READY, EMPTY, FAILED }

@Entity(tableName = "path_week", primaryKeys = ["path_id", "iso_week"])
data class PathWeekEntity(
    @ColumnInfo(name = "path_id") val pathId: String,
    /** ISO week key, e.g. `2026-W43` ([com.assismohanty.awaken.engine.path.PathScheduler.isoWeek]). */
    @ColumnInfo(name = "iso_week") val isoWeek: String,
    val status: PathWeekStatus,
    @ColumnInfo(name = "progression_signal") val progressionSignal: ProgressionSignal,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    @ColumnInfo(name = "response_sha256") val responseSha256: String,
    @ColumnInfo(name = "requested_at") val requestedAt: Instant,
    @ColumnInfo(name = "received_at") val receivedAt: Instant? = null,
    @ColumnInfo(name = "manual_retries") val manualRetries: Int = 0,
    @ColumnInfo(name = "suggest_advance") val suggestAdvance: Boolean = false,
)

/** A validated, priced blueprint placed in a week. Priced fields are copied to quests, never recomputed. */
@Entity(
    tableName = "path_slot",
    indices = [Index(value = ["path_id", "iso_week", "blueprint_index"], unique = true), Index("assigned_game_date")],
)
data class PathSlotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "path_id") val pathId: String,
    @ColumnInfo(name = "iso_week") val isoWeek: String,
    @ColumnInfo(name = "blueprint_index") val blueprintIndex: Int,
    /** Null = unscheduled: reroll stock for the week (ENGINE_DECISIONS #65). */
    @ColumnInfo(name = "assigned_game_date") val assignedGameDate: LocalDate?,
    @ColumnInfo(name = "carried_over") val carriedOver: Boolean,
    val title: String,
    val description: String,
    val stat: Stat,
    val unit: PathUnit,
    val target: Int,
    @ColumnInfo(name = "est_minutes") val estMinutes: Int,
    val mode: PathMode,
    @ColumnInfo(name = "priced_difficulty") val pricedDifficulty: Difficulty,
    @ColumnInfo(name = "priced_verification") val pricedVerification: Verification,
    @ColumnInfo(name = "gate_rank") val gateRank: GateRank?,
    @ColumnInfo(name = "min_duration_sec") val minDurationSec: Int,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    val rationale: String,
    val outcome: SlotOutcome,
    val feedback: Feedback? = null,
)

/** Outbox for `/v1/reports` (PRD F21). Holds the text reported, never an install ID. */
@Entity(tableName = "ai_report")
data class AiReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "path_slot_id") val pathSlotId: String,
    val reason: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "sent_at") val sentAt: Instant? = null,
)
