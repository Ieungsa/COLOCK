package com.ieungsa.myapplication.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 탐지 이력 Repository
 * DAO와 UI 레이어 사이의 중간 계층
 */
class DetectionRepository(private val dao: DetectionHistoryDao) {

    /**
     * 모든 탐지 이력 조회 (최신순)
     */
    fun getAllDetections(): Flow<List<DetectionHistory>> = dao.getAll()

    /**
     * 최근 N개 탐지 이력 조회
     */
    fun getRecentDetections(limit: Int = 10): Flow<List<DetectionHistory>> =
        dao.getRecentDetections(limit)

    /**
     * 총 탐지 횟수
     */
    fun getDetectionCount(): Flow<Int> = dao.getDetectionCount()

    /**
     * 오늘 탐지 횟수
     */
    fun getTodayDetectionCount(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return dao.getTodayDetectionCount(calendar.timeInMillis)
    }

    /**
     * 최근 탐지 시간
     */
    fun getLastDetectionTime(): Flow<Long?> = dao.getLastDetectionTime()

    /**
     * 평균 신뢰도
     */
    fun getAverageConfidence(): Flow<Float?> = dao.getAverageConfidence()

    /**
     * 탐지 이력 추가
     */
    suspend fun insertDetection(detectionHistory: DetectionHistory) {
        dao.insert(detectionHistory)
    }

    /**
     * 모든 이력 삭제
     */
    suspend fun deleteAllDetections() {
        dao.deleteAll()
    }

    /**
     * 30일 이전 이력 삭제
     */
    suspend fun deleteOldDetections() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteOlderThan(thirtyDaysAgo)
    }
}
