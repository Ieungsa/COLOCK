package com.ieungsa2

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.ieungsa2.database.PhishingAlert
import com.ieungsa2.database.PhishingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class SmsContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private val MMS_URI = Uri.parse("content://mms")
        private val SMS_URI = Uri.parse("content://sms")
        private val MMS_SMS_URI = Uri.parse("content://mms-sms")
    }

    // 마지막으로 처리한 메시지 ID (중복 처리 방지)
    private var lastProcessedSmsId: Long = -1
    private var lastProcessedMmsId: Long = -1
    private var lastProcessTime: Long = 0

    // 알려진 TLD 목록
    private val KNOWN_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "info", "biz", "name",
        "pro", "aero", "coop", "museum", "mobi", "asia", "tel",
        "kr", "us", "uk", "jp", "cn", "de", "fr", "ru", "ca", "au", "in", "br",
        "xyz", "tk", "ml", "cf", "ga", "gq", "pw", "top", "click", "loan",
        "trade", "win", "buzz", "science", "party", "racing", "download",
        "work", "date", "accountant", "webcam", "io", "me", "tv", "cc", "co", "site", "online", "club"
    )

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Log.d(TAG, "🔔 [디버그] onChange 호출됨: uri=$uri, selfChange=$selfChange")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 너무 빈번한 호출 방지 (2초 이내 중복 호출 무시)
                val currentTime = System.currentTimeMillis()
                val timeDiffFromLastProcess = currentTime - lastProcessTime
                if (timeDiffFromLastProcess < 2000) {
                    Log.d(TAG, "⏳ [디버그] 너무 빈번한 호출로 무시됨 (이전 호출과 ${timeDiffFromLastProcess}ms 차이)")
                    return@launch
                }
                lastProcessTime = currentTime

                Log.d(TAG, "🔍 [디버그] 메시지 분석 시작...")

                try {
                    // 최신 SMS 확인
                    checkLatestSms()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [디버그] SMS 확인 중 오류: ${e.message}")
                }

                try {
                    // 최신 MMS 확인
                    checkLatestMms()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [디버그] MMS 확인 중 오류: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [디버그] onChange 내부 오류: ${e.message}")
            }
        }
    }

    private fun checkLatestSms() {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                    if (idIndex < 0 || dateIndex < 0) {
                        Log.e(TAG, "SMS 컬럼 인덱스 없음")
                        return
                    }

                    val id = it.getLong(idIndex)
                    val date = it.getLong(dateIndex)
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - date

                    Log.d(TAG, "📩 [디버그] 최신 SMS 조회 성공: ID=$id, 시간차=${timeDiff}ms (메시지시간=$date, 현재시간=$currentTime)")

                    // 이미 처리한 메시지면 스킵
                    if (id == lastProcessedSmsId) {
                        Log.d(TAG, "⏭️ [디버그] 이미 처리한 SMS ID ($id) 건너뜀")
                        return
                    }

                    val address = if (addressIndex >= 0) it.getString(addressIndex) ?: "알 수 없음" else "알 수 없음"
                    val body = if (bodyIndex >= 0) it.getString(bodyIndex) ?: "" else ""

                    // 최근 60초 이내 메시지만 처리 (기존 30초에서 60초로 완화)
                    if (timeDiff > 60000 || timeDiff < -5000) { // 미래 시간 메시지도 약간 허용
                        Log.w(TAG, "⚠️ [디버그] 오래된 메시지 또는 시간 불일치로 무시됨: timeDiff=${timeDiff}ms")
                        lastProcessedSmsId = id
                        return
                    }

                    lastProcessedSmsId = id
                    Log.d(TAG, "✅ [디버그] 새 SMS 감지 성공: 발신자=$address, 본문길이=${body.length}")

                    processMessage(address, body, "SMS")
                } else {
                    Log.d(TAG, "ℹ️ [디버그] 조회된 SMS가 없습니다.")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "SMS 확인 오류: ${e.message}")
        }
    }

    private fun checkLatestMms() {
        try {
            val cursor = context.contentResolver.query(
                MMS_URI,
                arrayOf("_id", "date", "msg_box"),
                "msg_box = 1", // 1 = 받은 메시지
                null,
                "date DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex("_id")
                    val dateIndex = it.getColumnIndex("date")

                    if (idIndex < 0 || dateIndex < 0) {
                        Log.e(TAG, "MMS 컬럼 인덱스 없음")
                        return
                    }

                    val id = it.getLong(idIndex)

                    // 이미 처리한 메시지면 스킵
                    if (id == lastProcessedMmsId) {
                        return
                    }

                    val date = it.getLong(dateIndex) * 1000 // MMS date는 초 단위

                    // 최근 60초 이내 메시지만 처리 (MMS는 처리가 느릴 수 있음)
                    val timeDiff = System.currentTimeMillis() - date
                    if (timeDiff > 60000) {
                        lastProcessedMmsId = id
                        return
                    }

                    lastProcessedMmsId = id

                    // MMS 본문 읽기
                    val body = getMmsText(id)
                    val address = getMmsAddress(id)

                    Log.d(TAG, "새 MMS 감지: ID=$id, 발신자=$address, 길이=${body.length}")

                    if (body.isNotEmpty()) {
                        processMessage(address, body, "MMS")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "MMS 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "MMS 확인 오류: ${e.message}")
        }
    }

    private fun getMmsText(mmsId: Long): String {
        val builder = StringBuilder()

        try {
            val uri = Uri.parse("content://mms/part")
            val cursor = context.contentResolver.query(
                uri,
                null, // 모든 컬럼
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )

            cursor?.use {
                val ctIndex = it.getColumnIndex("ct")
                val textIndex = it.getColumnIndex("text")
                val idIndex = it.getColumnIndex("_id")

                while (it.moveToNext()) {
                    val contentType = if (ctIndex >= 0) it.getString(ctIndex) ?: "" else ""

                    if (contentType == "text/plain") {
                        val text = if (textIndex >= 0) it.getString(textIndex) else null
                        if (text != null) {
                            builder.append(text)
                        } else if (idIndex >= 0) {
                            // text가 null이면 _data에서 읽어야 함
                            try {
                                val partId = it.getLong(idIndex)
                                val partUri = Uri.parse("content://mms/part/$partId")
                                context.contentResolver.openInputStream(partUri)?.use { inputStream ->
                                    builder.append(inputStream.bufferedReader().readText())
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "MMS part 읽기 오류: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "MMS 텍스트 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "MMS 텍스트 읽기 오류: ${e.message}")
        }

        return builder.toString()
    }

    private fun getMmsAddress(mmsId: Long): String {
        try {
            val uri = Uri.parse("content://mms/$mmsId/addr")
            val cursor = context.contentResolver.query(
                uri,
                null, // 모든 컬럼
                "type = 137", // 137 = FROM
                null,
                null
            )

            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                if (it.moveToFirst() && addressIndex >= 0) {
                    return it.getString(addressIndex) ?: "알 수 없음"
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "MMS 주소 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "MMS 주소 읽기 오류: ${e.message}")
        }

        return "알 수 없음"
    }

    private fun processMessage(sender: String, body: String, type: String) {
        Log.d(TAG, "[$type] 메시지 처리 시작: 발신자=$sender, 길이=${body.length}")

        val urls = extractUrls(body)

        if (urls.isEmpty()) {
            Log.d(TAG, "[$type] URL 없음")
            return
        }

        Log.d(TAG, "[$type] ${urls.size}개 URL 발견: $urls")

        CoroutineScope(Dispatchers.IO).launch {
            analyzeUrls(urls, sender, body, type)
        }
    }

    private fun extractUrls(text: String): List<String> {
        val urls = mutableSetOf<String>()
        var modifiedText = text

        // 1. http:// 또는 www로 시작하는 URL 먼저 추출하고, 해당 부분을 공백으로 치환
        val urlPattern = java.util.regex.Pattern.compile(
            "(?:https?://|www\\.)[^\\s가-힣]+",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val matcher = urlPattern.matcher(modifiedText)
        val foundRanges = mutableListOf<IntRange>()

        while (matcher.find()) {
            var url = matcher.group(0) ?: continue
            url = cleanUrlTail(url)
            url = balanceParentheses(url)

            if (url.isNotEmpty()) {
                urls.add(url)
                foundRanges.add(matcher.start()..matcher.end())
            }
        }

        // 찾은 URL 부분을 공백으로 치환하여 중복 검사를 방지
        if (foundRanges.isNotEmpty()){
            val sb = StringBuilder(modifiedText)
            for (range in foundRanges.reversed()) {
                for (i in range) {
                    sb.setCharAt(i, ' ')
                }
            }
            modifiedText = sb.toString()
        }

        // 2. 프로토콜 없는 도메인 추출 (위에서 치환된 텍스트 기반)
        val domainPattern = java.util.regex.Pattern.compile(
            "([a-zA-Z0-9][-a-zA-Z0-9]*\\.)+([a-zA-Z]{2,})[^\\s가-힣]*",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val domainMatcher = domainPattern.matcher(modifiedText)

        while (domainMatcher.find()) {
            var candidate = domainMatcher.group(0) ?: continue

            // 이메일 도메인 필터링
            val matchStart = domainMatcher.start()
            if (matchStart > 0 && modifiedText[matchStart - 1] == '@') {
                continue // @ 뒤에 오는 도메인은 건너뛰기
            }

            // 'www.'는 1단계에서 처리했으므로 건너뛰기
            if (candidate.lowercase().startsWith("www.")) {
                continue
            }

            val parts = candidate.split("/")[0].split(".")
            val hasTld = parts.any { part ->
                KNOWN_TLDS.contains(part.lowercase())
            } || (parts.size >= 2 && KNOWN_TLDS.contains("${parts[parts.size-2]}.${parts.last()}".lowercase()))

            if (hasTld) {
                candidate = cleanUrlTail(candidate)
                candidate = balanceParentheses(candidate)

                if (candidate.isNotEmpty() && candidate.contains(".")) {
                    urls.add(candidate)
                }
            }
        }

        if (urls.isNotEmpty()) {
            Log.d(TAG, "URL 추출 성공: $urls")
        }

        return urls.toList()
    }

    private fun cleanUrlTail(url: String): String {
        var cleaned = url
        val trailingChars = setOf('.', ',', '!', '?', ';', '"', '\'', '。', '，', '！')

        while (cleaned.isNotEmpty() && cleaned.last() in trailingChars) {
            cleaned = cleaned.dropLast(1)
        }

        return cleaned
    }

    private fun balanceParentheses(url: String): String {
        var cleaned = url

        while (cleaned.isNotEmpty()) {
            val openCount = cleaned.count { it == '(' }
            val closeCount = cleaned.count { it == ')' }
            if (closeCount > openCount && cleaned.endsWith(')')) {
                cleaned = cleaned.dropLast(1)
            } else {
                break
            }
        }

        while (cleaned.isNotEmpty()) {
            val openCount = cleaned.count { it == '[' }
            val closeCount = cleaned.count { it == ']' }
            if (closeCount > openCount && cleaned.endsWith(']')) {
                cleaned = cleaned.dropLast(1)
            } else {
                break
            }
        }

        return cleaned
    }

    private suspend fun analyzeUrls(urls: List<String>, sender: String, messageBody: String, type: String) {
        try {
            val urlAnalyzer = UrlAnalyzer()
            val database = PhishingDatabase.getDatabase(context)
            val dao = database.phishingAlertDao()

        var mostDangerousUrl = ""
        var highestRiskScore = 0f
        val dangerousUrls = mutableListOf<Pair<String, Float>>()

        for (url in urls) {
            val riskScore = urlAnalyzer.analyzeUrlPattern(url, messageBody)
            Log.d(TAG, "[$type] URL: $url, 위험도: $riskScore")

            if (riskScore > 0.2f) {
                dangerousUrls.add(Pair(url, riskScore))
                if (riskScore > highestRiskScore) {
                    highestRiskScore = riskScore
                    mostDangerousUrl = url
                }
            }
        }

        if (dangerousUrls.isNotEmpty()) {
            Log.d(TAG, "[$type] ${dangerousUrls.size}개 위험 URL 발견")

            for ((url, riskScore) in dangerousUrls) {
                try {
                    val urlRiskLevel = when {
                        riskScore > 0.7f -> "매우 위험"
                        riskScore > 0.5f -> "위험"
                        else -> "의심"
                    }

                    val alert = PhishingAlert(
                        timestamp = Date(),
                        sender = sender,
                        messageBody = messageBody,
                        detectedUrl = url,
                        riskScore = riskScore,
                        riskLevel = urlRiskLevel
                    )

                    dao.insertAlert(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "DB 저장 실패: ${e.message}")
                }
            }

            NotificationHelper.showPhishingAlert(
                context,
                if (dangerousUrls.size == 1) mostDangerousUrl else "위험한 링크 ${dangerousUrls.size}개 감지",
                sender,
                highestRiskScore
            )

            Log.d(TAG, "[$type] 알림 발송 완료!")
        }
        } catch (e: Exception) {
            Log.e(TAG, "[$type] URL 분석 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    fun register() {
        try {
            // SMS URI 감시
            context.contentResolver.registerContentObserver(
                SMS_URI,
                true,
                this
            )
            Log.d(TAG, "SMS ContentObserver 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "SMS ContentObserver 등록 실패: ${e.message}")
        }

        try {
            // MMS URI 감시
            context.contentResolver.registerContentObserver(
                MMS_URI,
                true,
                this
            )
            Log.d(TAG, "MMS ContentObserver 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "MMS ContentObserver 등록 실패: ${e.message}")
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
            Log.d(TAG, "ContentObserver 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "ContentObserver 해제 실패: ${e.message}")
        }
    }
}
