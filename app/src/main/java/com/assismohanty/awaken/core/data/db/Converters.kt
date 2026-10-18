package com.assismohanty.awaken.core.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Enums are stored by name (Room built-in). Dates as ISO `yyyy-MM-dd`, instants as epoch millis UTC (ARCH §2). */
class Converters {
    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun stringSetToString(values: Set<String>?): String? = values?.sorted()?.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToStringSet(value: String?): Set<String>? =
        value?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet()

    private companion object {
        const val SEPARATOR = ","
    }
}
