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

/** The weekly review (GD §14.4). */
@Serializable
data object ReviewRoute

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

/** Reel counter setup: the disclosure, the Android switch, battery steps. */
@Serializable
data object ReelCounterSetupRoute

/** Daily reel limits per app. */
@Serializable
data object ReelLimitsRoute

/** Reel counter totals, per-app breakdown, 7 and 30 day charts. */
@Serializable
data object ReelStatsRoute

/** What Android must allow for Focus Lock and Reel counter: accessibility switches, battery, autostart. */
@Serializable
data object FocusAccessRoute

/** The Reel counter detector inspector (debug builds only). */
@Serializable
data object ReelInspectorRoute

/** Optional two-minute breathing before a phone-down night (GD §16.12), opened from the evening card on Today. */
@Serializable
data object WindDownRoute

/** Quit counters: days clean of something the hunter wants to stop (GD §16.13), opened from Status. */
@Serializable
data object QuitRoute

/** Programs: 30 / 66 / 75 days of a few daily rules (GD §16.15), opened from Status. */
@Serializable
data object ProgramsRoute
