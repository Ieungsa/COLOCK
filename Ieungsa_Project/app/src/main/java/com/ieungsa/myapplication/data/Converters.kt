package com.ieungsa.myapplication.data

import androidx.room.TypeConverter

/**
 * Room Database Type Converters
 */
class Converters {

    @TypeConverter
    fun fromScamCategory(value: ScamCategory): String {
        return value.name
    }

    @TypeConverter
    fun toScamCategory(value: String): ScamCategory {
        return try {
            ScamCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ScamCategory.OTHER
        }
    }
}
