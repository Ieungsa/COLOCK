package com.ieungsa2

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ieungsa2.database.PhishingAlert
import com.ieungsa2.database.PhishingDatabase
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.Date
import java.util.regex.Pattern

/**
 * MMS 메시지를 감지하는 ContentObserver
 * SMS는 SmsReceiver가 처리하므로, 여기서는 MMS만 처리합니다.
 */
class MmsObserver(context: Context) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "MmsObserver"
        private val MMS_URI = Uri.parse("content://mms")
    }

    // Context를 WeakReference로 보관하여 메모리 누수 방지
    private val contextRef = WeakReference(context)

    // 마지막으로 처리한 MMS ID (중복 처리 방지)
    private var lastProcessedMmsId: Long = -1

    // 코루틴 관리
    private val job = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "코루틴 예외 발생: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.IO + job + exceptionHandler)

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Log.d(TAG, "MMS 변경 감지: uri=$uri")

        scope.launch {
            try {
                checkLatestMms()
            } catch (e: Exception) {
                Log.e(TAG, "MMS 확인 중 오류: ${e.message}")
            }
        }
    }

    private suspend fun checkLatestMms() {
        val context = contextRef.get() ?: run {
            Log.e(TAG, "Context가 null입니다")
            return
        }

        var cursor: android.database.Cursor? = null
        try {
            cursor = context.contentResolver.query(
                MMS_URI,
                arrayOf("_id", "date", "msg_box"),
                "msg_box = 1", // 1 = 받은 메시지
                null,
                "date DESC LIMIT 1"
            )

            if (cursor == null || !cursor.moveToFirst()) {
                return
            }

            val idIndex = cursor.getColumnIndex("_id")
            val dateIndex = cursor.getColumnIndex("date")

            if (idIndex < 0 || dateIndex < 0) {
                Log.e(TAG, "MMS 컬럼 인덱스 없음")
                return
            }

            val id = cursor.getLong(idIndex)

            // 이미 처리한 메시지면 스킵
            if (id == lastProcessedMmsId) {
                return
            }

            val date = cursor.getLong(dateIndex) * 1000 // MMS date는 초 단위

            // 최근 60초 이내 메시지만 처리
            val timeDiff = System.currentTimeMillis() - date
            if (timeDiff > 60000) {
                lastProcessedMmsId = id
                return
            }

            lastProcessedMmsId = id

            // Increment scanned messages count
            incrementScannedCount(context)

            // MMS 본문과 발신자 읽기
            val body = getMmsText(context, id)
            val sender = getMmsAddress(context, id)

            Log.d(TAG, "새 MMS 감지: ID=$id, 발신자=$sender, 길이=${body.length}")

            if (body.isNotEmpty()) {
                processMessage(context, sender, body)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "MMS 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "MMS 확인 오류: ${e.message}")
        } finally {
            try {
                cursor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "커서 닫기 오류: ${e.message}")
            }
        }
    }

    private fun incrementScannedCount(context: Context) {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt("scanned_messages_count", 0)
        sharedPref.edit().putInt("scanned_messages_count", currentCount + 1).apply()
    }

    private fun getMmsText(context: Context, mmsId: Long): String {
        val builder = StringBuilder()
        var cursor: android.database.Cursor? = null

        try {
            val uri = Uri.parse("content://mms/part")
            cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "ct", "text"),
                "mid = ?",
                arrayOf(mmsId.toString()),
                null
            )

            if (cursor == null) return ""

            val ctIndex = cursor.getColumnIndex("ct")
            val textIndex = cursor.getColumnIndex("text")
            val idIndex = cursor.getColumnIndex("_id")

            while (cursor.moveToNext()) {
                val contentType = if (ctIndex >= 0) cursor.getString(ctIndex) ?: "" else ""

                if (contentType == "text/plain") {
                    val text = if (textIndex >= 0) cursor.getString(textIndex) else null
                    if (text != null) {
                        builder.append(text)
                    } else if (idIndex >= 0) {
                        // text가 null이면 _data에서 읽어야 함
                        try {
                            val partId = cursor.getLong(idIndex)
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
        } catch (e: Exception) {
            Log.e(TAG, "MMS 텍스트 읽기 오류: ${e.message}")
        } finally {
            try {
                cursor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "커서 닫기 오류: ${e.message}")
            }
        }

        return builder.toString()
    }

    private fun getMmsAddress(context: Context, mmsId: Long): String {
        var cursor: android.database.Cursor? = null

        try {
            val uri = Uri.parse("content://mms/$mmsId/addr")
            cursor = context.contentResolver.query(
                uri,
                arrayOf("address", "type"),
                "type = 137", // 137 = FROM
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val addressIndex = cursor.getColumnIndex("address")
                if (addressIndex >= 0) {
                    return cursor.getString(addressIndex) ?: "알 수 없음"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 주소 읽기 오류: ${e.message}")
        } finally {
            try {
                cursor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "커서 닫기 오류: ${e.message}")
            }
        }

        return "알 수 없음"
    }

    private suspend fun processMessage(context: Context, sender: String, body: String) {
        Log.d(TAG, "[MMS] 메시지 처리 시작: 발신자=$sender, 길이=${body.length}")

        val urls = extractUrls(body)

        if (urls.isEmpty()) {
            Log.d(TAG, "[MMS] URL 없음")
            return
        }

        Log.d(TAG, "[MMS] ${urls.size}개 URL 발견: $urls")

        analyzeUrls(context, urls, sender, body)
    }

    // 알려진 TLD 목록 (프로토콜 없는 URL 감지용)
    private val KNOWN_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "info", "biz", "co", "io", "me", "tv", "cc",
        "kr", "co.kr", "go.kr", "ac.kr", "or.kr",
        "us", "uk", "jp", "cn", "de", "fr", "ru", "ca", "au",
        "xyz", "tk", "ml", "cf", "ga", "pw", "top", "click", "loan", "win", "buzz",
        "site", "online", "store", "shop", "club", "live", "pro", "im"
    )

    /**
     * 텍스트에서 URL을 추출하는 함수.
     */
    private fun extractUrls(text: String): List<String> {
        val urls = mutableSetOf<String>()
        var modifiedText = text

        // 1. http:// 또는 www로 시작하는 URL 먼저 추출하고, 해당 부분을 공백으로 치환
        val urlPattern = Pattern.compile(
            "(?:https?://|www\\.)[^\\s가-힣]+",
            Pattern.CASE_INSENSITIVE
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
        val domainPattern = Pattern.compile(
            "([a-zA-Z0-9][-a-zA-Z0-9]*\\.)+([a-zA-Z]{2,})[^\\s가-힣]*", // 더 관대한 정규식으로 통일
            Pattern.CASE_INSENSITIVE
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

        // 닫는 괄호가 더 많으면 제거
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

        // 여는 괄호가 더 많으면 제거
        while (cleaned.isNotEmpty()) {
            val openCount = cleaned.count { it == '(' }
            val closeCount = cleaned.count { it == ')' }
            if (openCount > closeCount && cleaned.endsWith('(')) {
                cleaned = cleaned.dropLast(1)
            } else {
                break
            }
        }

        while (cleaned.isNotEmpty()) {
            val openCount = cleaned.count { it == '[' }
            val closeCount = cleaned.count { it == ']' }
            if (openCount > closeCount && cleaned.endsWith('[')) {
                cleaned = cleaned.dropLast(1)
            } else {
                break
            }
        }

        return cleaned
    }

    private suspend fun analyzeUrls(context: Context, urls: List<String>, sender: String, messageBody: String) {
        try {
            val urlAnalyzer = UrlAnalyzer()
            val database = PhishingDatabase.getDatabase(context)
            val dao = database.phishingAlertDao()

            var mostDangerousUrl = ""
            var highestRiskScore = 0f
            val dangerousUrls = mutableListOf<Pair<String, Float>>()

            for (url in urls) {
                val riskScore = urlAnalyzer.analyzeUrlPattern(url, messageBody)
                Log.d(TAG, "[MMS] URL: $url, 위험도: $riskScore")

                if (riskScore > 0.2f) {
                    dangerousUrls.add(Pair(url, riskScore))
                    if (riskScore > highestRiskScore) {
                        highestRiskScore = riskScore
                        mostDangerousUrl = url
                    }
                }
            }

            if (dangerousUrls.isNotEmpty()) {
                Log.d(TAG, "[MMS] ${dangerousUrls.size}개 위험 URL 발견")

                try {
                    // 위험 등급은 가장 높은 점수 기준
                    val riskLevel = when {
                        highestRiskScore > 0.7f -> "매우 위험"
                        highestRiskScore > 0.5f -> "위험"
                        else -> "의심"
                    }

                    // 여러 URL을 줄바꿈으로 구분하여 하나의 문자열로 저장
                    val allDangerousUrls = dangerousUrls.joinToString("\n") { it.first }

                    val alert = PhishingAlert(
                        timestamp = Date(),
                        sender = sender,
                        messageBody = messageBody,
                        detectedUrl = allDangerousUrls,
                        riskScore = highestRiskScore,
                        riskLevel = riskLevel
                    )

                    dao.insertAlert(alert)
                    Log.d(TAG, "[MMS] 위험 문자 저장 성공: URL ${dangerousUrls.size}개")
                } catch (e: Exception) {
                    Log.e(TAG, "DB 저장 실패: ${e.message}")
                }

                // 문자 알림이 먼저 표시되도록 1.5초 대기 후 위험 알림 표시
                kotlinx.coroutines.delay(1500L)

                // 알림은 메인 스레드에서 실행
                withContext(Dispatchers.Main) {
                    try {
                        NotificationHelper.showPhishingAlert(
                            context,
                            if (dangerousUrls.size == 1) mostDangerousUrl else "위험한 링크 ${dangerousUrls.size}개 감지",
                            sender,
                            highestRiskScore
                        )
                        Log.d(TAG, "[MMS] 알림 발송 완료!")
                    } catch (e: Exception) {
                        Log.e(TAG, "알림 발송 실패: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[MMS] URL 분석 오류: ${e.message}")
        }
    }

    fun register() {
        val context = contextRef.get() ?: run {
            Log.e(TAG, "Context가 null이라 등록 실패")
            return
        }

        try {
            context.contentResolver.registerContentObserver(
                MMS_URI,
                true,
                this
            )
            Log.d(TAG, "MMS Observer 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "MMS Observer 등록 실패: ${e.message}")
        }
    }

    fun unregister() {
        val context = contextRef.get()

        try {
            context?.contentResolver?.unregisterContentObserver(this)
            Log.d(TAG, "MMS Observer 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "MMS Observer 해제 실패: ${e.message}")
        }

        // 코루틴 정리
        job.cancel()
    }
}
