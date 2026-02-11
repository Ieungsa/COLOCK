package com.ieungsa.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 알림 관리자
 * - 변조 음성 탐지 시 알림 표시
 * - 진동 + 소리
 */
object AlertManager {

    private const val TAG = "AlertManager"
    private const val ALERT_CHANNEL_ID = "VoiceGuardAlertChannel"
    private const val SCAM_CHANNEL_ID = "VoiceGuardScamChannel"
    private const val SMISHING_CHANNEL_ID = "VoiceGuardSmishingChannel"
    private const val TTS_CHANNEL_ID = "VoiceGuardTTSChannel"
    private const val ALERT_NOTIFICATION_ID = 2001
    private const val SCAM_NOTIFICATION_ID = 2002
    private const val SMISHING_NOTIFICATION_ID = 2003
    private const val TTS_NOTIFICATION_ID = 2004

    // 마지막 알림 시간 (중복 방지)
    private var lastAlertTime = 0L
    private var lastScamAlertTime = 0L
    private var lastSmishingAlertTime = 0L
    private var lastTTSAlertTime = 0L
    private const val ALERT_COOLDOWN = 10000L // 10초

    /**
     * 경고 알림 표시
     */
    fun showWarning(
        context: Context,
        confidence: Float,
        details: String = ""
    ) {
        // 중복 알림 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) {
            Log.d(TAG, "알림 쿨다운 중... (${(ALERT_COOLDOWN - (currentTime - lastAlertTime)) / 1000}초 남음)")
            return
        }
        lastAlertTime = currentTime

        Log.w(TAG, "⚠️ 변조 음성 경고 알림 표시 (신뢰도: ${(confidence * 100).toInt()}%)")

        // Notification Channel 생성 (최초 1회)
        createAlertChannel(context)

        // 알림 생성
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 보이스피싱 경고")
            .setContentText("변조된 목소리가 감지되었습니다!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("변조된 목소리가 감지되었습니다!\n\n" +
                        "신뢰도: ${(confidence * 100).toInt()}%\n" +
                        if (details.isNotEmpty()) "$details\n\n" else "" +
                        "통화 내용을 주의하세요."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFFFF0000.toInt()) // 빨간색
            .build()

        // 알림 표시
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)

        // 진동
        triggerVibration(context)
    }

    /**
     * TTS(기계음) 경고 알림
     */
    fun showTTSWarning(context: Context) {
        // 중복 알림 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTTSAlertTime < ALERT_COOLDOWN) {
            Log.d(TAG, "TTS 알림 쿨다운 중...")
            return
        }
        lastTTSAlertTime = currentTime

        Log.w(TAG, "⚠️ TTS(기계음) 경고 알림 표시")

        // Notification Channel 생성
        createTTSAlertChannel(context)

        val notification = NotificationCompat.Builder(context, TTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ℹ️ 기계음(TTS) 감지")
            .setContentText("상대방의 목소리가 기계음으로 의심됩니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFF29B6F6.toInt()) // 파란색
            .build()

        // 알림 표시
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TTS_NOTIFICATION_ID, notification)

        // 진동
        triggerVibration(context) // 일반 진동 재사용
    }


    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 이미 존재하면 생략
            if (notificationManager.getNotificationChannel(ALERT_CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "VoiceGuard 경고 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "보이스피싱 변조 음성 탐지 시 긴급 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "알림 채널 생성 완료")
        }
    }

    /**
     * TTS 알림 채널 생성
     */
    private fun createTTSAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(TTS_CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                TTS_CHANNEL_ID,
                "VoiceGuard TTS 탐지 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "기계음(TTS) 감지 시 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "TTS 알림 채널 생성 완료")
        }
    }

    /**
     * 진동 트리거
     */
    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                // Android 11 이하
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+: VibrationEffect 사용
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                // Android 7.1 이하
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }

            Log.d(TAG, "진동 트리거 완료")

        } catch (e: Exception) {
            Log.e(TAG, "진동 실패: ${e.message}")
        }
    }

    /**
     * 스캠 전화번호 경고 알림
     */
    fun showScamNumberWarning(
        context: Context,
        phoneNumber: String,
        riskLevel: Int,
        category: String,
        description: String,
        reportCount: Int
    ) {
        // 중복 알림 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScamAlertTime < ALERT_COOLDOWN) {
            Log.d(TAG, "스캠 알림 쿨다운 중...")
            return
        }
        lastScamAlertTime = currentTime

        Log.w(TAG, "⚠️ 스캠 전화번호 경고 알림 표시: $phoneNumber")

        // Notification Channel 생성
        createScamAlertChannel(context)

        // 위험도에 따른 색상
        val color = when (riskLevel) {
            3 -> 0xFFEF5350.toInt() // 빨강
            2 -> 0xFFFFA726.toInt() // 주황
            else -> 0xFFFFEE58.toInt() // 노랑
        }

        val riskText = when (riskLevel) {
            3 -> "⛔ 높은 위험"
            2 -> "⚠️ 중간 위험"
            else -> "⚡ 낮은 위험"
        }

        // 알림 생성
        val notification = NotificationCompat.Builder(context, SCAM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 스캠 전화번호 차단")
            .setContentText("$phoneNumber - $riskText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("전화번호: $phoneNumber\n\n" +
                        "$riskText\n" +
                        "카테고리: $category\n" +
                        "신고 횟수: ${reportCount}회\n\n" +
                        "$description\n\n" +
                        "통화에 주의하세요!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(color)
            .build()

        // 알림 표시
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SCAM_NOTIFICATION_ID, notification)

        // 진동
        triggerScamVibration(context, riskLevel)
    }

    /**
     * 스캠 알림 채널 생성
     */
    private fun createScamAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(SCAM_CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                SCAM_CHANNEL_ID,
                "VoiceGuard 스캠 차단 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "스캠 전화번호 감지 시 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "스캠 알림 채널 생성 완료")
        }
    }

    /**
     * 스캠 번호 감지 시 진동
     */
    private fun triggerScamVibration(context: Context, riskLevel: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 위험도에 따른 진동 패턴
            val pattern = when (riskLevel) {
                3 -> longArrayOf(0, 400, 100, 400, 100, 400) // 높음: 3회 강하게
                2 -> longArrayOf(0, 300, 150, 300) // 중간: 2회
                else -> longArrayOf(0, 200) // 낮음: 1회
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = when (riskLevel) {
                    3 -> intArrayOf(0, 255, 0, 255, 0, 255)
                    2 -> intArrayOf(0, 200, 0, 200)
                    else -> intArrayOf(0, 150)
                }
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }

            Log.d(TAG, "스캠 번호 진동 트리거 완료 (위험도: $riskLevel)")

        } catch (e: Exception) {
            Log.e(TAG, "스캠 번호 진동 실패: ${e.message}")
        }
    }

    /**
     * 스미싱 경고 알림
     */
    fun showSmishingWarning(
        context: Context,
        sender: String,
        message: String,
        riskLevel: com.ieungsa.myapplication.sms.RiskLevel,
        reasons: List<String>,
        detectedUrls: List<String>
    ) {
        // 중복 알림 방지
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSmishingAlertTime < ALERT_COOLDOWN) {
            Log.d(TAG, "스미싱 알림 쿨다운 중...")
            return
        }
        lastSmishingAlertTime = currentTime

        Log.w(TAG, "⚠️ 스미싱 경고 알림 표시: $sender")

        // Notification Channel 생성
        createSmishingAlertChannel(context)

        // 위험도에 따른 색상
        val color = when (riskLevel) {
            com.ieungsa.myapplication.sms.RiskLevel.HIGH -> 0xFFEF5350.toInt()
            com.ieungsa.myapplication.sms.RiskLevel.MEDIUM -> 0xFFFFA726.toInt()
            com.ieungsa.myapplication.sms.RiskLevel.LOW -> 0xFFFFEE58.toInt()
            else -> 0xFF9E9E9E.toInt()
        }

        val riskText = when (riskLevel) {
            com.ieungsa.myapplication.sms.RiskLevel.HIGH -> "⛔ 높은 위험"
            com.ieungsa.myapplication.sms.RiskLevel.MEDIUM -> "⚠️ 중간 위험"
            com.ieungsa.myapplication.sms.RiskLevel.LOW -> "⚡ 낮은 위험"
            else -> "안전"
        }

        // 메시지 미리보기 (최대 50자)
        val messagePreview = if (message.length > 50) {
            message.take(50) + "..."
        } else {
            message
        }

        // 알림 생성
        val notificationText = buildString {
            appendLine("발신자: $sender")
            appendLine("$riskText")
            appendLine()
            if (reasons.isNotEmpty()) {
                appendLine("탐지 내용:")
                reasons.take(3).forEach { appendLine("- $it") }
            }
            if (detectedUrls.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 의심 링크 포함")
                appendLine("절대 클릭하지 마세요!")
            }
            appendLine()
            appendLine("메시지:")
            appendLine(messagePreview)
        }

        val notification = NotificationCompat.Builder(context, SMISHING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 스미싱 의심 메시지")
            .setContentText("$sender - $riskText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(color)
            .build()

        // 알림 표시
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SMISHING_NOTIFICATION_ID, notification)

        // 진동
        triggerSmishingVibration(context, riskLevel)
    }

    /**
     * 스미싱 알림 채널 생성
     */
    private fun createSmishingAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(SMISHING_CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                SMISHING_CHANNEL_ID,
                "VoiceGuard 스미싱 차단 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "스미싱 의심 메시지 감지 시 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "스미싱 알림 채널 생성 완료")
        }
    }

    /**
     * 스미싱 감지 시 진동
     */
    private fun triggerSmishingVibration(context: Context, riskLevel: com.ieungsa.myapplication.sms.RiskLevel) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 위험도에 따른 진동 패턴
            val pattern = when (riskLevel) {
                com.ieungsa.myapplication.sms.RiskLevel.HIGH -> longArrayOf(0, 500, 150, 500, 150, 500)
                com.ieungsa.myapplication.sms.RiskLevel.MEDIUM -> longArrayOf(0, 400, 200, 400)
                else -> longArrayOf(0, 300)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = when (riskLevel) {
                    com.ieungsa.myapplication.sms.RiskLevel.HIGH -> intArrayOf(0, 255, 0, 255, 0, 255)
                    com.ieungsa.myapplication.sms.RiskLevel.MEDIUM -> intArrayOf(0, 200, 0, 200)
                    else -> intArrayOf(0, 150)
                }
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }

            Log.d(TAG, "스미싱 진동 트리거 완료 (위험도: $riskLevel)")

        } catch (e: Exception) {
            Log.e(TAG, "스미싱 진동 실패: ${e.message}")
        }
    }

    /**
     * 테스트용 알림 표시
     */
    fun showTestAlert(context: Context) {
        showWarning(
            context = context,
            confidence = 0.95f,
            details = "테스트 알림입니다.\n실제 탐지 시 이와 같이 표시됩니다."
        )
    }
}
