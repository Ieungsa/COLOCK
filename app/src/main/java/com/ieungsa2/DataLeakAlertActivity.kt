package com.ieungsa2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class DataLeakAlertActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_leak_alert)

        val appName = intent.getStringExtra("app_name") ?: "알 수 없는 앱"
        val packageName = intent.getStringExtra("package_name") ?: ""
        val usageMB = intent.getDoubleExtra("usage_mb", 0.0)

        val titleText = findViewById<TextView>(R.id.alert_title)
        val messageText = findViewById<TextView>(R.id.alert_message)
        val usageText = findViewById<TextView>(R.id.alert_usage)
        val stopButton = findViewById<Button>(R.id.stop_app_button)
        val closeButton = findViewById<Button>(R.id.close_alert_button)

        titleText.text = "🚨 데이터 유출 경고!"
        messageText.text = "$appName 앱이 화면 OFF 상태에서\n과도한 데이터를 사용했습니다."
        usageText.text = "사용량: ${"%.2f".format(usageMB)}MB"

        stopButton.setOnClickListener {
            // 서비스에 사이렌 중지 요청
            sendBroadcast(Intent("com.ieungsa2.STOP_SIREN"))

            // 앱 정보 화면으로 이동
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            finish()
        }

        closeButton.setOnClickListener {
            // 서비스에 사이렌 중지 요청
            sendBroadcast(Intent("com.ieungsa2.STOP_SIREN"))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 뒤로가기 막기 (강제로 확인하도록)
        // super.onBackPressed()
    }
}
