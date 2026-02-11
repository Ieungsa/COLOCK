package com.ieungsa2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FamilyVerifyLogDao {
    @Insert
    suspend fun insert(log: FamilyVerifyLog): Long

    @Query("SELECT * FROM family_verify_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<FamilyVerifyLog>

    @Query("SELECT * FROM family_verify_logs WHERE isRead = 0 ORDER BY timestamp DESC")
    suspend fun getUnreadLogs(): List<FamilyVerifyLog>

    @Update
    suspend fun update(log: FamilyVerifyLog)

    @Query("UPDATE family_verify_logs SET status = :status WHERE requestId = :requestId")
    suspend fun updateStatus(requestId: String, status: String)

    @Query("DELETE FROM family_verify_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM family_verify_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM family_verify_logs WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
}
