package com.albers.app.ui.notifications

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.albers.app.R
import com.albers.app.databinding.FragmentNotificationDetailBinding
import com.albers.app.viewmodel.NotificationDetailViewModel
import kotlinx.coroutines.launch

class NotificationDetailFragment : Fragment() {
    private var _binding: FragmentNotificationDetailBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: NotificationDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[NotificationDetailViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }

        val notificationId = requireArguments().getLong(ARG_NOTIFICATION_ID)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeNotification(notificationId).collect { notification ->
                    if (notification == null) {
                        binding.notificationIcon.setImageResource(R.drawable.ic_notification_error)
                        binding.notificationTitle.text = getString(R.string.notification_details)
                        binding.notificationDetails.text = getString(R.string.no_notification_history_yet)
                        binding.notificationTime.text = ""
                    } else {
                        binding.notificationIcon.setImageResource(notification.type.toIconRes())
                        binding.notificationTitle.text = notification.title
                        binding.notificationDetails.text = notification.message
                        binding.notificationTime.text = DateFormat.format(
                            "MMM d, yyyy h:mm a",
                            notification.createdAtMillis
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_NOTIFICATION_ID = "notification_id"

        fun newInstance(notificationId: Long): NotificationDetailFragment {
            return NotificationDetailFragment().apply {
                arguments = Bundle().apply { putLong(ARG_NOTIFICATION_ID, notificationId) }
            }
        }
    }
}
