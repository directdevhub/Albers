package com.albers.app.ui.notifications

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.albers.app.R
import com.albers.app.data.model.NotificationItem
import com.albers.app.data.model.NotificationType
import com.albers.app.databinding.ItemNotificationsBinding
import com.albers.app.databinding.ItemNotificationsHeaderBinding

class NotificationsAdapter(
    private val onNotificationClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> VIEW_TYPE_HEADER
            is NotificationListItem.Content -> VIEW_TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemNotificationsHeaderBinding.inflate(inflater, parent, false)
            )

            else -> ContentViewHolder(
                ItemNotificationsBinding.inflate(inflater, parent, false),
                onNotificationClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NotificationListItem.Content -> (holder as ContentViewHolder).bind(item.notification)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemNotificationsHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationListItem.Header) {
            binding.notificationDate.text = item.label
        }
    }

    private class ContentViewHolder(
        private val binding: ItemNotificationsBinding,
        private val onNotificationClick: (NotificationItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: NotificationItem) {
            binding.notificationIcon.setImageResource(notification.type.toIconRes())
            binding.notificationTitle.text = notification.title
            binding.notificationDetails.text = notification.message
            binding.notificationTime.text = DateUtils.getRelativeTimeSpanString(
                notification.createdAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            binding.root.setOnClickListener { onNotificationClick(notification) }
        }
    }

    private companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTENT = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationListItem>() {
            override fun areItemsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return when {
                    oldItem is NotificationListItem.Header && newItem is NotificationListItem.Header ->
                        oldItem.label == newItem.label

                    oldItem is NotificationListItem.Content && newItem is NotificationListItem.Content ->
                        oldItem.notification.id == newItem.notification.id

                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

fun NotificationType.toIconRes(): Int {
    return when (this) {
        NotificationType.BatteryLow,
        NotificationType.BatteryCritical,
        NotificationType.BatteryFailure -> R.drawable.ic_battery_error

        NotificationType.PumpCycleCompleted,
        NotificationType.ReconnectSuccess -> R.drawable.ic_notification_success

        NotificationType.PumpError,
        NotificationType.ConnectionLost,
        NotificationType.OfflineDiagnostic,
        NotificationType.UnknownFault -> R.drawable.ic_notification_error
    }
}
