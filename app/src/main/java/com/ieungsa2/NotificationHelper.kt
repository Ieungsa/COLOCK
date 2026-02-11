package com.ieungsa2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat

/**
 * Helper class to manage system notifications and channels.
 */
object NotificationHelper {

    private const val URGENT_CHANNEL_ID = "phishing_urgent_alerts"
    private const val SERVICE_CHANNEL_ID = "phishing_service"
    private const val SHORTCUT_ID = "smishing_warning_shortcut"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val urgentChannel = NotificationChannel(URGENT_CHANNEL_ID, "Urgent Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Immediate notification for detected threats"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(urgentChannel)

            val serviceChannel = NotificationChannel(SERVICE_CHANNEL_ID, "Protection Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background protection status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }
    
    fun showPhishingAlert(context: Context, url: String, sender: String, riskScore: Float) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("detected_url", url)
            putExtra("sender", sender)
            putExtra("risk_score", riskScore)
            putExtra("emergency_alert", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, URGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Security Alert")
            .setContentText("Suspicious activity detected from $sender")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) { }
    }

    fun getServiceNotification(context: Context, title: String, text: String): android.app.Notification {
        createNotificationChannel(context)
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
