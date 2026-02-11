package com.ieungsa2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "family_verify_logs")
data class FamilyVerifyLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Date,
    val requestId: String,
    val myPhone: String,
    val targetPhone: String,
    val requestType: String, // "SENT" (내가 요청), "RECEIVED" (받음)
    val status: String, // "PENDING", "APPROVED", "REJECTED"
    val isRead: Boolean = false
)
