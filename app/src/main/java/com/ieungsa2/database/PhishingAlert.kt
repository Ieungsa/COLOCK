package com.ieungsa2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "phishing_alerts")
data class PhishingAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Date,           // 감지 시간
    val sender: String,            // 발신자
    val messageBody: String,       // 문자 전체 내용
    val detectedUrl: String,       // 감지된 위험 URL
    val riskScore: Float,          // 위험도 점수 (0.0 ~ 1.0)
    val riskLevel: String,         // 위험 등급 ("의심", "위험", "매우 위험")
    val type: String = "SMISHING", // "SMISHING" 또는 "VISHING"
    val isRead: Boolean = false,   // 사용자가 확인했는지
    val isReported: Boolean = false // 신고했는지
)