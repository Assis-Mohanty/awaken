package com.assismohanty.awaken.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object GatesRoute

@Serializable
data object StatusRoute

@Serializable
data object VaultRoute

/** Quest detail (DESIGN_SYSTEM §4.3), opened from any quest row. */
@Serializable
data class QuestDetailRoute(val questInstanceId: String)

/** Debug tools (registered only when DebugEntry.ENABLED, i.e. debug builds). */
@Serializable
data object DebugRoute
