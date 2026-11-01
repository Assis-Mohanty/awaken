package com.assismohanty.awaken.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object PathsRoute

/** One Path's full window: ladder, week, edit / pause / archive (DESIGN_SYSTEM §4.8). */
@Serializable
data class PathDetailRoute(val pathId: String)

@Serializable
data object GatesRoute

@Serializable
data object StatusRoute

@Serializable
data object VaultRoute

/** Quest detail (DESIGN_SYSTEM §4.3), opened from any quest row. */
@Serializable
data class QuestDetailRoute(val questInstanceId: String)

/** The running Rank Trial (GD §5), opened from Today. */
@Serializable
data object TrialRoute

/** Debug tools (registered only when DebugEntry.ENABLED, i.e. debug builds). */
@Serializable
data object DebugRoute

/** Settings (DESIGN_SYSTEM §4.7), opened from the Status header. */
@Serializable
data object SettingsRoute

/** Health Connect overview (steps, workouts, sleep, trends), opened from Settings. */
@Serializable
data object HealthRoute

/** Screen time + Screen Discipline (GD §6.7), opened from Settings and the Today card. */
@Serializable
data object ScreenTimeRoute

/** The 45-day Campaign (GD §6.8), opened from the Today card. */
/** The weekly review (GD §14.4). */
@Serializable
data object ReviewRoute

/** A monthly or yearly recap by its period key, "M:2026-10" or "Y:2026" (GD §14.6). */
@Serializable
data class RecapRoute(val periodKey: String)

@Serializable
data object CampaignRoute

/** Gym: workout tracker, exercise library and routines (bottom tab). */
@Serializable
data object GymRoute

/** Diet: food diary, calorie budget and nutrient targets (bottom tab). */
@Serializable
data object DietRoute

/** Weekly focus report (GD §8.1 P8), opened from the Focus card on Today. */
@Serializable
data object FocusReportRoute

/** Focus Lock setup: what it does, the Android switch, battery steps (GD 8.2). */
@Serializable
data object FocusLockSetupRoute

/** Apps Focus Lock leaves uncovered during a session. */
@Serializable
data object AllowedAppsRoute
