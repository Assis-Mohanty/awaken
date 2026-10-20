package com.assismohanty.awaken.core.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/** The last raw reply per call kind, in memory only, for the Debug screen (ARCH §7). Never persisted, never sent. */
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

    fun recordPlan(json: String) {
        lastPlanJson = json
    }

    fun recordWeek(json: String, drops: List<String>) {
        lastWeekJson = json
        lastWeekDrops = drops
    }
}
