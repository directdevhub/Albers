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
import com.albers.app.databinding.FragmentNotificationsBinding
import com.albers.app.viewmodel.NotificationsViewModel
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: NotificationsViewModel

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
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appState.collect { state ->
                    val notifications = state.notifications
                    binding.latestWarningText.text = state.faultSummary.primaryMessage
                    binding.historyItemOneText.text = notifications.getOrNull(0)?.toDisplayText()
                        ?: "No notification history yet"
                    binding.historyItemTwoText.text = notifications.getOrNull(1)?.toDisplayText()
                        ?: "Reconnect, battery, and pump alerts will appear here"
                    binding.historyItemThreeText.text = notifications.getOrNull(2)?.toDisplayText()
                        ?: "Stored diagnostics will appear after reconnect"
                }
            }
        }
    }

    private fun com.albers.app.data.model.NotificationItem.toDisplayText(): String {
        return "$title • $message"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): NotificationsFragment = NotificationsFragment()
    }
}
