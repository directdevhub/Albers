package com.albers.app.data.repository

import android.content.Context
import com.albers.app.data.local.AlbersDatabase
import com.albers.app.data.local.toNotificationEntity
import com.albers.app.data.local.toNotificationItem
import com.albers.app.data.model.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object NotificationStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fallbackNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    private var database: AlbersDatabase? = null

    val notifications: Flow<List<NotificationItem>>
        get() = database
            ?.notificationDao()
            ?.observeNotifications()
            ?.map { entities -> entities.map { it.toNotificationItem() } }
            ?: fallbackNotifications

    fun observeNotification(id: Long): Flow<NotificationItem?> {
        return database
            ?.notificationDao()
            ?.observeNotification(id)
            ?.map { entity -> entity?.toNotificationItem() }
            ?: fallbackNotifications.map { notifications -> notifications.firstOrNull { it.id == id } }
    }

    fun initialize(context: Context) {
        if (database != null) return
        database = AlbersDatabase.getInstance(context)
    }

    fun save(notification: NotificationItem) {
        val dao = database?.notificationDao()
        if (dao == null) {
            fallbackNotifications.value = (fallbackNotifications.value + notification)
                .sortedByDescending { it.createdAtMillis }
            return
        }
        scope.launch {
            dao.insert(notification.toNotificationEntity())
        }
    }

    fun clear() {
        val dao = database?.notificationDao()
        if (dao == null) {
            fallbackNotifications.value = emptyList()
            return
        }
        scope.launch {
            dao.clearAll()
        }
    }
}
