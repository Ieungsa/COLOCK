package com.ieungsa2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        
        // 토큰이 갱신되면 Firestore에 즉시 업데이트
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedPhone = sharedPref.getString("user_phone", null)
        
        if (!savedPhone.isNullOrEmpty()) {
            val repository = VerificationRepository()
            repository.updateMyToken(savedPhone, token)
            Log.d(TAG, "토큰 자동 갱신 완료: $savedPhone")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // 데이터 메시지가 포함되어 있는지 확인
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleNow(remoteMessage.data)
        }

        // 알림 메시지가 포함되어 있는지 확인
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body)
        }
    }

    private fun handleNow(data: Map<String, String>) {
        val type = data["type"]
        if (type == "VERIFICATION_REQUEST") {
            val requesterName = data["requesterName"] ?: "사용자"
            val requestId = data["requestId"] ?: return
            
            // 여기서 FullscreenAlertActivity 등을 띄워 승인을 요청합니다.
            // 지금은 단순 알림으로 처리하지만, 실제로는 Overlay 팝업을 띄우는 것이 좋습니다.
            showVerificationPopup(requesterName, requestId)
        }
    }

    private fun showVerificationPopup(requesterName: String, requestId: String) {
        val intent = Intent(this, FullscreenAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("ALERT_TYPE", "VERIFICATION")
            putExtra("REQUESTER_NAME", requesterName)
            putExtra("REQUEST_ID", requestId)
        }
        startActivity(intent)
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "smishing_guard_verification"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // 아이콘 교체 필요
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pendingIntent, true) // 중요! 헤드업 알림
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "User Verification",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
