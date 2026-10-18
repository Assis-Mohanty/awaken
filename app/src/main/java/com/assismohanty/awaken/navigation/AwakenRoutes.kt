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

/** Debug tools (registered only when DebugEntry.ENABLED, i.e. debug builds). */
@Serializable
data object DebugRoute
