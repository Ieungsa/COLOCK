package com.ieungsa.myapplication

// 필요한 모든 라이브러리를 정확한 경로로 임포트합니다.
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.HarmBlockThreshold
import com.google.firebase.vertexai.type.HarmCategory
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhishingDetector {

    // 1. 안전 설정 정의 (BLOCK_NONE 설정 포함)
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.NONE)
    )

    // 2. 모델 설정 (Gemini 1.5 Flash 사용)
    private val generativeModel = Firebase.vertexAI.generativeModel(
        modelName = "gemini-1.5-flash",
        generationConfig = generationConfig {
            temperature = 0.1f // 분석의 정확도를 위해 낮게 설정
            topK = 32
            topP = 1f
        },
        safetySettings = safetySettings
    )

    // 3. 분석 함수
    suspend fun analyzeText(input: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "다음 텍스트가 피싱이나 스미싱인지 분석해줘: $input"
            val response = generativeModel.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
