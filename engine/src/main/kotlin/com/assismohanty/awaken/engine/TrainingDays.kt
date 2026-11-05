package com.assismohanty.awaken.engine

import java.time.LocalDate

/** The days a person trains (GD §17). Stored as a Monday-first bit mask: ISO day d is bit d - 1, so Monday is 1 and Sunday 64. */
object TrainingDays {
    const val NONE = 0

    fun mask(isoDays: Set<Int>): Int = isoDays.filter { it in 1..7 }.fold(NONE) { acc, day -> acc or (1 shl (day - 1)) }

    fun days(mask: Int): Set<Int> = (1..7).filterTo(mutableSetOf()) { mask and (1 shl (it - 1)) != 0 }

    fun isTrainingDay(mask: Int, date: LocalDate): Boolean = mask and (1 shl (date.dayOfWeek.value - 1)) != 0
}
