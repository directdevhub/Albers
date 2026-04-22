package com.albers.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAtMillis DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    fun observeNotification(id: Long): Flow<NotificationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
