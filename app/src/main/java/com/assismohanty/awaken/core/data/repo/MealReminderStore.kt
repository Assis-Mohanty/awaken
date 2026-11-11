package com.assismohanty.awaken.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.assismohanty.awaken.core.data.di.MealReminderPrefs
import com.assismohanty.awaken.engine.body.MealReminderRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The reminder time of each meal that has one, in minutes after local midnight, keyed by meal id. Off by default. */
@Singleton
class MealReminderStore @Inject constructor(@MealReminderPrefs private val store: DataStore<Preferences>) {

    val reminders: Flow<Map<String, Int>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val minutes = value as? Int
            if (minutes != null && key.name.startsWith(PREFIX)) key.name.removePrefix(PREFIX) to minutes else null
        }.toMap()
    }

    /** Sets or, with a null time, clears [mealId]'s reminder. False when the time is invalid or four meals already have one. */
    suspend fun set(mealId: String, minutes: Int?): Boolean {
        if (minutes != null && !MealReminderRules.validMinutes(minutes)) return false
        if (minutes != null && !MealReminderRules.canSet(reminders.first().keys, mealId)) return false
        store.edit { prefs ->
            val key = intPreferencesKey(PREFIX + mealId)
            if (minutes == null) prefs.remove(key) else prefs[key] = minutes
        }
        return true
    }

    private companion object {
        const val PREFIX = "meal_"
    }
}
