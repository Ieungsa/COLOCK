package com.ieungsa.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 탐지 이력 DAO (Data Access Object)
 */
@Dao
interface DetectionHistoryDao {

    /**
     * 탐지 이력 추가
     */
    @Insert
    suspend fun insert(detectionHistory: DetectionHistory)

    /**
     * 모든 탐지 이력 조회 (최신순)
     */
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DetectionHistory>>

    /**
     * 최근 N개 탐지 이력 조회
     */
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDetections(limit: Int = 10): Flow<List<DetectionHistory>>

    /**
     * 총 탐지 횟수
     */
    @Query("SELECT COUNT(*) FROM detection_history")
    fun getDetectionCount(): Flow<Int>

    /**
     * 오늘 탐지 횟수
     */
    @Query("SELECT COUNT(*) FROM detection_history WHERE timestamp >= :startOfDay")
    fun getTodayDetectionCount(startOfDay: Long): Flow<Int>

    /**
     * 최근 탐지 시간
     */
    @Query("SELECT MAX(timestamp) FROM detection_history")
    fun getLastDetectionTime(): Flow<Long?>

    /**
     * 평균 신뢰도
     */
    @Query("SELECT AVG(confidence) FROM detection_history")
    fun getAverageConfidence(): Flow<Float?>

    /**
     * 모든 이력 삭제
     */
    @Query("DELETE FROM detection_history")
    suspend fun deleteAll()

    /**
     * 특정 기간의 탐지 이력 삭제
     */
    @Query("DELETE FROM detection_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
