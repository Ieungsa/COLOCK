package com.ieungsa.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 스캠 전화번호 DAO
 */
@Dao
interface ScamNumberDao {

    /**
     * 스캠 번호 추가/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scamNumber: ScamNumber)

    /**
     * 여러 스캠 번호 한번에 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scamNumbers: List<ScamNumber>)

    /**
     * 스캠 번호 업데이트
     */
    @Update
    suspend fun update(scamNumber: ScamNumber)

    /**
     * 전화번호로 조회
     */
    @Query("SELECT * FROM scam_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun getByPhoneNumber(phoneNumber: String): ScamNumber?

    /**
     * 전화번호가 스캠인지 확인
     */
    @Query("SELECT EXISTS(SELECT 1 FROM scam_numbers WHERE phoneNumber = :phoneNumber)")
    suspend fun isScamNumber(phoneNumber: String): Boolean

    /**
     * 모든 스캠 번호 조회
     */
    @Query("SELECT * FROM scam_numbers ORDER BY riskLevel DESC, lastReportedAt DESC")
    fun getAllScamNumbers(): Flow<List<ScamNumber>>

    /**
     * 위험도별 조회
     */
    @Query("SELECT * FROM scam_numbers WHERE riskLevel = :riskLevel ORDER BY lastReportedAt DESC")
    fun getByRiskLevel(riskLevel: Int): Flow<List<ScamNumber>>

    /**
     * 카테고리별 조회
     */
    @Query("SELECT * FROM scam_numbers WHERE category = :category ORDER BY lastReportedAt DESC")
    fun getByCategory(category: ScamCategory): Flow<List<ScamNumber>>

    /**
     * 사용자 차단 목록 조회
     */
    @Query("SELECT * FROM scam_numbers WHERE isUserBlocked = 1 ORDER BY registeredAt DESC")
    fun getUserBlockedNumbers(): Flow<List<ScamNumber>>

    /**
     * 총 스캠 번호 개수
     */
    @Query("SELECT COUNT(*) FROM scam_numbers")
    fun getScamNumberCount(): Flow<Int>

    /**
     * 스캠 번호 삭제
     */
    @Query("DELETE FROM scam_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByPhoneNumber(phoneNumber: String)

    /**
     * 모든 스캠 번호 삭제
     */
    @Query("DELETE FROM scam_numbers")
    suspend fun deleteAll()

    /**
     * 사용자 차단 번호만 삭제
     */
    @Query("DELETE FROM scam_numbers WHERE isUserBlocked = 1")
    suspend fun deleteUserBlocked()
}
