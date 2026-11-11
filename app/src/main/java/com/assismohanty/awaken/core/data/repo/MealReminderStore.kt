package com.assismohanty.awaken.core.data.repo

import com.assismohanty.awaken.core.data.db.dao.FoodLibraryDao
import com.assismohanty.awaken.core.data.db.entity.MealReminderEntity
import com.assismohanty.awaken.engine.body.MealReminderRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The reminder time of each meal that has one, in minutes after local midnight, keyed by meal id. Off by default. Part of the backup file. */
@Singleton
class MealReminderStore @Inject constructor(private val dao: FoodLibraryDao) {

    val reminders: Flow<Map<String, Int>> = dao.observeMealReminders().map { rows -> rows.associate { it.mealId to it.minutes } }

    /** Sets or, with a null time, clears [mealId]'s reminder. False when the time is invalid or four meals already have one. */
    suspend fun set(mealId: String, minutes: Int?): Boolean {
        if (minutes == null) {
            dao.deleteMealReminder(mealId)
            return true
        }
        if (!MealReminderRules.validMinutes(minutes)) return false
        if (!MealReminderRules.canSet(dao.mealReminders().map { it.mealId }.toSet(), mealId)) return false
        dao.upsertMealReminder(MealReminderEntity(mealId, minutes))
        return true
    }
}
