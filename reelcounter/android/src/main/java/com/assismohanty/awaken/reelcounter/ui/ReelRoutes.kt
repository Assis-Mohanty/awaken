package com.assismohanty.awaken.reelcounter.ui

import kotlinx.serialization.Serializable

@Serializable data object SetupRoute

@Serializable data object StatsRoute

@Serializable data object StatusRoute

@Serializable data object LimitsRoute

@Serializable data object SettingsRoute

/** Only registered in builds that have an inspector. */
@Serializable data object InspectorRoute

/** Phase 2 only: a plain-text counts screen. Removed in Phase 5 when Stats becomes the start screen. */
@Serializable data object TodayDebugRoute
