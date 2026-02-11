package com.ieungsa2

import android.util.Log
import java.net.URL
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import java.security.cert.X509Certificate
import java.security.cert.CertificateException
import java.net.UnknownHostException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import com.ieungsa2.network.RetrofitClient
import com.ieungsa2.network.SafeBrowsingService
import com.ieungsa2.network.SafeBrowsingRequest
import com.ieungsa2.network.ThreatInfo
import com.ieungsa2.network.ThreatEntry

class UrlAnalyzer {
    
    // Google Safe Browsing API Key
    // TODO: Replace with your actual API Key
    private val API_KEY = "YOUR_API_KEY"
    
    companion object {
        private const val TAG = "UrlAnalyzer"
        
        // Suspicious TLDs commonly used in phishing
        private val SUSPICIOUS_TLDS = setOf(
            "tk", "ml", "cf", "ga", "pw", "top", "buzz", "info", "xyz",
            "click", "download", "loan", "work", "party", "racing",
            "science", "accountant", "trade", "webcam", "win", "date"
        )
        
        // URL Shorteners
        private val URL_SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", 
            "short.link", "cutt.ly", "rb.gy", "han.gl"
        )
        
        // Phishing Keywords (Korean & English)
        private val PHISHING_KEYWORDS = setOf(
            "긴급", "즉시", "확인", "차단", "정지", "마감", "urgent", "immediate", "verify",
            "택배", "배송", "delivery", "package", "shipping",
            "은행", "카드", "결제", "환불", "bank", "card", "payment", "refund", "account",
            "당첨", "이벤트", "무료", "선물", "winner", "free", "gift", "prize",
            "scam", "fake", "phish", "fraud", "steal", "hack", "security",
            "login", "signin", "password", "update", "suspend", "verify",
            "슬랏", "슬롯", "카지노", "배팅", "베팅", "도박", "포커", "바카라", "룰렛",
            "나이스", "초이스", "페이백", "컴프", "스포츠", "토토", "경마", "복권",
            "무한", "영업", "중", "환급", "적중", "수익", "대박", "잭팟",
            "casino", "bet", "slot", "poker", "baccarat", "roulette", "sports", "comp",
            "payback", "jackpot", "winning", "profit", "gambling"
        )
        
        // Major Brands for Typosquatting Check
        private val MAJOR_BRANDS = setOf(
            "naver", "kakao", "samsung", "lg", "sk", "kt", "lotte",
            "nhbank", "kbbank", "shinhan", "woori", "hana",
            "coupang", "11st", "gmarket", "auction"
        )
        
        // URL 추출 정규식에 사용될 TLD 목록
        val COMMON_TLDS = setOf(
            // 일반 TLD
            "com", "org", "net", "edu", "gov", "mil", "int", "info", "biz", "name",
            "pro", "aero", "coop", "museum", "mobi", "asia", "tel", "xxx",
            // 국가 코드 TLD (일부)
            "kr", "co.kr", "go.kr", "ac.kr", "or.kr", "pe.kr", "es.kr", "ms.kr",
            "us", "uk", "jp", "cn", "de", "fr", "ru", "ca", "au",
            // 스팸/피싱에 자주 사용되는 TLD
            "xyz", "tk", "ml", "cf", "ga", "gq", "pw", "top", "click", "loan",
            "trade", "win", "buzz", "science", "party", "racing", "download",
            "work", "date", "accountant", "webcam"
        )
    }
    
    suspend fun analyzeUrlPattern(url: String, messageBody: String = ""): Float {
        Log.d(TAG, "🔍 [[디버그]] UrlAnalyzer 분석 시작: url=$url")
        var riskScore = 0f
        
        try {
            // URL 정규화
            val normalizedUrl = normalizeUrl(url)
            val urlObj = URL(normalizedUrl)
            val domain = urlObj.host.lowercase()

            // 1. 화이트리스트 확인
            for (whiteDomain in LinkDatabase.WHITELIST_DOMAINS) {
                if (domain == whiteDomain || domain.endsWith(".$whiteDomain")) {
                    Log.d(TAG, "✅ [[디버그]] 화이트리스트 감지 ($domain): 0.0 반환")
                    return 0.0f
                }
            }

            // 2. Google Safe Browsing API 확인 (강력한 실시간 분석)
            val googleRisk = checkWithGoogleSafeBrowsing(normalizedUrl)
            if (googleRisk > 0f) {
                Log.d(TAG, "🚨 [[디버그]] Google Safe Browsing 감지: $googleRisk 반환")
                return googleRisk
            }

            // 도메인 내 명백히 위험한 단어
            val suspiciousWords = setOf("scam", "fake", "phish", "fraud", "steal", "hack", "cheat", "spam")
            for (word in suspiciousWords) {
                if (domain.contains(word)) {
                    Log.d(TAG, "🚨 [[디버그]] 매우 위험한 단어 감지 ($word): 1.0 반환")
                    return 1.0f
                }
            }

            // IP 주소 URL
            if (isIpAddress(domain)) {
                Log.d(TAG, "🚨 [[디버그]] IP 주소 URL 감지: 1.0 반환")
                return 1.0f
            }

            // 3. 블랙리스트 확인
            for (blackPattern in LinkDatabase.BLACKLIST_PATTERNS) {
                if (normalizedUrl.contains(blackPattern)) {
                    Log.d(TAG, "🚨 [[디버그]] 블랙리스트 패턴 감지 ($blackPattern): 1.0 반환")
                    return 1.0f
                }
            }
            
            // 4. 기존 휴리스틱 분석
            val domainScore = analyzeDomain(domain)
            riskScore += domainScore
            Log.d(TAG, "📊 [[디버그]] 도메인 점수: $domainScore (누적: $riskScore)")
            
            val structureScore = analyzeUrlStructure(normalizedUrl)
            riskScore += structureScore
            Log.d(TAG, "📊 [[디버그]] 구조 점수: $structureScore (누적: $riskScore)")
            
            val patternScore = analyzeSpecialPatterns(normalizedUrl, messageBody)
            riskScore += patternScore
            Log.d(TAG, "📊 [[디버그]] 패턴 점수: $patternScore (누적: $riskScore)")
            
            // 5. SSL 인증서 검증
            if (normalizedUrl.startsWith("https://")) {
                val sslScore = analyzeSslCertificate(normalizedUrl)
                riskScore += sslScore
                Log.d(TAG, "📊 [[디버그]] SSL 점수: $sslScore (누적: $riskScore)")
            } else {
                riskScore += 0.3f
                Log.d(TAG, "📊 [[디버그]] HTTP 비보안 가점: +0.3 (누적: $riskScore)")
            }
            
            Log.d(TAG, "🏁 [[디버그]] 최종 계산된 위험도: $riskScore")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ [[디버그]] 분석 중 오류 발생: ${e.message}")
            riskScore = 0.8f 
        }
        
        return minOf(riskScore, 1.0f)
    }
    
    private suspend fun checkWithGoogleSafeBrowsing(url: String): Float = withContext(Dispatchers.IO) {
        Log.d(TAG, "🌐 Google Safe Browsing 요청 시도: $url")
        try {
            val retrofit = RetrofitClient.getClient("https://safebrowsing.googleapis.com/")
            val service = retrofit.create(SafeBrowsingService::class.java)
            
            val request = SafeBrowsingRequest(
                threatInfo = ThreatInfo(
                    threatEntries = listOf(ThreatEntry(url))
                )
            )
            
            val response = service.checkUrl(API_KEY, request)
            if (response.isSuccessful) {
                val matches = response.body()?.matches
                if (!matches.isNullOrEmpty()) {
                    Log.e(TAG, "🚨 Google Safe Browsing에서 위협 감지됨!!! 타입: ${matches[0].threatType}")
                    return@withContext 1.0f 
                } else {
                    Log.d(TAG, "✅ Google Safe Browsing 결과: 안전함")
                }
            } else {
                Log.w(TAG, "⚠️ Google Safe Browsing 응답 실패 (코드: ${response.code()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Google Safe Browsing 확인 중 예외 발생: ${e.message}")
        }
        return@withContext 0f
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        
        // http:// 또는 https:// 추가
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        
        return normalized
    }
    
    private fun analyzeDomain(domain: String): Float {
        var score = 0f
        
        // 의심스러운 TLD
        val tld = domain.substringAfterLast(".")
        if (tld in SUSPICIOUS_TLDS) {
            score += 0.4f  // 0.3에서 0.4로 증가
            Log.d(TAG, "의심스러운 TLD ($tld): +0.4")
        }
        
        // 단축 URL 서비스
        if (URL_SHORTENERS.any { domain.contains(it) }) {
            score += 0.4f
            Log.d(TAG, "단축 URL 감지: +0.4")
        }
        
        // 의심스러운 도메인 이름 패턴
        score += checkSuspiciousDomainName(domain)
        
        // 과도한 서브도메인
        val subdomainCount = domain.count { it == '.' }
        if (subdomainCount > 3) {
            score += 0.2f
            Log.d(TAG, "과도한 서브도메인 ($subdomainCount): +0.2")
        }
        
        // 브랜드 typosquatting 체크
        score += checkBrandTyposquatting(domain)
        
        return score
    }
    
    private fun analyzeUrlStructure(url: String): Float {
        var score = 0f
        
        // URL 길이 (너무 길거나 짧은 경우)
        when {
            url.length > 150 -> {
                score += 0.3f
                Log.d(TAG, "URL 너무 김 (${url.length}): +0.3")
            }
            url.length < 10 -> {
                score += 0.2f
                Log.d(TAG, "URL 너무 짧음 (${url.length}): +0.2")
            }
        }
        
        // 특수문자 비율
        val specialCharCount = url.count { !it.isLetterOrDigit() && it != '.' && it != '/' && it != ':' }
        val specialCharRatio = specialCharCount.toFloat() / url.length
        if (specialCharRatio > 0.3f) {
            score += 0.2f
            Log.d(TAG, "특수문자 과다 ($specialCharRatio): +0.2")
        }
        
        // 숫자 과다 사용
        val digitRatio = url.count { it.isDigit() }.toFloat() / url.length
        if (digitRatio > 0.3f) {
            score += 0.15f
            Log.d(TAG, "숫자 과다 ($digitRatio): +0.15")
        }
        
        return score
    }
    
    private fun analyzeSpecialPatterns(url: String, messageBody: String = ""): Float {
        var score = 0f
        
        // URL에서 피싱 키워드 확인
        val urlKeywordCount = PHISHING_KEYWORDS.count { url.lowercase().contains(it) }
        if (urlKeywordCount > 0) {
            score += urlKeywordCount * 0.15f
            Log.d(TAG, "URL에서 피싱 키워드 ${urlKeywordCount}개 발견: +${urlKeywordCount * 0.15f}")
        }
        
        // 메시지 본문에서 피싱 키워드 확인 (더 강력하게)
        if (messageBody.isNotEmpty()) {
            val messageKeywordCount = PHISHING_KEYWORDS.count { messageBody.lowercase().contains(it) }
            if (messageKeywordCount > 0) {
                score += messageKeywordCount * 0.2f
                Log.d(TAG, "메시지에서 피싱 키워드 ${messageKeywordCount}개 발견: +${messageKeywordCount * 0.2f}")
            }
            
            // 도박 관련 특수 패턴 확인 (URL에서 사용되는 // 제외)
            val gamblingPatterns = listOf(
                "((", "))", "[[", "]]", // 특수 문자 패턴 (// 제거 - URL에 포함됨)
                "무한영업", "영업중", // 도박 특수 용어 (더 구체적으로)
                "페이백", "컴프", "슬랏" // 도박 전용 용어
            )
            
            val gamblingCount = gamblingPatterns.count { messageBody.contains(it) }
            if (gamblingCount > 0) {
                score += gamblingCount * 0.3f
                Log.d(TAG, "도박 관련 특수 패턴 ${gamblingCount}개 발견: +${gamblingCount * 0.3f}")
            }
        }
        
        return score
    }
    
    private fun checkSuspiciousDomainName(domain: String): Float {
        var score = 0f
        val domainLower = domain.lowercase()
        
        // 도박 관련 의심 단어들
        val gamblingWords = setOf("bet", "casino", "poker", "slot", "girn", "game", "win", "prize", "lucky")
        for (word in gamblingWords) {
            if (domainLower.contains(word)) {
                score += 0.6f
                Log.d(TAG, "도박 관련 도메인 단어 감지 ($word): +0.6")
                break
            }
        }
        
        // 금융/배송 관련 스푸핑 단어
        val targetWords = setOf("bank", "delivery", "secure", "verify", "update", "account", "login", "pay")
        for (word in targetWords) {
            if (domainLower.contains(word)) {
                score += 0.4f // 0.3에서 0.4로 증가
                Log.d(TAG, "스푸핑 의심 단어 감지 ($word): +0.4")
                break
            }
        }
        
        // 숫자가 많이 포함된 도메인 (예: girn107)
        val digitCount = domainLower.count { it.isDigit() }
        if (digitCount > 2) {
            score += 0.2f
            Log.d(TAG, "숫자 과다 도메인 (${digitCount}개): +0.2")
        }
        
        return score
    }
    
    private fun checkBrandTyposquatting(domain: String): Float {
        for (brand in MAJOR_BRANDS) {
            val similarity = calculateStringSimilarity(domain, brand)
            if (similarity > 0.7f && similarity < 1.0f) {
                Log.d(TAG, "브랜드 유사성 감지 ($brand): $similarity")
                return 0.5f
            }
        }
        return 0f
    }
    
    private fun isIpAddress(domain: String): Boolean {
        val ipPattern = Pattern.compile(
            "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"
        )
        return ipPattern.matcher(domain).matches()
    }
    
    private fun calculateStringSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0f
        
        val editDistance = calculateEditDistance(longer, shorter)
        return (longer.length - editDistance).toFloat() / longer.length
    }
    
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) {
            for (j in 0..n) {
                when {
                    i == 0 -> dp[i][j] = j
                    j == 0 -> dp[i][j] = i
                    s1[i - 1] == s2[j - 1] -> dp[i][j] = dp[i - 1][j - 1]
                    else -> dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[m][n]
    }
    
    private suspend fun analyzeSslCertificate(url: String): Float = withContext(Dispatchers.IO) {
        var riskScore = 0f
        
        try {
            // 3초 타임아웃으로 SSL 인증서 검사
            val result = withTimeoutOrNull(3000) {
                checkSslCertificate(url)
            }
            
            when (result) {
                is SslCheckResult.Valid -> {
                    Log.d(TAG, "SSL 인증서 유효: ${result.issuer}")
                    // 유효한 SSL은 위험도를 낮춤
                    riskScore -= 0.1f
                }
                is SslCheckResult.SelfSigned -> {
                    Log.d(TAG, "자체 서명 인증서 감지: +0.4")
                    riskScore += 0.4f
                }
                is SslCheckResult.Expired -> {
                    Log.d(TAG, "만료된 인증서: +0.5")
                    riskScore += 0.5f
                }
                is SslCheckResult.Invalid -> {
                    Log.d(TAG, "유효하지 않은 인증서: +0.6")
                    riskScore += 0.6f
                }
                is SslCheckResult.Unreachable -> {
                    Log.d(TAG, "사이트 접근 불가: +0.3")
                    riskScore += 0.3f
                }
                null -> {
                    Log.d(TAG, "SSL 검사 타임아웃: +0.2")
                    riskScore += 0.2f
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SSL 검사 오류: ${e.message}")
            riskScore += 0.3f // 검사 실패시 약간 위험하다고 봄
        }
        
        return@withContext riskScore
    }
    
    private fun checkSslCertificate(url: String): SslCheckResult {
        try {
            val urlObj = URL(url)
            val connection = urlObj.openConnection() as HttpsURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "HEAD" // 헤더만 요청
            
            connection.connect()
            
            val certificates = connection.serverCertificates
            if (certificates.isNotEmpty()) {
                val cert = certificates[0] as X509Certificate
                
                // 인증서 만료 확인
                try {
                    cert.checkValidity()
                } catch (e: CertificateException) {
                    return SslCheckResult.Expired(e.message ?: "만료됨")
                }
                
                // 자체 서명 인증서 확인
                if (cert.subjectDN == cert.issuerDN) {
                    return SslCheckResult.SelfSigned(cert.issuerDN.toString())
                }
                
                // 유효한 인증서
                return SslCheckResult.Valid(cert.issuerDN.toString())
            }
            
            return SslCheckResult.Invalid("인증서 없음")
            
        } catch (e: SSLException) {
            return SslCheckResult.Invalid("SSL 오류: ${e.message}")
        } catch (e: UnknownHostException) {
            return SslCheckResult.Unreachable("호스트를 찾을 수 없음")
        } catch (e: IOException) {
            return SslCheckResult.Unreachable("네트워크 오류: ${e.message}")
        } catch (e: Exception) {
            return SslCheckResult.Invalid("검사 실패: ${e.message}")
        }
    }
    
    sealed class SslCheckResult {
        data class Valid(val issuer: String) : SslCheckResult()
        data class SelfSigned(val issuer: String) : SslCheckResult()
        data class Expired(val reason: String) : SslCheckResult()
        data class Invalid(val reason: String) : SslCheckResult()
        data class Unreachable(val reason: String) : SslCheckResult()
    }
}
