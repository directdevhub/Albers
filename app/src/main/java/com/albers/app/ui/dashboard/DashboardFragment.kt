package com.albers.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.albers.app.MainActivity
import com.albers.app.R
import com.albers.app.databinding.FragmentDashboardBinding
import com.albers.app.ui.common.toDrawableRes
import com.albers.app.utils.AlbersNotificationHelper
import com.albers.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: DashboardViewModel
    private lateinit var notificationHelper: AlbersNotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        notificationHelper = AlbersNotificationHelper(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDashboardState()

        binding.deviceStateText.setOnClickListener {
            Toast.makeText(requireContext(), viewModel.deviceStateClickMessage(), Toast.LENGTH_SHORT).show()
            viewModel.onDeviceStateClicked()
        }

        binding.pumpNowButton.setOnClickListener {
            Toast.makeText(requireContext(), viewModel.pumpNowClickMessage(), Toast.LENGTH_SHORT).show()
            viewModel.onPumpNowClicked()
        }

        binding.stopButton.setOnClickListener {
            Toast.makeText(requireContext(), viewModel.stopClickMessage(), Toast.LENGTH_SHORT).show()
            viewModel.onStopClicked()
        }

        binding.dashboardBottomNav.systemStatusNavButton.setOnClickListener {
            (requireActivity() as MainActivity).showSystemStatus()
        }
        binding.dashboardBottomNav.settingsNavButton.setOnClickListener {
            (requireActivity() as MainActivity).showSettings()
        }
        binding.dashboardBottomNav.rinseNavButton.setOnClickListener {
            (requireActivity() as MainActivity).showRinse()
        }
        binding.dashboardBottomNav.helpNavButton.setOnClickListener {
            (requireActivity() as MainActivity).showHelp()
        }
    }

    private fun observeDashboardState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.timerText.text = state.countdownText

                    binding.deviceStateText.isEnabled = true
                    binding.deviceStateText.alpha = if (state.isDeviceIlluminated) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.deviceStateLabelText.alpha = binding.deviceStateText.alpha

                    binding.stopButton.isEnabled = true
                    binding.stopButton.alpha = if (state.isStopIlluminated) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.stopButtonLabelText.text = state.stopLabel
                    binding.stopButtonLabelText.alpha = binding.stopButton.alpha

                    binding.timerOverrideButton.visibility =
                        if (state.shouldShowTimerOverride) View.VISIBLE else View.GONE

                    binding.pumpNowButton.isEnabled = true
                    binding.pumpNowButton.text = state.pumpNowLabel
                    binding.pumpNowButton.alpha = when {
                        state.showPumping && state.pumpNowLabel.isBlank() -> DISABLED_ALPHA
                        state.canPumpNow || state.showPumping -> ENABLED_ALPHA
                        else -> DISABLED_ALPHA
                    }
                    binding.pumpNowButton.setBackgroundResource(
                        when {
                            state.showPumping -> R.drawable.bg_button_red
                            state.canPumpNow -> R.drawable.bg_timer_window
                            else -> R.drawable.bg_button_disabled
                        }
                    )

                    binding.dashboardBottomNav.systemStatusNavButton.isEnabled = true
                    binding.dashboardBottomNav.systemStatusNavButton.setImageResource(state.statusBadge.toDrawableRes())
                    binding.dashboardBottomNav.settingsNavButton.isEnabled = true
                    binding.dashboardBottomNav.rinseNavButton.isEnabled = true
                    binding.dashboardBottomNav.helpNavButton.isEnabled = true
                    binding.dashboardBottomNav.systemStatusNavButton.alpha = ENABLED_ALPHA
                    binding.dashboardBottomNav.settingsNavButton.alpha = ENABLED_ALPHA
                    binding.dashboardBottomNav.rinseNavButton.alpha = ENABLED_ALPHA
                    binding.dashboardBottomNav.helpNavButton.alpha = ENABLED_ALPHA
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.albers.app.data.repository.AlbersRepository.appState.collect { state ->
                    notificationHelper.notifyIfCritical(state.faultSummary)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.2f

        fun newInstance(): DashboardFragment = DashboardFragment()
    }
}
