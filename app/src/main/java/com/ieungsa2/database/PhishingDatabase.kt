package com.ieungsa2.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [PhishingAlert::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PhishingDatabase : RoomDatabase() {
    
    abstract fun phishingAlertDao(): PhishingAlertDao
    
    companion object {
        @Volatile
        private var INSTANCE: PhishingDatabase? = null
        
        fun getDatabase(context: Context): PhishingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhishingDatabase::class.java,
                    "phishing_database"
                )
                .fallbackToDestructiveMigration() // 스키마 변경 시 데이터 초기화
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}