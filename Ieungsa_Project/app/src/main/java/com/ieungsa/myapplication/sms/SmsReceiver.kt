package com.ieungsa.myapplication.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.ieungsa.myapplication.AlertManager

/**
 * SMS 수신 BroadcastReceiver
 * - 수신된 SMS를 실시간으로 분석
 * - 피싱 메시지 탐지 시 알림
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "SMS 수신됨")

        try {
            // SMS 메시지 추출
            val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else {
                // Android 4.3 이하 (구식 방법)
                extractMessagesLegacy(intent)
            }

            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "메시지를 추출할 수 없습니다")
                return
            }

            // 메시지 병합 (여러 부분으로 나뉜 SMS)
            val sender = messages[0].originatingAddress ?: "알 수 없음"
            val messageBody = messages.joinToString("") { it.messageBody ?: "" }

            Log.d(TAG, "발신자: $sender, 메시지: ${messageBody.take(50)}...")

            // SMS 분석
            val analyzer = SmsAnalyzer()
            val result = analyzer.analyze(sender, messageBody)

            // 피싱 메시지 감지 시 알림
            if (result.isPhishing) {
                Log.w(TAG, "⚠️ 피싱 SMS 감지! 위험도: ${result.riskLevel}")

                AlertManager.showSmishingWarning(
                    context = context,
                    sender = sender,
                    message = messageBody,
                    riskLevel = result.riskLevel,
                    reasons = result.reasons,
                    detectedUrls = result.detectedUrls
                )
            } else {
                Log.d(TAG, "✅ 안전한 메시지")
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 실패: ${e.message}", e)
        }
    }

    /**
     * Android 4.3 이하용 메시지 추출 (레거시)
     */
    @Suppress("DEPRECATION")
    private fun extractMessagesLegacy(intent: Intent): Array<SmsMessage>? {
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return null
        return pdus.map { pdu ->
            SmsMessage.createFromPdu(pdu as ByteArray)
        }.toTypedArray()
    }
}
