package com.ieungsa2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DataLeakAlert::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DataLeakDatabase : RoomDatabase() {
    abstract fun dataLeakAlertDao(): DataLeakAlertDao

    companion object {
        @Volatile
        private var INSTANCE: DataLeakDatabase? = null

        fun getDatabase(context: Context): DataLeakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DataLeakDatabase::class.java,
                    "data_leak_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
