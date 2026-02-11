package com.ieungsa.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CallMonitorService : Service() {

    private val TAG = "CallMonitorService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "VoiceGuardChannel"
    // SPAM_THRESHOLD는 Gemini 모델의 is_phishing 플래그나 risk_score를 사용하게 되므로,
    // 직접적인 사용은 줄어들 수 있습니다.
    private val SPAM_THRESHOLD = 80 // Gemini의 risk_score와 맞추기 위해 80으로 설정

    private lateinit var phishingDetector: PhishingDetector
    private var isDetecting = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: android.telephony.PhoneStateListener? = null

    // Vosk STT
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private val VOSK_MODEL_PATH = "vosk-model-ko-0.22"

    private var callFileWatcher: CallFileWatcher? = null
    private val BCR_FOLDER_PATH = "/sdcard/Music/"
    private val SAMPLE_RATE = 16000f
    
    private suspend fun copyAssetFolder(assetPath: String, targetPath: String) {
        withContext(Dispatchers.IO) {
            val assetManager = assets
            val files = try {
                assetManager.list(assetPath)
            } catch (e: IOException) {
                // assetManager.list()가 실패하면 파일로 간주합니다.
                null
            }

            val targetFile = File(targetPath)

            if (files.isNullOrEmpty()) { // 파일이거나 빈 디렉토리인 경우
                // assetManager.open()을 시도하여 파일인지 확인합니다.
                try {
                    assetManager.open(assetPath).use { inputStream ->
                        // 파일인 경우, 복사를 진행합니다.
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        Log.d(TAG, "파일 복사: $assetPath -> ${targetFile.absolutePath}")
                    }
                } catch (e: IOException) {
                    // open() 실패 시 디렉토리로 간주하고 생성만 합니다.
                    if (!targetFile.exists()) {
                        targetFile.mkdirs()
                        Log.d(TAG, "빈 디렉토리 생성: ${targetFile.absolutePath}")
                    }
                }
                Unit // Explicitly return Unit
            } else { // 하위 파일/디렉토리가 있는 경우
                if (!targetFile.exists()) {
                    targetFile.mkdirs() // targetDir -> targetFile 수정
                }
                for (filename in files) {
                    copyAssetFolder("$assetPath/$filename", "$targetPath/$filename")
                }
                Unit // Explicitly return Unit
            }
        }
    }

    override fun onCreate() {
        super.onCreate() 
        Log.d(TAG, "CallMonitorService 생성")

        createNotificationChannel()

        phishingDetector = PhishingDetector()

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        // 전화 상태 리스너는 Vosk 모델이 로드된 후 등록합니다.
        initializeVoskAndFileWatcher()
    }

    private fun initializeVoskAndFileWatcher() {
        serviceScope.launch {
            try {
                val modelDir = File(filesDir, VOSK_MODEL_PATH)
                if (!modelDir.exists() || modelDir.list()?.isEmpty() == true) {
                    Log.d(TAG, "Vosk 모델 복사 시작: $VOSK_MODEL_PATH")
                    copyAssetFolder(VOSK_MODEL_PATH, modelDir.absolutePath)
                    Log.d(TAG, "Vosk 모델 복사 완료.")
                } else {
                    Log.d(TAG, "Vosk 모델이 이미 존재합니다.")
                }
                
                if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
                    voskModel = Model(modelDir.absolutePath)
                    Log.d(TAG, "Vosk 모델 로드 성공: ${modelDir.absolutePath}")

                    callFileWatcher = CallFileWatcher(BCR_FOLDER_PATH) { audioChunk ->
                        if (voskRecognizer?.acceptWaveForm(audioChunk, audioChunk.size) == true) {
                            val result = JSONObject(voskRecognizer!!.result).getString("text")
                            if(result.isNotBlank()) {
                                Log.i(TAG, "Vosk 최종 결과: $result")
                                // Gemini API 호출은 suspend 함수이므로 코루틴 스코프 내에서 호출
                                serviceScope.launch {
                                    analyzeText(result)
                                }
                            }
                        } else {
                            val partialResult = JSONObject(voskRecognizer!!.partialResult).getString("partial")
                            if (partialResult.isNotBlank()) {
                                Log.d(TAG, "Vosk 부분 결과: $partialResult")
                            }
                        }
                    }
                    Log.d(TAG, "CallFileWatcher 초기화 완료. 감시 폴더: $BCR_FOLDER_PATH")

                    // 모델 로딩이 성공했으므로, 이제 전화 상태 감지를 시작합니다.
                    withContext(Dispatchers.Main) {
                        registerPhoneStateListener()
                        Log.d(TAG, "전화 상태 리스너 등록 완료. 이제부터 통화 감시를 시작합니다.")
                    }

                } else {
                    Log.e(TAG, "Vosk 모델 디렉토리 복사 실패 또는 비어 있음: ${modelDir.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vosk 모델 로드 실패", e)
            }
        }
    }
    
    private suspend fun analyzeText(text: String) { // suspend 함수로 변경
        val analysisResult = phishingDetector.analyzeText(text) // String? 반환
        Log.d(TAG, "분석 텍스트: \"$text\" / 분석 결과: $analysisResult")

        if (!analysisResult.isNullOrBlank()) {
            Log.w(TAG, "⚠️ 피싱 분석 결과: $analysisResult")
            updateNotification("🚨 피싱 의심! $analysisResult")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("대기 중"))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
        // phishingDetector.close() // Gemini 모델은 close()가 필요 없음
        voskModel?.close()
        serviceScope.cancel()
        unregisterPhoneStateListener()
    }

    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) { handleCallStateChange(state) }
            }
            telephonyManager?.registerTelephonyCallback(mainExecutor, telephonyCallback!!)
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener = object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) { handleCallStateChange(state) }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager?.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "통화 시작 감지")
                updateNotification("통화 중 - 내용 분석 활성화")
                startDetection()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "통화 종료 감지")
                updateNotification("대기 중")
                stopDetection()
            }
        }
    }

    private fun startDetection() {
        if (isDetecting || voskModel == null) return
        isDetecting = true
        Log.d(TAG, "Vosk STT 및 내용 분석 시작")
        voskRecognizer = Recognizer(voskModel, SAMPLE_RATE)
        callFileWatcher?.startWatching()
    }

    private fun stopDetection() {
        if (!isDetecting) return
        isDetecting = false
        Log.d(TAG, "Vosk STT 및 내용 분석 중지")
        callFileWatcher?.stop()
        voskRecognizer?.close()
        voskRecognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VoiceGuard 실시간 감시", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceGuard 활성화")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
}