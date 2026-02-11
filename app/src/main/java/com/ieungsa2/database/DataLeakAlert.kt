package com.ieungsa2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.Date

@Entity(tableName = "data_leak_alerts")
data class DataLeakAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Date,
    val appName: String,
    val packageName: String,
    val usageMB: Double,
    val thresholdMB: Int,
    val wasScreenOff: Boolean, // 화면 꺼진 상태였는지
    val isRead: Boolean = false
)
