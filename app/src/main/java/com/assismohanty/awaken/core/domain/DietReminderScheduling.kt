package com.assismohanty.awaken.core.domain

/** Arms the quiet Diet notifications: a reminder per chosen meal, and one when a fast reaches its target (GD 20.4). */
interface DietReminderScheduling {
    /** Re-reads the chosen meal times and arms each; a meal that lost its reminder stops at once. */
    suspend fun rearmMeals()

    /** Arms the end of the running fast, or cancels it when none runs. */
    suspend fun rearmFast()
}
