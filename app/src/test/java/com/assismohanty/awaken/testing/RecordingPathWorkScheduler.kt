package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.domain.PathWorkScheduler

/** Records what the domain asked the scheduler to do; tests drive the work by hand. */
class RecordingPathWorkScheduler : PathWorkScheduler {
    data class PlanCall(val pathId: String, val thenWeek: Boolean)
    data class WeekCall(val pathId: String, val isoWeek: String, val manual: Boolean)

    val planCalls = mutableListOf<PlanCall>()
    val weekCalls = mutableListOf<WeekCall>()
    var reportCalls = 0
        private set
    var cancelAllCalls = 0
        private set

    override fun enqueuePlan(pathId: String, thenWeek: Boolean) {
        planCalls += PlanCall(pathId, thenWeek)
    }

    override fun enqueueWeek(pathId: String, isoWeek: String, manual: Boolean) {
        weekCalls += WeekCall(pathId, isoWeek, manual)
    }

    override fun enqueueReports() {
        reportCalls++
    }

    override fun cancelAllPathWork() {
        cancelAllCalls++
    }
}
