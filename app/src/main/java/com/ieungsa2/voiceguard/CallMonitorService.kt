package com.ieungsa2.voiceguard

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
import com.ieungsa2.R
import com.ieungsa2.NotificationHelper
import com.ieungsa2.database.VishingDatabase
import com.ieungsa2.database.VishingAlert
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground Service for real-time Voice Phishing detection.
 * Monitors call state and analyzes audio using Gemini API.
 */
class CallMonitorService : Service() {

    private val TAG = "CallMonitorService"
    private val NOTIFICATION_ID = 1002
    private val CHANNEL_ID = "VoiceGuardChannel"
    
    private lateinit var phishingDetector: PhishingDetector
    private var isDetecting = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callScope: CoroutineScope? = null

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    
    private var callFileWatcher: CallFileWatcher? = null
    private val BCR_FOLDER_PATH = "/storage/emulated/0/Android/data/com.chiller3.bcr/files/" 
    
    private val SAMPLE_RATE = 16000f
    private val audioBuffer = mutableListOf<Byte>()
    private val CHUNK_SIZE = (SAMPLE_RATE * 2 * 15).toInt() 
    
    private var isServiceStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        phishingDetector = PhishingDetector()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        callFileWatcher = CallFileWatcher(this, BCR_FOLDER_PATH) { callInfo, audioChunk ->
            if (isDetecting) {
                processAudioChunk(callInfo, audioChunk)
            }
        }

        registerPhoneStateListener()
    }

    private fun processAudioChunk(callInfo: String, audioChunk: ByteArray) {
        synchronized(audioBuffer) {
            audioBuffer.addAll(audioChunk.toList())
            
            if (audioBuffer.size >= CHUNK_SIZE) {
                val chunkToSend = audioBuffer.toByteArray()
                audioBuffer.clear()
                
                callScope?.launch {
                    analyzeCallAudio(callInfo, chunkToSend)
                }
            }
        }
    }

    private suspend fun analyzeCallAudio(callInfo: String, pcmData: ByteArray) {
        try {
            val tempPcmFile = File(cacheDir, "temp_chunk.pcm")
            val tempWavFile = File(cacheDir, "temp_chunk.wav")
            
            FileOutputStream(tempPcmFile).use { it.write(pcmData) }
            WavFileWriter.createWavFile(tempPcmFile, tempWavFile, SAMPLE_RATE.toInt())
            
            val result = phishingDetector.analyzeAudio(tempWavFile.readBytes())
            
            if (result != null) {
                // Determine if warning is needed
                if (result.isPhishing || result.score >= 70) {
                    savePhishingDetection(callInfo, result)
                    withContext(Dispatchers.Main) {
                        AlertManager.showWarning(this@CallMonitorService, result.score / 100f, result.warningMessage)
                    }
                }
            }
            
            tempPcmFile.delete()
            tempWavFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Audio analysis error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceStarted) {
            startForeground(NOTIFICATION_ID, createNotification("Monitoring..."))
            isServiceStarted = true
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
        serviceScope.cancel()
        unregisterPhoneStateListener()
    }

    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) { handleCallStateChange(state) }
            }
            telephonyManager?.registerTelephonyCallback(mainExecutor, telephonyCallback!!)
        }
    }

    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                updateNotification("Call in progress - Protection Active")
                startDetection()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                updateNotification("Standby")
                stopDetection()
            }
        }
    }

    private fun startDetection() {
        if (isDetecting) return
        isDetecting = true
        callScope = CoroutineScope(Dispatchers.IO + Job())
        synchronized(audioBuffer) { audioBuffer.clear() }
        callFileWatcher?.startWatching()
    }

    private fun stopDetection() {
        if (!isDetecting) return
        isDetecting = false
        callScope?.cancel()
        callScope = null
        callFileWatcher?.stop()
        synchronized(audioBuffer) { audioBuffer.clear() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VoiceGuard Service", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceGuard Active")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private suspend fun savePhishingDetection(callInfo: String, result: PhishingDetector.AnalysisResult) {
        try {
            val database = VishingDatabase.getDatabase(applicationContext)
            val riskLevel = when {
                result.score >= 90 -> "High Risk"
                result.score >= 70 -> "Risk"
                else -> "Suspicious"
            }

            val alert = VishingAlert(
                timestamp = java.util.Date(),
                phoneNumber = callInfo,
                callDuration = 0L,
                transcription = result.sttText,
                detectedKeywords = result.detectedKeywords.joinToString(", "),
                riskScore = result.score / 100f,
                riskLevel = riskLevel
            )
            database.vishingAlertDao().insert(alert)
        } catch (e: Exception) {
            Log.e(TAG, "DB Save error: ${e.message}")
        }
    }
}
