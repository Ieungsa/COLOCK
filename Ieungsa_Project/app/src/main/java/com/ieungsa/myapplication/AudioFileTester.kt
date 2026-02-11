package com.ieungsa.myapplication

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 오디오 파일 테스트 유틸리티
 * - 오디오 파일을 읽어서 변조 음성 분석
 * - 실제 통화 없이 알고리즘 검증 가능
 */
class AudioFileTester(private val context: Context) {

    private val TAG = "AudioFileTester"
    private val analyzer = ImprovedVoiceAnalyzer()
    private var aiModel: AIModel? = null

    companion object {
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 1024
    }

    init {
        try {
            aiModel = AIModel(context)
            Log.d(TAG, "AI 모델 로드 완료")
        } catch (e: Exception) {
            Log.w(TAG, "AI 모델 로드 실패: ${e.message}")
        }
    }

    /**
     * WAV 파일 분석
     */
    suspend fun analyzeWavFile(filePath: String): TestResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "분석 시작: $filePath")

        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext TestResult(
                    success = false,
                    error = "파일을 찾을 수 없습니다: $filePath"
                )
            }

            // WAV 파일 읽기
            val audioData = readWavFile(file)
            if (audioData == null) {
                return@withContext TestResult(
                    success = false,
                    error = "WAV 파일 읽기 실패"
                )
            }

            Log.d(TAG, "오디오 데이터 크기: ${audioData.size} samples")

            // 프레임 단위로 분석 (침묵 부분 제외)
            val results = mutableListOf<ImprovedVoiceAnalyzer.DetectionResult>()
            var offset = 0

            while (offset + FRAME_SIZE < audioData.size) {
                val frame = audioData.sliceArray(offset until offset + FRAME_SIZE)

                // 볼륨 체크 (Python과 동일: volume > 0.01)
                val volume = frame.maxOrNull()?.let { kotlin.math.abs(it / 32768.0f) } ?: 0f

                if (volume > 0.01f) {  // 침묵이 아닐 때만 분석
                    val result = analyzer.predictImproved(frame, aiModel)
                    results.add(result)

                    Log.d(TAG, "프레임 $offset: 변조=${result.isModulated}, 신뢰도=${String.format("%.2f", result.confidence)}, 볼륨=${String.format("%.3f", volume)}")
                } else {
                    Log.d(TAG, "프레임 $offset: 침묵 구간 스킵 (볼륨=${String.format("%.3f", volume)})")
                }

                offset += FRAME_SIZE
            }

            // 전체 결과 집계
            val modulatedCount = results.count { it.isModulated }
            val totalCount = results.size
            val modulatedPercent = (modulatedCount.toFloat() / totalCount * 100).toInt()

            val finalResult = TestResult(
                success = true,
                totalFrames = totalCount,
                modulatedFrames = modulatedCount,
                modulatedPercent = modulatedPercent,
                averageConfidence = results.map { it.confidence }.average().toFloat(),
                isModulated = modulatedPercent > 30,  // 30% 이상이면 변조로 판정
                details = "분석 완료: ${totalCount}개 프레임 중 ${modulatedCount}개(${modulatedPercent}%)에서 변조 탐지"
            )

            Log.d(TAG, "최종 결과: ${finalResult.details}")
            finalResult

        } catch (e: Exception) {
            Log.e(TAG, "분석 실패: ${e.message}")
            e.printStackTrace()
            TestResult(
                success = false,
                error = "분석 오류: ${e.message}"
            )
        }
    }

    /**
     * WAV 파일 메타데이터
     */
    data class WavMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataSize: Int,
        val dataOffset: Int  // data 청크 시작 위치
    )

    /**
     * WAV 파일 헤더 파싱 (data 청크를 동적으로 검색)
     */
    private fun parseWavHeader(file: File): WavMetadata? {
        return try {
            val inputStream = FileInputStream(file)

            // 충분히 큰 버퍼 읽기 (LIST 등 추가 청크 대비)
            val headerBuffer = ByteArray(200)
            val bytesRead = inputStream.read(headerBuffer)
            inputStream.close()

            // RIFF 헤더 확인
            val riff = String(headerBuffer.sliceArray(0..3))
            val wave = String(headerBuffer.sliceArray(8..11))
            if (riff != "RIFF" || wave != "WAVE") {
                Log.e(TAG, "유효하지 않은 WAV 파일")
                return null
            }

            val buffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            // fmt 청크에서 정보 읽기 (위치는 고정)
            val sampleRate = buffer.getInt(24)
            val channels = buffer.getShort(22).toInt()
            val bitsPerSample = buffer.getShort(34).toInt()

            // data 청크 찾기 (동적 검색)
            var dataSize = 0
            var dataOffset = 0
            var offset = 36  // fmt 청크 다음부터 시작

            while (offset < bytesRead - 8) {
                val chunkId = String(headerBuffer.sliceArray(offset until offset + 4))
                val chunkSize = buffer.getInt(offset + 4)

                if (chunkId == "data") {
                    dataSize = chunkSize
                    dataOffset = offset + 8  // 8바이트 청크 헤더 다음이 실제 데이터
                    Log.d(TAG, "data 청크 발견: offset=$dataOffset, size=$dataSize bytes")
                    break
                } else {
                    Log.d(TAG, "청크 스킵: $chunkId (${chunkSize}bytes)")
                    // 다음 청크로 이동 (8바이트 헤더 + 청크 크기)
                    offset += 8 + chunkSize
                }
            }

            if (dataSize == 0) {
                Log.e(TAG, "data 청크를 찾을 수 없습니다")
                return null
            }

            Log.d(TAG, "WAV 메타데이터: ${sampleRate}Hz, ${channels}ch, ${bitsPerSample}bit, 데이터시작=${dataOffset}, ${dataSize}bytes")

            WavMetadata(sampleRate, channels, bitsPerSample, dataSize, dataOffset)

        } catch (e: Exception) {
            Log.e(TAG, "WAV 헤더 파싱 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * WAV 파일을 ShortArray로 읽기 (스테레오 -> 모노 변환 포함)
     */
    private fun readWavFile(file: File): ShortArray? {
        return try {
            // 1. 메타데이터 파싱
            val metadata = parseWavHeader(file) ?: return null

            val inputStream = FileInputStream(file)

            // data 청크 시작 위치까지 스킵
            inputStream.skip(metadata.dataOffset.toLong())

            // 오디오 데이터 읽기 (data 청크 크기만큼만)
            val audioBytes = ByteArray(metadata.dataSize)
            val bytesRead = inputStream.read(audioBytes)
            inputStream.close()

            Log.d(TAG, "오디오 데이터 읽기: ${bytesRead}/${metadata.dataSize} bytes")

            // ByteArray -> ShortArray 변환 (16-bit PCM)
            val rawAudioData = ShortArray(audioBytes.size / 2)
            ByteBuffer.wrap(audioBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(rawAudioData)

            // 2. 스테레오 -> 모노 변환
            val monoData = if (metadata.channels == 2) {
                Log.d(TAG, "스테레오 -> 모노 변환 중...")
                val mono = ShortArray(rawAudioData.size / 2)
                for (i in mono.indices) {
                    val left = rawAudioData[i * 2].toInt()
                    val right = rawAudioData[i * 2 + 1].toInt()
                    mono[i] = ((left + right) / 2).toShort()
                }
                mono
            } else {
                rawAudioData
            }

            // 3. 샘플레이트 리샘플링 (간단한 선형 보간)
            val resampledData = if (metadata.sampleRate != SAMPLE_RATE) {
                Log.d(TAG, "${metadata.sampleRate}Hz -> ${SAMPLE_RATE}Hz 리샘플링 중...")
                resampleAudio(monoData, metadata.sampleRate, SAMPLE_RATE)
            } else {
                monoData
            }

            Log.d(TAG, "최종 오디오 데이터: ${resampledData.size} samples")
            resampledData

        } catch (e: Exception) {
            Log.e(TAG, "WAV 파일 읽기 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 간단한 선형 보간 리샘플링
     */
    private fun resampleAudio(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return input

        val ratio = fromRate.toDouble() / toRate
        val outputSize = (input.size / ratio).toInt()
        val output = ShortArray(outputSize)

        for (i in output.indices) {
            val srcIndex = i * ratio
            val srcIndexInt = srcIndex.toInt()

            if (srcIndexInt + 1 < input.size) {
                // 선형 보간
                val frac = srcIndex - srcIndexInt
                val sample1 = input[srcIndexInt].toDouble()
                val sample2 = input[srcIndexInt + 1].toDouble()
                output[i] = (sample1 + (sample2 - sample1) * frac).toInt().toShort()
            } else if (srcIndexInt < input.size) {
                output[i] = input[srcIndexInt]
            }
        }

        return output
    }

    /**
     * assets 폴더에서 WAV 파일 분석
     */
    suspend fun analyzeAssetFile(fileName: String): TestResult = withContext(Dispatchers.IO) {
        try {
            // assets 폴더에서 임시 파일로 복사
            val tempFile = File(context.cacheDir, fileName)
            context.assets.open(fileName).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val result = analyzeWavFile(tempFile.absolutePath)
            tempFile.delete()
            result

        } catch (e: Exception) {
            Log.e(TAG, "Asset 파일 분석 실패: ${e.message}")
            TestResult(
                success = false,
                error = "Asset 파일 오류: ${e.message}"
            )
        }
    }

    /**
     * 테스트 결과
     */
    data class TestResult(
        val success: Boolean,
        val totalFrames: Int = 0,
        val modulatedFrames: Int = 0,
        val modulatedPercent: Int = 0,
        val averageConfidence: Float = 0f,
        val isModulated: Boolean = false,
        val details: String = "",
        val error: String? = null
    )

    fun close() {
        aiModel?.close()
    }
}
