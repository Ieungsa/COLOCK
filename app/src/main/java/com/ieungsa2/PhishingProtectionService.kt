package com.ieungsa2

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Service to handle data leak monitoring and impersonation verification requests.
 */
class PhishingProtectionService : Service() {

    companion object {
        private const val TAG = "PhishingService"
        private const val SERVICE_ID = 1001
    }

    private val verificationRepository = VerificationRepository()
    private var verificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        
        val notification = NotificationHelper.getServiceNotification(
            this,
            "Smishing Guard Active",
            "Monitoring for threats."
        )
        startForeground(SERVICE_ID, notification)

        startListeningForVerification()
    }

    private fun startListeningForVerification() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val myPhoneRaw = sharedPref.getString("user_phone", null) ?: return
        val myPhone = myPhoneRaw.replace("-", "").replace(" ", "").trim()

        verificationJob = CoroutineScope(Dispatchers.IO).launch {
            verificationRepository.listenForIncomingRequests(myPhone).collect { data ->
                val requestId = data["requestId"] as? String ?: return@collect
                val requesterPhone = data["requesterPhone"] as? String ?: "User"

                // Launch fullscreen alert
                val intent = Intent(this@PhishingProtectionService, FullscreenAlertActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("ALERT_TYPE", "VERIFICATION")
                    putExtra("REQUESTER_NAME", requesterPhone)
                    putExtra("REQUEST_ID", requestId)
                }
                
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch alert: ${e.message}")
                }
                
                // Backup notification
                NotificationHelper.showPhishingAlert(
                    this@PhishingProtectionService,
                    "Identity verification request received.",
                    requesterPhone,
                    0.4f
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        verificationJob?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
