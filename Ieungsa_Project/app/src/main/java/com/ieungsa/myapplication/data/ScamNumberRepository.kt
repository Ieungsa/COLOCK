package com.ieungsa.myapplication.data

import kotlinx.coroutines.flow.Flow

/**
 * 스캠 전화번호 Repository
 */
class ScamNumberRepository(private val dao: ScamNumberDao) {

    /**
     * 전화번호가 스캠인지 확인
     */
    suspend fun isScamNumber(phoneNumber: String): Boolean {
        // 전화번호 정규화 (하이픈 제거 등)
        val normalized = normalizePhoneNumber(phoneNumber)
        return dao.isScamNumber(normalized)
    }

    /**
     * 전화번호로 스캠 정보 조회
     */
    suspend fun getScamInfo(phoneNumber: String): ScamNumber? {
        val normalized = normalizePhoneNumber(phoneNumber)
        return dao.getByPhoneNumber(normalized)
    }

    /**
     * 스캠 번호 추가
     */
    suspend fun addScamNumber(scamNumber: ScamNumber) {
        val normalized = scamNumber.copy(
            phoneNumber = normalizePhoneNumber(scamNumber.phoneNumber)
        )
        dao.insert(normalized)
    }

    /**
     * 여러 스캠 번호 추가
     */
    suspend fun addScamNumbers(scamNumbers: List<ScamNumber>) {
        val normalized = scamNumbers.map { it.copy(
            phoneNumber = normalizePhoneNumber(it.phoneNumber)
        )}
        dao.insertAll(normalized)
    }

    /**
     * 모든 스캠 번호 조회
     */
    fun getAllScamNumbers(): Flow<List<ScamNumber>> = dao.getAllScamNumbers()

    /**
     * 위험도별 조회
     */
    fun getByRiskLevel(riskLevel: Int): Flow<List<ScamNumber>> = dao.getByRiskLevel(riskLevel)

    /**
     * 사용자 차단 목록 조회
     */
    fun getUserBlockedNumbers(): Flow<List<ScamNumber>> = dao.getUserBlockedNumbers()

    /**
     * 스캠 번호 개수
     */
    fun getScamNumberCount(): Flow<Int> = dao.getScamNumberCount()

    /**
     * 스캠 번호 삭제
     */
    suspend fun deleteScamNumber(phoneNumber: String) {
        val normalized = normalizePhoneNumber(phoneNumber)
        dao.deleteByPhoneNumber(normalized)
    }

    /**
     * 모든 스캠 번호 삭제
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * 전화번호 정규화 (하이픈, 공백 제거)
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }

    /**
     * 샘플 스캠 전화번호 데이터 초기화
     */
    suspend fun initializeSampleData() {
        // 이미 데이터가 있으면 초기화하지 않음
        if (dao.getScamNumberCount().toString().toIntOrNull() ?: 0 > 0) {
            return
        }

        val sampleScamNumbers = listOf(
            ScamNumber(
                phoneNumber = "02-1234-5678",
                riskLevel = 3,
                reportCount = 523,
                category = ScamCategory.VOICE_PHISHING,
                description = "경찰청 사칭 보이스피싱"
            ),
            ScamNumber(
                phoneNumber = "1588-1234",
                riskLevel = 3,
                reportCount = 412,
                category = ScamCategory.LOAN_FRAUD,
                description = "저금리 대출 사기"
            ),
            ScamNumber(
                phoneNumber = "010-1234-5678",
                riskLevel = 2,
                reportCount = 89,
                category = ScamCategory.INVESTMENT_FRAUD,
                description = "가상화폐 투자 사기"
            ),
            ScamNumber(
                phoneNumber = "031-9876-5432",
                riskLevel = 3,
                reportCount = 678,
                category = ScamCategory.IMPERSONATION,
                description = "금융감독원 사칭"
            ),
            ScamNumber(
                phoneNumber = "02-9999-8888",
                riskLevel = 2,
                reportCount = 234,
                category = ScamCategory.SMISHING,
                description = "택배 문자 스미싱"
            ),
            ScamNumber(
                phoneNumber = "070-1234-5678",
                riskLevel = 1,
                reportCount = 45,
                category = ScamCategory.SPAM,
                description = "보험 텔레마케팅"
            ),
            ScamNumber(
                phoneNumber = "02-5555-6666",
                riskLevel = 3,
                reportCount = 892,
                category = ScamCategory.VOICE_PHISHING,
                description = "검찰청 사칭 보이스피싱"
            ),
            ScamNumber(
                phoneNumber = "1599-9999",
                riskLevel = 2,
                reportCount = 156,
                category = ScamCategory.LOAN_FRAUD,
                description = "불법 대부업체"
            )
        )

        addScamNumbers(sampleScamNumbers)
    }
}
