package com.ieungsa2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ieungsa2.database.PhishingAlert
import com.ieungsa2.database.PhishingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern

/**
 * BroadcastReceiver for handling incoming SMS messages.
 * Detects URLs within the message body and initiates phishing analysis.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            pendingResult.finish()
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                pendingResult.finish()
                return
            }

            val fullMessageBody = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"

            // Update stats
            incrementScannedCount(context)

            if (fullMessageBody.isBlank()) {
                pendingResult.finish()
                return
            }

            val urls = extractUrls(fullMessageBody)

            if (urls.isNotEmpty()) {
                Log.i(TAG, "URLs detected: ${urls.size}")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        analyzeUrls(context, urls, sender, fullMessageBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "Analysis error: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                pendingResult.finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receiver error: ${e.message}")
            pendingResult.finish()
        }
    }

    private fun incrementScannedCount(context: Context) {
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt("scanned_messages_count", 0)
        sharedPref.edit().putInt("scanned_messages_count", currentCount + 1).apply()
    }

    /**
     * Extracts URLs from the message text using regex.
     * Supports standard URLs and shorteners without http prefix.
     */
    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val urlPattern = Pattern.compile(
            "(?:https?://|www\\.|[a-zA-Z0-9.-]+\\.(?:com|net|kr|org|xyz|tk|me|link|info|biz|top|buzz|co\\.kr|go\\.kr))/[^\\s가-힣]*|https?://[^\\s가-힣]+",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = urlPattern.matcher(text)

        while (matcher.find()) {
            val rawUrl = matcher.group(0)
            if (rawUrl != null) {
                val cleanedUrl = cleanUrl(rawUrl)
                if (cleanedUrl.isNotEmpty()) {
                    urls.add(cleanedUrl)
                }
            }
        }
        return urls.distinct()
    }

    private fun cleanUrl(rawUrl: String): String {
        var url = rawUrl
        // Remove trailing punctuation
        url = url.trimEnd('.', ',', ':', ';')
        return url
    }

    private suspend fun analyzeUrls(
        context: Context,
        urls: List<String>,
        sender: String,
        messageBody: String
    ) {
        val database = PhishingDatabase.getDatabase(context)
        performAnalysisAndNotify(context, database, urls, sender, messageBody)
    }

    private suspend fun performAnalysisAndNotify(
        context: Context,
        database: PhishingDatabase,
        urls: List<String>,
        sender: String,
        messageBody: String
    ) {
        val urlAnalyzer = UrlAnalyzer()
        val dao = database.phishingAlertDao()

        var mostDangerousUrl = ""
        var highestRiskScore = 0f
        val dangerousUrls = mutableListOf<Pair<String, Float>>()

        for (url in urls) {
            val riskScore = urlAnalyzer.analyzeUrlPattern(url, messageBody)
            
            // Threshold: 0.1
            if (riskScore >= 0.1f) {
                dangerousUrls.add(Pair(url, riskScore))
                if (riskScore > highestRiskScore) {
                    highestRiskScore = riskScore
                    mostDangerousUrl = url
                }
            }
        }

        if (dangerousUrls.isNotEmpty()) {
            try {
                val riskLevel = when {
                    highestRiskScore > 0.7f -> "매우 위험"
                    highestRiskScore > 0.4f -> "위험"
                    else -> "의심"
                }

                val allDangerousUrls = dangerousUrls.joinToString("\n") { it.first }

                val alert = PhishingAlert(
                    timestamp = Date(),
                    sender = sender,
                    messageBody = messageBody,
                    detectedUrl = allDangerousUrls,
                    riskScore = highestRiskScore,
                    riskLevel = riskLevel,
                    type = "SMISHING"
                )
                dao.insertAlert(alert)
            } catch (e: Exception) {
                Log.e(TAG, "DB Save failed: ${e.message}")
            }

            NotificationHelper.showPhishingAlert(
                context,
                if (dangerousUrls.size == 1) mostDangerousUrl else "${dangerousUrls.size} suspicious links detected",
                sender,
                highestRiskScore
            )
        }
    }
}