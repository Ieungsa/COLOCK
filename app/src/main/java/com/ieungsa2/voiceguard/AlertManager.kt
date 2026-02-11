package com.ieungsa2.voiceguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

object AlertManager {

    private const val TAG = "AlertManager"
    private const val ALERT_CHANNEL_ID = "VoiceGuardAlertChannel"
    private const val ALERT_NOTIFICATION_ID = 2001

    private var lastAlertTime = 0L
    private const val ALERT_COOLDOWN = 10000L // 10초

    fun showWarning(
        context: Context,
        confidence: Float,
        details: String = ""
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) {
            return
        }
        lastAlertTime = currentTime

        Log.w(TAG, "⚠️ 변조 음성 경고 알림 표시")

        createAlertChannel(context)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 보이스피싱 경고")
            .setContentText("피싱 의심 내용이 감지되었습니다!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("피싱 의심 내용이 감지되었습니다!\n\n" +
                        if (details.isNotEmpty()) "$details\n\n" else "" +
                        "통화 내용을 주의하세요."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFFFF0000.toInt())
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)

        triggerVibration(context)
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "진동 실패: ${e.message}")
        }
    }
}