package com.ieungsa2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DataLeakAlertDao {
    @Insert
    suspend fun insert(alert: DataLeakAlert): Long

    @Query("SELECT * FROM data_leak_alerts ORDER BY timestamp DESC")
    suspend fun getAllAlerts(): List<DataLeakAlert>

    @Query("SELECT * FROM data_leak_alerts WHERE isRead = 0 ORDER BY timestamp DESC")
    suspend fun getUnreadAlerts(): List<DataLeakAlert>

    @Update
    suspend fun update(alert: DataLeakAlert)

    @Query("DELETE FROM data_leak_alerts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM data_leak_alerts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM data_leak_alerts WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
}
