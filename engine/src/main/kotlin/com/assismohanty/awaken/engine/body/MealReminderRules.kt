package com.assismohanty.awaken.engine.body

/** Meal reminder limits (GD section 20.4): up to four, one per meal, off by default. Times are minutes after local midnight. Pure. */
object MealReminderRules {
    const val MAX_REMINDERS = 4
    private const val MINUTES_PER_DAY = 24 * 60

    fun validMinutes(minutes: Int): Boolean = minutes in 0 until MINUTES_PER_DAY

    /** Where a reminder starts when it is first switched on; the hunter can change it. */
    fun defaultMinutes(mealId: String): Int = when (mealId) {
        "BREAKFAST" -> 8 * 60
        "LUNCH" -> 13 * 60
        "SNACK" -> 16 * 60 + 30
        "DINNER" -> 20 * 60
        else -> 12 * 60
    }

    /** True when [mealId] may get a reminder: it already has one (changing its time), or fewer than [MAX_REMINDERS] exist. */
    fun canSet(existing: Set<String>, mealId: String): Boolean = mealId in existing || existing.size < MAX_REMINDERS
}
