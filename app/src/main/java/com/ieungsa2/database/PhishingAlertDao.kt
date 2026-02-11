package com.ieungsa2.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhishingAlertDao {
    
    @Query("SELECT * FROM phishing_alerts WHERE type = :type ORDER BY timestamp DESC")
    fun getAlertsByType(type: String): Flow<List<PhishingAlert>>

    @Query("SELECT COUNT(*) FROM phishing_alerts WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT * FROM phishing_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<PhishingAlert>>
    
    @Query("SELECT * FROM phishing_alerts WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadAlerts(): Flow<List<PhishingAlert>>
    
    @Query("SELECT COUNT(*) FROM phishing_alerts WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
    
    @Query("SELECT COUNT(*) FROM phishing_alerts")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT * FROM phishing_alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): PhishingAlert?
    
    @Insert
    suspend fun insertAlert(alert: PhishingAlert): Long
    
    @Update
    suspend fun updateAlert(alert: PhishingAlert)
    
    @Query("UPDATE phishing_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)
    
    @Query("UPDATE phishing_alerts SET isReported = 1 WHERE id = :id")
    suspend fun markAsReported(id: Long)
    
    @Query("DELETE FROM phishing_alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)
    
    @Query("DELETE FROM phishing_alerts")
    suspend fun deleteAllAlerts()

    @Query("SELECT * FROM phishing_alerts ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAlert(): PhishingAlert?

    @Query("SELECT * FROM phishing_alerts WHERE sender = :sender ORDER BY timestamp DESC LIMIT 1")
    suspend fun getAlertBySender(sender: String): PhishingAlert?
    
    @Query("""
        SELECT * FROM phishing_alerts 
        WHERE messageBody LIKE '%' || :searchQuery || '%' 
           OR sender LIKE '%' || :searchQuery || '%'
           OR detectedUrl LIKE '%' || :searchQuery || '%'
        ORDER BY timestamp DESC
    """)
    fun searchAlerts(searchQuery: String): Flow<List<PhishingAlert>>
}