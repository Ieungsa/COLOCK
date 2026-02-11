package com.ieungsa.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * VoiceGuard Room Database
 */
@Database(
    entities = [DetectionHistory::class, ScamNumber::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoiceGuardDatabase : RoomDatabase() {

    abstract fun detectionHistoryDao(): DetectionHistoryDao
    abstract fun scamNumberDao(): ScamNumberDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceGuardDatabase? = null

        fun getDatabase(context: Context): VoiceGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceGuardDatabase::class.java,
                    "voiceguard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
