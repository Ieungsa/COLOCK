package com.ieungsa.myapplication

import kotlin.math.*

/**
 * 개선된 음성 분석기
 * - 8개 특징 추출
 * - 연속 탐지 버퍼
 * - 오탐 방지 로직
 */
class ImprovedVoiceAnalyzer {

    // 상수 정의
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CONFIDENCE_THRESHOLD = 0.80f
        private const val LOUDNESS_0_100_MIN = 0.0020f
        private const val LOUDNESS_100_200_MIN = 0.0035f
        private const val CONSECUTIVE_THRESHOLD = 3  // 5개 중 3개 (파이썬과 동일)
        private const val BUFFER_SIZE = 5
    }

    // 연속 탐지 버퍼
    private val predictionBuffer = ArrayDeque<PredictionResult>(BUFFER_SIZE)

    // 오탐 방지 카운터
    private var falsePositivePrevention = 0

    /**
     * 오디오 특징
     */
    data class AudioFeatures(
        val loudness0to100: Float,      // 0-100Hz 대역 loudness
        val loudness100to200: Float,    // 100-200Hz 대역
        val loudness200to300: Float,    // 200-300Hz 대역
        val loudness4to8k: Float,       // 4-8kHz 대역
        val loudness16kPlus: Float,     // 16kHz+ 대역
        val totalEnergy: Float,         // 총 에너지
        val spectralCentroid: Float,    // 스펙트럼 중심
        val zcr: Float                  // Zero Crossing Rate
    )

    /**
     * 프레임 단위 예측 결과
     */
    data class PredictionResult(
        val prediction: Int,            // 0=정상, 1=변조
        val probability: Float,         // 확률
        val loudness0to100: Float,
        val loudness100to200: Float
    )

    /**
     * 최종 탐지 결과
     */
    data class DetectionResult(
        val isModulated: Boolean,       // 변조 여부
        val confidence: Float,          // 신뢰도
        val aiConfidence: Float,        // AI 확률
        val hasSignature: Boolean,      // 특징 검출 여부
        val bufferCount: String,        // 버퍼 상태
        val falsePositivePrevented: Int // 오탐 방지 횟수
    )

    /**
     * 오디오 프레임에서 특징 추출
     */
    fun extractFeatures(audioFrame: ShortArray): AudioFeatures {
        // FFT 수행
        val fftMagnitude = performFFT(audioFrame)
        val freqs = getFrequencies(fftMagnitude.size)

        return AudioFeatures(
            loudness0to100 = calculateBandLoudness(fftMagnitude, freqs, 0f, 100f),
            loudness100to200 = calculateBandLoudness(fftMagnitude, freqs, 100f, 200f),
            loudness200to300 = calculateBandLoudness(fftMagnitude, freqs, 200f, 300f),
            loudness4to8k = calculateBandLoudness(fftMagnitude, freqs, 4000f, 8000f),
            loudness16kPlus = calculateBandLoudness(fftMagnitude, freqs, 16000f, 22050f),
            totalEnergy = fftMagnitude.sumOf { (it * it).toDouble() }.toFloat(),
            spectralCentroid = calculateSpectralCentroid(fftMagnitude, freqs),
            zcr = calculateZCR(audioFrame)
        )
    }

    /**
     * 개선된 예측 함수
     * AI 모델 + 규칙 기반 검증
     */
    fun predictImproved(audioFrame: ShortArray, aiModel: AIModel?): DetectionResult {
        // 1. 특징 추출
        val features = extractFeatures(audioFrame)

        // 2. AI 예측 (모델이 있으면)
        val aiPrediction: Int
        val aiProbability: Float

        if (aiModel != null) {
            val (pred, prob) = aiModel.predict(features)
            aiPrediction = pred
            aiProbability = prob
        } else {
            // AI 모델 없으면 규칙 기반
            aiPrediction = if (features.loudness0to100 >= 0.003f) 1 else 0
            aiProbability = if (aiPrediction == 1) 0.9f else 0.1f
        }

        // 3. 특징 기반 검증 (변조)
        val hasSignature = (
            features.loudness0to100 >= LOUDNESS_0_100_MIN ||
            features.loudness100to200 >= LOUDNESS_100_200_MIN
        )

        // 4. AI + 규칙 결합 (변조)
        val framePrediction: Int = if (aiPrediction == 1) {
            if (aiProbability >= CONFIDENCE_THRESHOLD && hasSignature) {
                1
            } else {
                falsePositivePrevention++
                0
            }
        }
        else {
            0
        }

        // 5. 버퍼에 추가 (변조)
        predictionBuffer.addLast(
            PredictionResult(
                prediction = framePrediction,
                probability = aiProbability,
                loudness0to100 = features.loudness0to100,
                loudness100to200 = features.loudness100to200
            )
        )

        if (predictionBuffer.size > BUFFER_SIZE) {
            predictionBuffer.removeFirst()
        }

        // 6. 연속 탐지 (변조 스무딩)
        val isModulated: Boolean
        val avgProbability: Float

        if (predictionBuffer.size >= 3) {
            val modulatedCount = predictionBuffer.count { it.prediction == 1 }
            isModulated = modulatedCount >= CONSECUTIVE_THRESHOLD
            avgProbability = predictionBuffer.map { it.probability }.average().toFloat()
        } else {
            isModulated = false
            avgProbability = 0.5f
        }

        return DetectionResult(
            isModulated = isModulated,
            confidence = if (isModulated) avgProbability else 1f - avgProbability,
            aiConfidence = aiProbability,
            hasSignature = hasSignature,
            bufferCount = "${predictionBuffer.count { it.prediction == 1 }}/${predictionBuffer.size}",
            falsePositivePrevented = falsePositivePrevention
        )
    }

    /**
     * FFT 수행 (간단한 Cooley-Tukey FFT)
     */
    private fun performFFT(audioFrame: ShortArray): FloatArray {
        val n = audioFrame.size
        val fftSize = nextPowerOfTwo(n)
        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        for (i in audioFrame.indices) {
            real[i] = audioFrame[i].toDouble() / 32768.0
        }
        fft(real, imag)
        val magnitude = FloatArray(fftSize / 2)
        val normFactor = audioFrame.size.toFloat()
        for (i in magnitude.indices) {
            magnitude[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]).toFloat() / normFactor
        }
        return magnitude
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return
        bitReversalPermutation(real, imag)
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val tablestep = n / size
            for (i in 0 until n step size) {
                var k = 0
                for (j in i until i + halfSize) {
                    val tpre = real[j + halfSize] * cos(-2.0 * PI * k / size) - imag[j + halfSize] * sin(-2.0 * PI * k / size)
                    val tpim = real[j + halfSize] * sin(-2.0 * PI * k / size) + imag[j + halfSize] * cos(-2.0 * PI * k / size)
                    real[j + halfSize] = real[j] - tpre
                    imag[j + halfSize] = imag[j] - tpim
                    real[j] += tpre
                    imag[j] += tpim
                    k += tablestep
                }
            }
            size *= 2
        }
    }

    private fun bitReversalPermutation(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempReal = real[i]
                val tempImag = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempReal
                imag[j] = tempImag
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }
    }

    private fun getFrequencies(fftSize: Int): FloatArray {
        return FloatArray(fftSize) { index ->
            index * (SAMPLE_RATE.toFloat() / (fftSize * 2))
        }
    }

    private fun calculateBandLoudness(fftMagnitude: FloatArray, freqs: FloatArray, minFreq: Float, maxFreq: Float): Float {
        var sum = 0f
        var count = 0
        for (i in fftMagnitude.indices) {
            if (freqs[i] in minFreq..maxFreq) {
                sum += fftMagnitude[i]
                count++
            }
        }
        return if (count > 0) sum / count else 0f
    }

    private fun calculateSpectralCentroid(fftMagnitude: FloatArray, freqs: FloatArray): Float {
        var numerator = 0f
        var denominator = 0f
        for (i in fftMagnitude.indices) {
            numerator += freqs[i] * fftMagnitude[i]
            denominator += fftMagnitude[i]
        }
        return if (denominator > 0) numerator / denominator else 0f
    }

    private fun calculateZCR(audioFrame: ShortArray): Float {
        var count = 0
        for (i in 1 until audioFrame.size) {
            if ((audioFrame[i] >= 0 && audioFrame[i - 1] < 0) || (audioFrame[i] < 0 && audioFrame[i - 1] >= 0)) {
                count++
            }
        }
        return count.toFloat() / (2 * audioFrame.size)
    }

    fun reset() {
        predictionBuffer.clear()
        falsePositivePrevention = 0
    }
}