package com.assismohanty.awaken.core.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the Oracle last said, in memory only (never persisted, never sent): the raw replies and drop reasons for the Debug
 * screen (ARCH §7), and which Paths are currently waiting for a connection so the Paths tab can say "Offline".
 */
@Singleton
class OracleTrace @Inject constructor() {
    @Volatile
    var lastPlanJson: String? = null
        private set

    @Volatile
    var lastWeekJson: String? = null
        private set

    @Volatile
    var lastWeekDrops: List<String> = emptyList()
        private set

    private val offline = MutableStateFlow<Set<String>>(emptySet())

    /** Path ids whose last attempt failed for lack of a connection and will be retried. */
    val offlinePaths: StateFlow<Set<String>> = offline.asStateFlow()

    fun recordPlan(json: String) {
        lastPlanJson = json
    }

    fun recordWeek(json: String, drops: List<String>) {
        lastWeekJson = json
        lastWeekDrops = drops
    }

    fun markOffline(pathId: String) = offline.update { it + pathId }

    fun clearOffline(pathId: String) = offline.update { it - pathId }
}
