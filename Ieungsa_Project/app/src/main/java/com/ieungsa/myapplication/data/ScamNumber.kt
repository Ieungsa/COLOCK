package com.ieungsa.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 스캠 전화번호 Entity
 */
@Entity(tableName = "scam_numbers")
data class ScamNumber(
    @PrimaryKey
    val phoneNumber: String,

    // 위험도 (1: 낮음, 2: 중간, 3: 높음)
    val riskLevel: Int,

    // 신고 횟수
    val reportCount: Int = 1,

    // 카테고리
    val category: ScamCategory,

    // 설명
    val description: String = "",

    // 등록일
    val registeredAt: Long = System.currentTimeMillis(),

    // 마지막 신고일
    val lastReportedAt: Long = System.currentTimeMillis(),

    // 사용자가 직접 차단한 번호인지 여부
    val isUserBlocked: Boolean = false
)

/**
 * 스캠 카테고리
 */
enum class ScamCategory {
    VOICE_PHISHING,  // 보이스피싱
    SMISHING,        // 스미싱
    LOAN_FRAUD,      // 대출 사기
    INVESTMENT_FRAUD, // 투자 사기
    IMPERSONATION,   // 기관 사칭
    SPAM,            // 스팸
    OTHER            // 기타
}
