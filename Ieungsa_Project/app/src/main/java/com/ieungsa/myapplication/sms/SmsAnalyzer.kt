package com.ieungsa.myapplication.sms

import android.util.Log
import java.util.regex.Pattern

/**
 * SMS 메시지 분석기
 * - 피싱 키워드 검출
 * - URL 안전성 검증
 * - 위험도 평가
 */
class SmsAnalyzer {

    companion object {
        private const val TAG = "SmsAnalyzer"

        // 피싱 키워드 (가중치 포함)
        private val PHISHING_KEYWORDS = mapOf(
            // 금융/대출 사기
            "저금리" to 3,
            "대출" to 2,
            "승인" to 1,
            "신청하세요" to 2,
            "즉시" to 2,
            "무담보" to 3,
            "무보증" to 3,
            "신용" to 1,

            // 기관 사칭
            "경찰청" to 3,
            "검찰청" to 3,
            "금융감독원" to 3,
            "국세청" to 3,
            "우체국" to 2,
            "은행" to 1,
            "법원" to 3,
            "우리은행" to 2,
            "국민은행" to 2,
            "신한은행" to 2,

            // 긴급/협박
            "긴급" to 2,
            "즉시" to 2,
            "24시간" to 2,
            "마감" to 2,
            "체포" to 3,
            "압수" to 3,
            "출석" to 3,
            "명의도용" to 3,

            // 보상/당첨
            "당첨" to 2,
            "환급" to 3,
            "보상금" to 3,
            "적립금" to 2,
            "포인트" to 1,

            // 개인정보 요구
            "계좌번호" to 3,
            "비밀번호" to 3,
            "인증번호" to 2,
            "주민번호" to 3,
            "카드번호" to 3,
            "CVC" to 3,
            "CVV" to 3,

            // 액션 유도
            "클릭" to 1,
            "다운로드" to 2,
            "설치" to 2,
            "확인하세요" to 1,
            "입력하세요" to 2
        )

        // URL 정규식
        private val URL_PATTERN = Pattern.compile(
            "(https?://[\\w가-힣\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
        )

        // 의심스러운 URL 패턴
        private val SUSPICIOUS_URL_PATTERNS = listOf(
            Pattern.compile(".*\\.(top|xyz|club|online|site|work|click|link)\$"),  // 의심 도메인
            Pattern.compile(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*"),  // IP 주소
            Pattern.compile(".*bit\\.ly.*"),  // 단축 URL
            Pattern.compile(".*goo\\.gl.*"),
            Pattern.compile(".*tinyurl\\.com.*"),
            Pattern.compile(".*ow\\.ly.*"),
            Pattern.compile(".*[가-힣]+\\.(com|net|org)"),  // 한글 도메인
            Pattern.compile(".*-.*-.*\\..*")  // 하이픈이 많은 도메인
        )
    }

    /**
     * SMS 메시지 분석
     */
    fun analyze(sender: String, message: String): SmsAnalysisResult {
        Log.d(TAG, "SMS 분석 시작: $sender")

        var riskScore = 0
        val detectedKeywords = mutableListOf<String>()
        val detectedUrls = mutableListOf<String>()
        val reasons = mutableListOf<String>()

        // 1. 키워드 검출
        PHISHING_KEYWORDS.forEach { (keyword, weight) ->
            if (message.contains(keyword, ignoreCase = true)) {
                riskScore += weight
                detectedKeywords.add(keyword)
                Log.d(TAG, "키워드 검출: $keyword (가중치: $weight)")
            }
        }

        if (detectedKeywords.isNotEmpty()) {
            reasons.add("의심 키워드: ${detectedKeywords.take(3).joinToString(", ")}")
        }

        // 2. URL 추출 및 검증
        val matcher = URL_PATTERN.matcher(message)
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            detectedUrls.add(url)

            // URL 안전성 검증
            val urlRisk = checkUrlSafety(url)
            riskScore += urlRisk

            if (urlRisk > 0) {
                reasons.add("의심 링크: $url")
                Log.d(TAG, "의심 URL 검출: $url (위험도: $urlRisk)")
            }
        }

        // 3. 발신자 검증
        if (!sender.startsWith("+") && sender.length < 10) {
            // 짧은 번호 (스팸 가능성)
            riskScore += 1
            reasons.add("짧은 발신번호")
        }

        // 4. 메시지 길이 검증 (너무 짧거나 긴 경우)
        if (message.length > 500) {
            riskScore += 1
            reasons.add("비정상적으로 긴 메시지")
        }

        // 5. 위험도 계산
        val riskLevel = when {
            riskScore >= 8 -> RiskLevel.HIGH
            riskScore >= 4 -> RiskLevel.MEDIUM
            riskScore > 0 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }

        Log.d(TAG, "SMS 분석 완료: 위험도=$riskLevel, 점수=$riskScore")

        return SmsAnalysisResult(
            isPhishing = riskLevel != RiskLevel.SAFE,
            riskLevel = riskLevel,
            riskScore = riskScore,
            detectedKeywords = detectedKeywords,
            detectedUrls = detectedUrls,
            reasons = reasons
        )
    }

    /**
     * URL 안전성 검증
     */
    private fun checkUrlSafety(url: String): Int {
        var risk = 0

        // 의심스러운 도메인 패턴 체크
        SUSPICIOUS_URL_PATTERNS.forEach { pattern ->
            if (pattern.matcher(url).matches()) {
                risk += 2
            }
        }

        // HTTP (HTTPS 아님) 체크
        if (url.startsWith("http://", ignoreCase = true)) {
            risk += 1
        }

        // 특수문자가 많은 경우
        val specialCharCount = url.count { it == '@' || it == '%' || it == '?' }
        if (specialCharCount > 3) {
            risk += 1
        }

        return risk
    }
}

/**
 * SMS 분석 결과
 */
data class SmsAnalysisResult(
    val isPhishing: Boolean,
    val riskLevel: RiskLevel,
    val riskScore: Int,
    val detectedKeywords: List<String>,
    val detectedUrls: List<String>,
    val reasons: List<String>
)

/**
 * 위험도
 */
enum class RiskLevel {
    SAFE,    // 안전
    LOW,     // 낮음
    MEDIUM,  // 중간
    HIGH     // 높음
}
