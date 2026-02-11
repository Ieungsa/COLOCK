package com.ieungsa2

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FullscreenAlertActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 잠금화면 위로 표시 및 화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        setContentView(R.layout.activity_fullscreen_alert)

        val titleView: TextView = findViewById(R.id.alert_title)
        val messageView: TextView = findViewById(R.id.alert_message)
        val urlView: TextView = findViewById(R.id.alert_url)
        val closeButton: Button = findViewById(R.id.close_button)
        val verificationButtons: android.view.View = findViewById(R.id.verification_buttons)
        val approveButton: Button = findViewById(R.id.approve_button)
        val rejectButton: Button = findViewById(R.id.reject_button)

        val alertType = intent.getStringExtra("ALERT_TYPE") ?: "PHISHING"
        
        if (alertType == "VERIFICATION") {
            val requesterName = intent.getStringExtra("REQUESTER_NAME") ?: "사용자"
            val requestId = intent.getStringExtra("REQUEST_ID") ?: ""
            
            titleView.text = "🛡️ 본인 확인 요청"
            titleView.setTextColor(android.graphics.Color.parseColor("#3B82F6")) // 파란색으로 변경
            messageView.text = "'$requesterName' 님이 본인 확인 인증을 요청했습니다.\n실제 본인이 맞으신가요?"
            urlView.visibility = android.view.View.GONE
            closeButton.visibility = android.view.View.GONE
            verificationButtons.visibility = android.view.View.VISIBLE

            val repository = VerificationRepository()
            
            approveButton.setOnClickListener {
                repository.approveRequest(requestId, true)
                finish()
            }
            
            rejectButton.setOnClickListener {
                repository.approveRequest(requestId, false)
                finish()
            }
        } else {
            // 기존 피싱 경고 로직
            val sender = intent.getStringExtra("sender") ?: "알 수 없는 발신자"
            val url = intent.getStringExtra("detected_url") ?: "URL 정보 없음"
            
            messageView.text = "'$sender'(으)로부터 스미싱으로 의심되는 위험한 링크가 포함된 문자가 감지되었습니다."
            urlView.text = url
            
            closeButton.setOnClickListener {
                finish()
            }
        }
    }
}
