package com.albers.app.ui.notifications

import com.albers.app.data.model.NotificationItem

sealed class NotificationListItem {
    data class Header(val label: String) : NotificationListItem()
    data class Content(val notification: NotificationItem) : NotificationListItem()
}
