package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.domain.HabitReminderScheduling

/** Counts how often habit reminders were re-armed, so tests don't touch WorkManager. */
class RecordingHabitReminders : HabitReminderScheduling {
    var rearmed = 0
        private set

    override suspend fun rearm() {
        rearmed++
    }
}
