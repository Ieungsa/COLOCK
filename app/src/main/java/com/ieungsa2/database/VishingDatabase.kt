package com.ieungsa2.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [VishingAlert::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VishingDatabase : RoomDatabase() {

    abstract fun vishingAlertDao(): VishingAlertDao

    companion object {
        @Volatile
        private var INSTANCE: VishingDatabase? = null

        fun getDatabase(context: Context): VishingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VishingDatabase::class.java,
                    "vishing_database"
                )
                .fallbackToDestructiveMigration() // 스키마 변경 시 데이터 초기화
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
