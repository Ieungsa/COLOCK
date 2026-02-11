package com.ieungsa2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "vishing_alerts")
data class VishingAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Date,           // 감지 시간
    val phoneNumber: String,       // 전화번호
    val callDuration: Long,        // 통화 시간 (초)
    val transcription: String,     // STT 받아쓰기 내용
    val detectedKeywords: String,  // 감지된 키워드 (쉼표로 구분)
    val riskScore: Float,          // 위험도 점수 (0.0 ~ 1.0)
    val riskLevel: String,         // 위험 등급 ("의심", "위험", "매우 위험")
    val isRead: Boolean = false,   // 사용자가 확인했는지
    val isReported: Boolean = false // 신고했는지
)
