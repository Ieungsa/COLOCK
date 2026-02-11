package com.ieungsa.myapplication

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite AI 모델 래퍼
 * - 8개 특징을 입력으로 받아 변조 여부 예측
 */
class AIModel(context: Context, modelFilename: String = "voice_classifier.tflite") {

    private var interpreter: Interpreter? = null
    private val TAG = "AIModel"

    init {
        try {
            // assets에서 모델 로드
            val modelFile = loadModelFile(context, modelFilename)
            interpreter = Interpreter(modelFile)
            Log.d(TAG, "AI 모델 로드 성공: $modelFilename")
        } catch (e: Exception) {
            Log.e(TAG, "AI 모델 로드 실패: ${e.message}")
            interpreter = null
        }
    }

    /**
     * 8개 특징으로 예측
     * @return Pair(예측값 0/1, 확률)
     */
    fun predict(features: ImprovedVoiceAnalyzer.AudioFeatures): Pair<Int, Float> {
        // 모델이 없으면 규칙 기반 fallback
        if (interpreter == null) {
            return fallbackPrediction(features)
        }

        try {
            // 입력 배열 준비 (8개 특징)
            val inputArray = floatArrayOf(
                features.loudness0to100,
                features.loudness100to200,
                features.loudness200to300,
                features.loudness4to8k,
                features.loudness16kPlus,
                features.totalEnergy,
                features.spectralCentroid,
                features.zcr
            )

            // 입력 버퍼 생성 (TFLite는 ByteBuffer 사용)
            val inputBuffer = ByteBuffer.allocateDirect(8 * 4).apply {
                order(ByteOrder.nativeOrder())
                inputArray.forEach { putFloat(it) }
            }

            // 출력 버퍼 생성 [정상 확률, 변조 확률]
            val outputBuffer = ByteBuffer.allocateDirect(2 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            // 예측 수행
            interpreter?.run(inputBuffer, outputBuffer)

            // 결과 파싱
            outputBuffer.rewind()
            val normalProb = outputBuffer.float
            val modulatedProb = outputBuffer.float

            val prediction = if (modulatedProb > normalProb) 1 else 0
            // 파이썬 코드와 동일: 변조 확률을 리턴 (ai_probability[1])
            val confidence = modulatedProb

            Log.d(TAG, "AI 예측: $prediction, 변조확률: $confidence (정상: $normalProb, 변조: $modulatedProb)")

            return Pair(prediction, confidence)

        } catch (e: Exception) {
            Log.e(TAG, "예측 실패: ${e.message}")
            return fallbackPrediction(features)
        }
    }

    /**
     * Fallback: 규칙 기반 예측
     * AI 모델이 없거나 실패했을 때 사용
     */
    private fun fallbackPrediction(features: ImprovedVoiceAnalyzer.AudioFeatures): Pair<Int, Float> {
        val isModulated = features.loudness0to100 >= 0.003f

        val prediction = if (isModulated) 1 else 0
        val confidence = if (isModulated) {
            // 0.003 ~ 0.005 범위를 0.8 ~ 1.0으로 매핑
            val normalized = ((features.loudness0to100 - 0.003f) / 0.002f).coerceIn(0f, 1f)
            0.8f + (normalized * 0.2f)
        } else {
            // 0.0 ~ 0.003 범위를 0.8 ~ 1.0으로 매핑
            val normalized = (features.loudness0to100 / 0.003f).coerceIn(0f, 1f)
            0.8f + ((1 - normalized) * 0.2f)
        }

        Log.d(TAG, "Fallback 예측: $prediction, 확률: $confidence (0-100Hz: ${features.loudness0to100})")

        return Pair(prediction, confidence)
    }

    /**
     * assets에서 모델 파일 로드
     */
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * 리소스 해제
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "AI 모델 리소스 해제")
    }

    /**
     * 모델이 정상적으로 로드되었는지 확인
     */
    fun isModelLoaded(): Boolean {
        return interpreter != null
    }
}
