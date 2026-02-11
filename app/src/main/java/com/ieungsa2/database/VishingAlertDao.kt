package com.ieungsa2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface VishingAlertDao {

    @Insert
    suspend fun insert(alert: VishingAlert): Long

    @Query("SELECT * FROM vishing_alerts ORDER BY timestamp DESC")
    suspend fun getAllAlerts(): List<VishingAlert>

    @Query("SELECT * FROM vishing_alerts WHERE id = :alertId")
    suspend fun getAlertById(alertId: Long): VishingAlert?

    @Update
    suspend fun update(alert: VishingAlert)

    @Query("DELETE FROM vishing_alerts WHERE id = :alertId")
    suspend fun delete(alertId: Long)

    @Query("DELETE FROM vishing_alerts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vishing_alerts")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM vishing_alerts WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("SELECT * FROM vishing_alerts WHERE riskLevel = :level ORDER BY timestamp DESC")
    suspend fun getAlertsByRiskLevel(level: String): List<VishingAlert>
}
