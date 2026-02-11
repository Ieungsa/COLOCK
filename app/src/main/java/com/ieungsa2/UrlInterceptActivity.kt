package com.ieungsa2

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ieungsa2.database.PhishingAlert
import com.ieungsa2.database.PhishingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class UrlInterceptActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UrlInterceptActivity"
    }

    private lateinit var targetUrl: String
    private val urlAnalyzer = UrlAnalyzer()
    private lateinit var loadingUrlText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_intercept)

        Log.d(TAG, "🚀 [[URL_INTERCEPT]] 액티비티 시작됨!")
        Log.d(TAG, "🚀 [[URL_INTERCEPT]] Intent Action: ${intent.action}")
        Log.d(TAG, "🚀 [[URL_INTERCEPT]] Data: ${intent.dataString}")

        loadingUrlText = findViewById(R.id.loading_url_text)

        // URL 추출
        targetUrl = intent.dataString ?: ""

        if (targetUrl.isEmpty()) {
            Log.w(TAG, "⚠️ [[URL_INTERCEPT]] URL이 비어있어 종료합니다.")
            finish()
            return
        }

        Log.d(TAG, "✅ [[URL_INTERCEPT]] 클릭 감지된 URL: $targetUrl")

        // 로딩 화면에 URL 표시
        loadingUrlText.text = targetUrl

        // 백그라운드에서 URL 분석
        CoroutineScope(Dispatchers.IO).launch {
            analyzeAndDecide()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private suspend fun analyzeAndDecide() {
        try {
            val riskScore = urlAnalyzer.analyzeUrlPattern(targetUrl)

            Log.d(TAG, "URL: $targetUrl, 위험도: $riskScore")

            runOnUiThread {
                if (riskScore > 0.3f) {
                    // 위험한 링크 - 차단 다이얼로그 표시
                    showBlockingDialog(riskScore)
                } else {
                    // 안전한 링크 - 바로 열기
                    openUrlDirectly()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "URL 분석 오류: ${e.message}")
            runOnUiThread {
                showAnalysisErrorDialog()
            }
        }
    }

    private fun showBlockingDialog(riskScore: Float) {
        val riskLevel = when {
            riskScore > 0.7f -> "매우 위험"
            riskScore > 0.5f -> "위험"
            else -> "의심"
        }

        AlertDialog.Builder(this)
            .setTitle("🚫 링크 차단됨")
            .setMessage(
                "이 링크는 ${riskLevel}한 피싱 링크로 판단됩니다.\n\n" +
                "URL: $targetUrl\n" +
                "위험도: ${(riskScore * 100).toInt()}%\n\n" +
                "접속을 권장하지 않습니다."
            )
            .setPositiveButton("차단하기") { _, _ ->
                // 차단 기록을 DB에 저장
                saveBlockedAlert(riskScore)
                finish()
            }
            .setNegativeButton("강제 열기") { _, _ ->
                openUrlDirectly()
            }
            .setOnCancelListener {
                // 뒤로가기 버튼으로 취소
                finish()
            }
            .setCancelable(true)
            .show()
    }

    private fun showAnalysisErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 분석 오류")
            .setMessage("링크 안전성을 분석할 수 없습니다.\n\nURL: $targetUrl\n\n어떻게 하시겠습니까?")
            .setPositiveButton("취소") { _, _ ->
                finish()
            }
            .setNegativeButton("열기") { _, _ ->
                openUrlDirectly()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * URL을 다른 앱(브라우저)으로 열기.
     * COLOCK 자신을 후보에서 제외하여 무한 루프를 방지합니다.
     */
    private fun openUrlDirectly() {
        try {
            val uri = Uri.parse(targetUrl)

            // 우선순위 브라우저 목록 (명시적으로 패키지명 지정)
            val preferredBrowsers = listOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "com.sec.android.app.sbrowser.beta",
                "org.mozilla.firefox",
                "com.microsoft.emmx",
                "com.opera.browser",
                "com.brave.browser"
            )

            // 설치된 브라우저를 찾아서 직접 실행
            var browserOpened = false
            for (browserPackage in preferredBrowsers) {
                try {
                    // 해당 브라우저가 설치되어 있는지 확인
                    packageManager.getPackageInfo(browserPackage, 0)

                    // 설치되어 있으면 해당 브라우저로 직접 열기
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(browserPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    startActivity(browserIntent)
                    Log.d(TAG, "브라우저로 열기 성공: $browserPackage")
                    browserOpened = true
                    break

                } catch (e: Exception) {
                    Log.d(TAG, "브라우저 $browserPackage 시도 실패: ${e.message}")
                    // 이 브라우저는 없거나 실행 불가, 다음 브라우저 시도
                    continue
                }
            }

            if (!browserOpened) {
                // 알려진 브라우저가 없으면 시스템 선택기 표시
                Log.w(TAG, "알려진 브라우저를 찾을 수 없음, 선택기 표시")

                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // 선택기를 통해 사용자가 직접 선택하도록
                val chooserIntent = Intent.createChooser(viewIntent, "브라우저 선택")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    startActivity(chooserIntent)
                    browserOpened = true
                } catch (e: Exception) {
                    Log.e(TAG, "선택기 표시 실패: ${e.message}")
                }
            }

            if (!browserOpened) {
                // 모든 시도가 실패
                AlertDialog.Builder(this)
                    .setTitle("오류")
                    .setMessage("링크를 열 수 있는 브라우저가 설치되어 있지 않습니다.\n\nChrome 또는 다른 브라우저를 설치해주세요.")
                    .setPositiveButton("확인") { _, _ -> finish() }
                    .show()
                return
            }

            finish()

        } catch (e: Exception) {
            Log.e(TAG, "URL 열기 실패: ${e.message}", e)

            AlertDialog.Builder(this)
                .setTitle("오류")
                .setMessage("링크를 열 수 없습니다.\n오류: ${e.message}\n\n브라우저 앱이 설치되어 있는지 확인해주세요.")
                .setPositiveButton("확인") { _, _ -> finish() }
                .show()
        }
    }

    /**
     * 차단된 URL을 PhishingAlert DB에 저장
     */
    private fun saveBlockedAlert(riskScore: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val riskLevel = when {
                    riskScore > 0.7f -> "매우 위험"
                    riskScore > 0.5f -> "위험"
                    else -> "의심"
                }

                val alert = PhishingAlert(
                    timestamp = Date(),
                    sender = "[URL 클릭 차단]",
                    messageBody = "사용자가 직접 클릭한 링크가 차단되었습니다.",
                    detectedUrl = targetUrl,
                    riskScore = riskScore,
                    riskLevel = riskLevel
                )

                val database = PhishingDatabase.getDatabase(this@UrlInterceptActivity)
                database.phishingAlertDao().insertAlert(alert)

                Log.d(TAG, "차단 기록 저장 완료: $targetUrl")
            } catch (e: Exception) {
                Log.e(TAG, "차단 기록 저장 실패: ${e.message}")
            }
        }
    }

}
