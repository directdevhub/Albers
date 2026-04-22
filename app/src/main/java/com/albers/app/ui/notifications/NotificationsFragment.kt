package com.albers.app.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.albers.app.MainActivity
import com.albers.app.data.model.NotificationItem
import com.albers.app.databinding.FragmentNotificationsBinding
import com.albers.app.viewmodel.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: NotificationsViewModel
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[NotificationsViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationsAdapter { notification ->
            (requireActivity() as MainActivity).showNotificationDetail(notification.id)
        }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationsRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.clearNotificationsButton.setOnClickListener { viewModel.clearNotifications() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notifications.collect { notifications ->
                    val listItems = notifications.toListItems()
                    adapter.submitList(listItems)
                    binding.emptyNotificationsText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                    binding.notificationsRecyclerView.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun List<NotificationItem>.toListItems(): List<NotificationListItem> {
        val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return groupBy { notification -> notification.createdAtMillis.toDateHeader(dateFormatter) }
            .flatMap { (header, notifications) ->
                listOf(NotificationListItem.Header(header)) +
                    notifications.map { NotificationListItem.Content(it) }
            }
    }

    private fun Long.toDateHeader(dateFormatter: SimpleDateFormat): String {
        val now = Calendar.getInstance()
        val notificationDate = Calendar.getInstance().apply { timeInMillis = this@toDateHeader }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            now.isSameDay(notificationDate) -> "Today"
            yesterday.isSameDay(notificationDate) -> "Yesterday"
            else -> dateFormatter.format(Date(this))
        }
    }

    private fun Calendar.isSameDay(other: Calendar): Boolean {
        return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NotificationsFragment = NotificationsFragment()
    }
}
