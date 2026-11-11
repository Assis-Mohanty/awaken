package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MealReminderRulesTest {
    @Test
    fun aFifthMealCannotGetAReminderButAnExistingOneCanChange() {
        val four = setOf("BREAKFAST", "LUNCH", "SNACK", "DINNER")
        assertThat(MealReminderRules.canSet(four, "custom:1")).isFalse()
        assertThat(MealReminderRules.canSet(four, "LUNCH")).isTrue()
        assertThat(MealReminderRules.canSet(setOf("LUNCH"), "custom:1")).isTrue()
    }

    @Test
    fun timesStayInsideTheDay() {
        assertThat(MealReminderRules.validMinutes(0)).isTrue()
        assertThat(MealReminderRules.validMinutes(1_439)).isTrue()
        assertThat(MealReminderRules.validMinutes(1_440)).isFalse()
        assertThat(MealReminderRules.validMinutes(-1)).isFalse()
        assertThat(MealReminderRules.defaultMinutes("DINNER")).isEqualTo(1_200)
    }
}
