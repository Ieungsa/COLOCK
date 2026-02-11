package com.ieungsa.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보이스피싱 탐지 이력 Entity
 */
@Entity(tableName = "detection_history")
data class DetectionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 탐지 시간
    val timestamp: Long = System.currentTimeMillis(),

    // 최종 신뢰도 (0.0 ~ 1.0)
    val confidence: Float,

    // AI 모델 신뢰도 (0.0 ~ 1.0)
    val aiConfidence: Float,

    // 특징 기반 탐지 여부
    val hasSignature: Boolean,

    // 오디오 소스 (VOICE_DOWNLINK, VOICE_CALL 등)
    val audioSource: String,

    // 연속 탐지 버퍼 카운트
    val bufferCount: Int
)
