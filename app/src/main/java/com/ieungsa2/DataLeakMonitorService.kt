package com.ieungsa2

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ieungsa2.database.DataLeakAlert
import com.ieungsa2.database.DataLeakDatabase
import com.ieungsa2.network.NetworkUsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class DataLeakMonitorService : Service() {

    companion object {
        private const val TAG = "DataLeakMonitorService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "DataLeakMonitorChannel"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var monitoringRunnable: Runnable
    private var allInstalledApps: List<AppInfo> = listOf()
    private val suspiciousAppsMap = mutableMapOf<String, SuspiciousAppInfo>() // packageName -> info
    private val alertHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    private val sirenStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.ieungsa2.STOP_SIREN") {
                Log.d(TAG, "사이렌 중지 브로드캐스트 수신")
                stopSirenSound()
            }
        }
    }

    data class SuspiciousAppInfo(
        val appName: String,
        val packageName: String,
        val usageMB: Double,
        var alertCount: Int = 0
    )

    data class AppInfo(val name: String, val packageName: String, val uid: Int)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DataLeakMonitorService 생성")
        createNotificationChannel()
        setupMonitoringRunnable()

        // 사이렌 중지 브로드캐스트 리시버 등록
        val filter = IntentFilter("com.ieungsa2.STOP_SIREN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sirenStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sirenStopReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DataLeakMonitorService 시작")

        // 포그라운드 서비스 시작
        val notification = createNotification("데이터 유출 감시 중...")
        startForeground(NOTIFICATION_ID, notification)

        // 모니터링 시작
        handler.post(monitoringRunnable)

        return START_STICKY
    }

    private fun setupMonitoringRunnable() {
        monitoringRunnable = Runnable {
            Log.d(TAG, "===== 백그라운드 데이터 사용량 모니터링 시작 =====")

            loadAllApps()
            val tracker = NetworkUsageTracker(this)

            val endTime = System.currentTimeMillis()

            // 금일 00시 00분 00초 계산
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis // 오늘 00시부터

            val threshold = getSharedPreferences("data_leak_settings", MODE_PRIVATE)
                .getInt("threshold_mb", 50) * 1024L * 1024L // MB to Bytes

            val whitelistPrefs = getSharedPreferences("data_leak_whitelist", MODE_PRIVATE)
            val whitelist = whitelistPrefs.getStringSet("packages", emptySet()) ?: emptySet()

            var suspiciousCount = 0
            var totalAppsChecked = 0
            val appsWithData = mutableListOf<String>()

            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            Log.w(TAG, "총 ${allInstalledApps.size}개 앱 검사 시작...")
            Log.w(TAG, "조회 기간: ${timeFormat.format(startTime)} ~ ${timeFormat.format(endTime)} (오늘 하루)")

            allInstalledApps.forEach { app ->
                totalAppsChecked++

                try {
                    val usage = tracker.queryNetworkUsage(app.uid, startTime, endTime)
                    val usageMB = usage / (1024.0 * 1024.0)

                    // 화이트리스트 앱 여부 확인
                    val isWhitelisted = whitelist.contains(app.packageName)

                    // 데이터 사용량이 있는 앱만 목록에 추가
                    if (usageMB > 0) {
                        val prefix = if (isWhitelisted) "✅ [제외]" else "📱"
                        appsWithData.add("$prefix ${app.name}: ${"%.2f".format(usageMB)}MB")
                    }

                    // 모든 앱을 WARNING 레벨로 출력 (첫 10개만)
                    if (totalAppsChecked <= 10) {
                        val prefix = if (isWhitelisted) "✅ [제외]" else "📱"
                        Log.w(TAG, "[샘플 $totalAppsChecked] $prefix ${app.name} (${app.packageName}): ${"%.2f".format(usageMB)}MB")
                    }

                    // 화이트리스트에 없고 임계값 초과한 앱만 경고
                    if (!isWhitelisted && usage > threshold) {
                        suspiciousCount++
                        Log.w(TAG, "⚠️ ${app.name} (${app.packageName}): ${"%.2f".format(usageMB)}MB 사용 (임계값 초과)")

                        val screenOff = !isScreenOn()

                        // DB에 기록 저장
                        saveToDatabase(app.name, app.packageName, usageMB, threshold / (1024 * 1024), screenOff)

                        // 화면 꺼져있을 때만 강력한 경고 시작
                        if (screenOff) {
                            Log.e(TAG, "🚨 화면 OFF 상태 - ${app.name} 반복 경고 시작")

                            // 의심 앱 목록에 추가 (이미 있으면 업데이트)
                            suspiciousAppsMap[app.packageName] = SuspiciousAppInfo(
                                appName = app.name,
                                packageName = app.packageName,
                                usageMB = usageMB,
                                alertCount = suspiciousAppsMap[app.packageName]?.alertCount ?: 0
                            )

                            // 전체화면 경고 표시
                            showFullscreenAlert(app.name, app.packageName, usageMB)
                        } else {
                            // 화면 켜져있을 때는 일반 알림만
                            Log.w(TAG, "화면 ON 상태 - ${app.name} 모니터링만 수행")
                            sendWarningNotification(app.name, usageMB)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "앱 ${app.name} 검사 중 오류: ${e.message}")
                }
            }

            // 데이터 사용량이 있는 앱 목록 출력
            if (appsWithData.isNotEmpty()) {
                Log.w(TAG, "========== 데이터 사용량이 있는 앱 (${appsWithData.size}개) ==========")
                appsWithData.take(20).forEach { appInfo ->
                    Log.w(TAG, appInfo)
                }
                if (appsWithData.size > 20) {
                    Log.w(TAG, "... 외 ${appsWithData.size - 20}개")
                }
            } else {
                Log.w(TAG, "⚠️ 데이터 사용량이 있는 앱이 없습니다. 네트워크 통계 접근 권한을 확인하세요!")
            }

            // 포그라운드 알림 업데이트
            val statusText = if (suspiciousCount > 0) {
                "⚠️ 의심 앱 ${suspiciousCount}개 감지"
            } else {
                "✅ 데이터 유출 감시 중..."
            }
            updateNotification(statusText)

            Log.w(TAG, "===== 모니터링 완료 =====")
            Log.w(TAG, "검사한 앱: ${totalAppsChecked}개 / 데이터 사용 앱: ${appsWithData.size}개 / 의심 앱: ${suspiciousCount}개")
            Log.w(TAG, "다음 실행: 30초 후")
            Log.w(TAG, "==========================")

            // 30초마다 반복
            handler.postDelayed(monitoringRunnable, 30 * 1000)
        }
    }

    private fun loadAllApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // 모든 앱을 포함 (런처 앱이 아니어도 백그라운드에서 실행될 수 있음)
        allInstalledApps = apps
            .filter { appInfo ->
                // 시스템 앱 중 일부는 제외할 수 있지만, 대부분 포함
                // 패키지명이 있는 모든 앱 포함
                !appInfo.packageName.isNullOrEmpty()
            }
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName, it.uid) }

        Log.d(TAG, "앱 목록 로드 완료: 총 ${allInstalledApps.size}개")
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    private fun saveToDatabase(appName: String, packageName: String, usageMB: Double, thresholdMB: Long, wasScreenOff: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DataLeakDatabase.getDatabase(applicationContext)
                val alert = DataLeakAlert(
                    timestamp = Date(),
                    appName = appName,
                    packageName = packageName,
                    usageMB = usageMB,
                    thresholdMB = thresholdMB.toInt(),
                    wasScreenOff = wasScreenOff,
                    isRead = false
                )
                db.dataLeakAlertDao().insert(alert)
                Log.d(TAG, "DB 저장 완료: $appName")
            } catch (e: Exception) {
                Log.e(TAG, "DB 저장 실패: ${e.message}")
            }
        }
    }

    private fun showFullscreenAlert(appName: String, packageName: String, usageMB: Double) {
        val intent = Intent(this, DataLeakAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("app_name", appName)
            putExtra("package_name", packageName)
            putExtra("usage_mb", usageMB)
        }
        startActivity(intent)

        // 사이렌 소리 재생
        playSirenSound()

        // 10초 간격으로 반복 알림 시작
        startRepeatingAlert(packageName)
    }

    private fun playSirenSound() {
        try {
            // 기존 재생 중지
            mediaPlayer?.release()
            mediaPlayer = null

            // 알람 소리 URI 가져오기
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }

                isLooping = true // 반복 재생
                prepare()
                start()
            }

            Log.d(TAG, "사이렌 소리 재생 시작")
        } catch (e: Exception) {
            Log.e(TAG, "사이렌 소리 재생 실패: ${e.message}")
        }
    }

    private fun stopSirenSound() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "사이렌 소리 중지")
        } catch (e: Exception) {
            Log.e(TAG, "사이렌 소리 중지 실패: ${e.message}")
        }
    }

    private fun startRepeatingAlert(packageName: String) {
        val runnable = object : Runnable {
            override fun run() {
                val appInfo = suspiciousAppsMap[packageName]
                if (appInfo != null && !isScreenOn()) {
                    appInfo.alertCount++
                    Log.w(TAG, "🚨 [${appInfo.alertCount}회] ${appInfo.appName} 반복 알림 발송")
                    sendCriticalNotification(appInfo.appName, appInfo.usageMB, appInfo.alertCount)

                    // 10초 후 다시 실행
                    alertHandler.postDelayed(this, 10 * 1000)
                } else {
                    // 화면이 켜지거나 앱이 목록에서 제거되면 중지
                    Log.d(TAG, "반복 알림 중지: ${appInfo?.appName ?: packageName}")
                    suspiciousAppsMap.remove(packageName)

                    // 사이렌 소리 중지
                    stopSirenSound()
                }
            }
        }
        alertHandler.postDelayed(runnable, 10 * 1000) // 첫 알림은 10초 후
    }

    private fun sendWarningNotification(appName: String, usageMB: Double) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ 의심스러운 데이터 사용 감지")
            .setContentText("$appName: ${"%.2f".format(usageMB)}MB 사용")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun sendCriticalNotification(appName: String, usageMB: Double, alertCount: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 앱 정보 화면으로 이동하는 인텐트
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${suspiciousAppsMap.values.find { it.appName == appName }?.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚨 데이터 유출 위험! [$alertCount 회]")
            .setContentText("$appName: ${"%.2f".format(usageMB)}MB 사용 - 즉시 확인 필요!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$appName 앱이 화면 OFF 상태에서 ${"%.2f".format(usageMB)}MB 데이터를 사용했습니다.\n\n탭하여 앱을 종료하세요!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(appName.hashCode(), notification)
    }

    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("데이터 유출 보호")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "데이터 유출 감시",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드에서 앱의 데이터 사용량을 모니터링합니다"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitoringRunnable)
        alertHandler.removeCallbacksAndMessages(null) // 모든 반복 알림 중지
        suspiciousAppsMap.clear()
        stopSirenSound() // 사이렌 소리 중지

        // 브로드캐스트 리시버 해제
        try {
            unregisterReceiver(sirenStopReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "리시버 해제 실패: ${e.message}")
        }

        Log.d(TAG, "DataLeakMonitorService 종료")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
