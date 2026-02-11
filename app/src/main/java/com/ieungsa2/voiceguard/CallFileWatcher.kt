package com.ieungsa2.voiceguard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile

class CallFileWatcher(
    private val context: Context,
    private val folderPath: String,
    private val onAudioChunk: (String, ByteArray) -> Unit
) {

    private val TAG = "CallFileWatcher"
    private var isStreaming = false
    private var tailingJob: Job? = null
    private val watcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watcherStartTime: Long = 0

    // 앱 내부 캐시 폴더에 복사될 임시 파일
    private val tempLocalFile = File(context.cacheDir, "bcr_tail_temp.wav")

    fun startWatching() {
        watcherStartTime = System.currentTimeMillis()
        Log.d(TAG, "▶ [Root] 실시간 감시 시작 (경로: $folderPath)")
        isStreaming = true
       
        tailingJob = watcherScope.launch {
            var currentTailingPath: String? = null
            var tailingJobChild: Job? = null
           
            while (isStreaming) {
                // 루트 권한으로 최신 .wav 파일명 가져오기
                val latestFileName = getLatestWavWithRoot(folderPath)
                
                if (latestFileName != null) {
                    val fullPath = if (folderPath.endsWith("/")) "$folderPath$latestFileName" else "$folderPath/$latestFileName"
                    
                    if (fullPath != currentTailingPath) {
                        Log.i(TAG, "✨ [Root] 새 파일 발견: $latestFileName")
                        tailingJobChild?.cancel()
                        currentTailingPath = fullPath
                        
                        tailingJobChild = launch {
                            startTailingWithRoot(fullPath, latestFileName)
                        }
                    }
                } else {
                    Log.v(TAG, "🔍 [Root] 감시 중... 후보 없음")
                }
               
                delay(1000) // 1초마다 체크
            }
            tailingJobChild?.cancel()
        }
    }

    private fun getLatestWavWithRoot(path: String): String? {
        return try {
            val cmd = "ls -t $path | grep .wav | head -n 1"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val fileName = reader.readLine()?.trim()
            process.destroy()
            if (fileName.isNullOrBlank()) null else fileName
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Root] ls 실패: ${e.message}")
            null
        }
    }

    private suspend fun startTailingWithRoot(remotePath: String, fileName: String) {
        withContext(Dispatchers.IO) {
            try {
                var lastSize = 0L
                val callInfo = extractCallInfo(fileName)
                
                Log.d(TAG, "🎧 [Root] 스트리밍 분석 시작: $callInfo")
                
                while (isStreaming) {
                    val currentSize = getFileSizeWithRoot(remotePath)

                    if (currentSize > lastSize) {
                        val diff = currentSize - lastSize
                        Log.d(TAG, "📈 [Root 크기 증가] $fileName: $lastSize -> $currentSize (+$diff bytes)")

                        // 루트 권한으로 파일을 로컬 캐시로 복사
                        if (copyFileWithRoot(remotePath, tempLocalFile.absolutePath)) {
                            val raf = RandomAccessFile(tempLocalFile, "r")
                            raf.seek(lastSize)

                            val buffer = ByteArray(4096)
                            var read: Int
                            while (raf.read(buffer).also { read = it } != -1) {
                                onAudioChunk(callInfo, buffer.copyOf(read))
                            }
                            raf.close()
                            lastSize = currentSize
                        } else {
                            Log.e(TAG, "❌ [파일 복사 실패] $remotePath")
                        }
                    } else if (currentSize == 0L) {
                        Log.w(TAG, "⚠️ [파일 크기 0] $fileName - 파일이 아직 생성 중이거나 접근 권한 없음")
                    }
                    delay(500) // 0.5초 대기
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Root] 분석 오류: ${e.message}")
            } finally {
                Log.d(TAG, "⏹ [Root] 스트리밍 분석 종료")
            }
        }
    }

    private fun getFileSizeWithRoot(path: String): Long {
        return try {
            val cmd = "stat -c%s \"$path\""
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val sizeStr = reader.readLine()?.trim()
            val errorMsg = errorReader.readLine()
            process.destroy()

            if (errorMsg != null) {
                Log.e(TAG, "❌ [Root stat 에러] $path: $errorMsg")
            }

            val size = sizeStr?.toLong() ?: 0L
            Log.v(TAG, "📏 [파일 크기] $path: $size bytes")
            size
        } catch (e: Exception) {
            Log.e(TAG, "❌ [stat 예외] $path: ${e.message}")
            0L
        }
    }

    private fun copyFileWithRoot(src: String, dst: String): Boolean {
        return try {
            val cmd = "cp \"$src\" \"$dst\" && chmod 666 \"$dst\""
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val result = process.waitFor()
            val errorMsg = errorReader.readLine()
            process.destroy()

            if (result != 0) {
                Log.e(TAG, "❌ [Root cp 실패] $src -> $dst (exit=$result): $errorMsg")
                return false
            }

            Log.d(TAG, "✅ [Root cp 성공] $src -> $dst")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ [cp 예외] $src: ${e.message}")
            false
        }
    }

    private fun extractCallInfo(fileName: String): String {
        return try {
            val nameWithoutExt = fileName.removeSuffix(".wav")
            val parts = nameWithoutExt.split("_")
            
            val identifier = parts.lastOrNull() ?: fileName
            val direction = if (parts.size >= 2) parts[parts.size - 2] else ""
            
            if (direction == "IN" || direction == "OUT") {
                "$identifier ($direction)"
            } else {
                identifier
            }
        } catch (e: Exception) {
            fileName
        }
    }

    fun stop() {
        isStreaming = false
        tailingJob?.cancel()
        Log.d(TAG, "⏹ 감시 중지")
    }
}
