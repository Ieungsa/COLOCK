package com.ieungsa2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FamilyVerifyLog::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FamilyVerifyDatabase : RoomDatabase() {
    abstract fun familyVerifyLogDao(): FamilyVerifyLogDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyVerifyDatabase? = null

        fun getDatabase(context: Context): FamilyVerifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FamilyVerifyDatabase::class.java,
                    "family_verify_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
