package com.ieungsa2.voiceguard

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PhishingDetector {

    // TODO: Replace with your actual Gemini API Key
    private val API_KEY = "YOUR_GEMINI_API_KEY"

    // Gemini 2.5 Flash Model Configuration
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
        )
    )

    /**
     * Sends audio data to Gemini for STT and phishing analysis.
     */
    suspend fun analyzeAudio(audioData: ByteArray): AnalysisResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val prompt = """
                You are a top-tier voice phishing analyst. 
                Analyze the attached audio file following these strict guidelines:

                1. Transcribe the entire conversation into Korean accurately (STT).
                2. Analyze the context to determine if it's a voice phishing attempt.
                
                [Criteria]
                - Impersonation (Prosecutor/Bank/Family) + Pressure + Demand (Money/App Install/Info).
                - Low score for general inquiries or normal conversations.

                [Output JSON Format]
                {
                  "stt_text": "Full transcription",
                  "score": 0-100,
                  "is_phishing": true/false,
                  "detected_keywords": ["Keyword1", "Keyword2"],
                  "warning_message": "Short warning message for the user",
                  "reason": "Reason for judgment"
                }
            """.trimIndent()

            val inputContent = content {
                blob("audio/wav", audioData) // BCR 데이터는 기본적으로 PCM/WAV 형태
                text(prompt)
            }

            val response = generativeModel.generateContent(inputContent)
            val resultText = response.text?.trim() ?: ""
            
            val duration = System.currentTimeMillis() - startTime
            Log.d("PhishingDetector", "🌐 Gemini Multimodal 응답 ($duration ms): $resultText")

            if (resultText.isNotBlank()) {
                val json = JSONObject(resultText)
                return@withContext AnalysisResult(
                    sttText = json.optString("stt_text", ""),
                    score = json.optInt("score", 0),
                    isPhishing = json.optBoolean("is_phishing", false),
                    warningMessage = json.optString("warning_message", ""),
                    reason = json.optString("reason", ""),
                    detectedKeywords = json.optJSONArray("detected_keywords")?.let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("PhishingDetector", "❌ Gemini 멀티모달 분석 실패: ${e.message}")
            e.printStackTrace()
        }
        null
    }

    data class AnalysisResult(
        val sttText: String,
        val score: Int,
        val isPhishing: Boolean,
        val warningMessage: String,
        val reason: String,
        val detectedKeywords: List<String>
    )

    // 기존 텍스트 분석 함수 (호환성을 위해 유지하되 SDK 방식으로 업그레이드)
    suspend fun analyzeText(input: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "다음 통화 텍스트가 보이스피싱인지 분석하여 JSON { \"is_phishing\": bool, \"reason\": string } 으로 답해줘: \"$input\""
            val response = generativeModel.generateContent(prompt)
            val json = JSONObject(response.text ?: "{}")
            if (json.optBoolean("is_phishing", false)) {
                return@withContext json.optString("reason", "보이스피싱 의심")
            }
        } catch (e: Exception) {
            Log.e("PhishingDetector", "텍스트 분석 실패: ${e.message}")
        }
        null
    }
}
