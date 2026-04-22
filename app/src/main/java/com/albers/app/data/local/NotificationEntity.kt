package com.albers.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.albers.app.data.model.NotificationItem
import com.albers.app.data.model.NotificationType

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val createdAtMillis: Long
)

fun NotificationEntity.toNotificationItem(): NotificationItem {
    return NotificationItem(
        id = id,
        type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.UnknownFault),
        title = title,
        message = message,
        createdAtMillis = createdAtMillis
    )
}

fun NotificationItem.toNotificationEntity(): NotificationEntity {
    return NotificationEntity(
        id = id,
        type = type.name,
        title = title,
        message = message,
        createdAtMillis = createdAtMillis
    )
}
