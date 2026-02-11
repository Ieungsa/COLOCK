package com.ieungsa.myapplication

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile

class CallFileWatcher(
    private val folderPath: String,
    private val onAudioChunk: (ByteArray) -> Unit
) {

    private val TAG = "Navigator"
    private var isStreaming = false
    private var tailingJob: Job? = null
    private val watcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startWatching() {
        Log.d(TAG, "폴더 감시 시작: $folderPath (주기적 스캔 방식)")
        isStreaming = true
        tailingJob = watcherScope.launch {
            var currentTailingFile: File? = null
            var filePointer: Long = 0

            while (isStreaming) {
                val newestWavFile = findNewestWavFile(folderPath)

                if (newestWavFile != null && newestWavFile != currentTailingFile) {
                    Log.d(TAG, "새로운 통화 녹음 감지됨: ${newestWavFile.name}")
                    currentTailingFile = newestWavFile
                    filePointer = 44 // Skip WAV header

                    startTailingFile(currentTailingFile, filePointer) { currentTailingFile }
                }
                delay(1000)
            }
        }
    }

    private fun findNewestWavFile(folderPath: String): File? {
        val directory = File(folderPath)
        if (!directory.exists() || !directory.isDirectory) {
            Log.e(TAG, "감시 폴더가 존재하지 않거나 디렉토리가 아님: $folderPath")
            return null
        }

        return directory.listFiles { file -> file.isFile && file.extension == "wav" }
            ?.maxByOrNull { it.lastModified() }
    }

    private suspend fun startTailingFile(fileToTail: File, initialFilePointer: Long, currentTailingFileRef: () -> File?) {
        withContext(Dispatchers.IO) {
            try {
                val raf = RandomAccessFile(fileToTail, "r")
                var filePointer: Long = initialFilePointer

                Log.d(TAG, "실시간 스트리밍 시작: ${fileToTail.name}")
                
                val buffer = ByteArray(1024)
                var logCounter = 0 // 로그가 너무 많이 찍히는 것을 방지

                while (isStreaming && fileToTail == currentTailingFileRef()) {
                    val length = fileToTail.length()
                    
                    // 5초에 한 번씩 파일 크기 로그 출력
                    if (logCounter % 100 == 0) { // (100 * 50ms = 5 seconds)
                        Log.d(TAG, "파일 크기 확인: 현재 크기=${length}, 읽은 위치=${filePointer}")
                    }
                    logCounter++
                    
                    if (length > filePointer) {
                        Log.d(TAG, "파일 크기 증가 감지! 새 데이터 읽기 시도. 현재 크기=${length}, 읽은 위치=${filePointer}")
                        delay(150)
                        
                        val currentLength = fileToTail.length()
                        if (currentLength > filePointer) {
                            raf.seek(filePointer)
                            val read = raf.read(buffer)
                            
                            if (read > 0) {
                                Log.i(TAG, "성공적으로 ${read} 바이트 읽음. onAudioChunk 콜백 호출.")
                                filePointer += read
                                onAudioChunk(buffer.copyOf(read)) 
                            }
                        }
                    } else {
                        delay(50) 
                    }
                }
                raf.close()
                Log.d(TAG, "파일 스트리밍 종료: ${fileToTail.name}")
            } catch (e: Exception) {
                Log.e(TAG, "파일 읽기 에러: ${e.message}")
            }
        }
    }

    fun stop() {
        isStreaming = false
        tailingJob?.cancel()
        watcherScope.cancel()
        Log.d(TAG, "폴더 감시 중지: $folderPath")
    }
}
