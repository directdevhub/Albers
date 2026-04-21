package com.albers.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.albers.app.MainActivity
import com.albers.app.R
import com.albers.app.databinding.FragmentDashboardBinding
import com.albers.app.utils.AlbersNotificationHelper
import com.albers.app.viewmodel.DashboardIndicator
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

        binding.pumpNowButton.setOnClickListener {
            viewModel.onPumpNowClicked()
        }

        binding.stopButton.setOnClickListener {
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

                    binding.deviceStateText.isEnabled = state.deviceState == "ON"
                    binding.deviceStateText.alpha = if (binding.deviceStateText.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.stopButton.isEnabled = state.showPumping
                    binding.stopButton.alpha = if (binding.stopButton.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA

                    binding.timerOverrideButton.visibility = if (state.showPumping) View.GONE else View.VISIBLE

                    binding.pumpNowButton.isEnabled = state.canPumpNow
                    binding.pumpNowButton.text = if (state.showPumping) "PUMPING" else getString(R.string.pump_now)
                    binding.pumpNowButton.setBackgroundResource(
                        when {
                            state.showPumping -> R.drawable.bg_button_red
                            state.canPumpNow -> R.drawable.bg_timer_window
                            else -> R.drawable.bg_button_disabled
                        }
                    )

                    binding.dashboardBottomNav.systemStatusNavButton.isEnabled = true
                    binding.dashboardBottomNav.systemStatusNavButton.setImageResource(state.indicator.toSystemStatusIconRes())
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

private fun DashboardIndicator.toSystemStatusIconRes(): Int {
    return when (this) {
        DashboardIndicator.Nominal -> R.drawable.ic_system_icon
        DashboardIndicator.Hazard -> R.drawable.ic_pump_error
        DashboardIndicator.LowBattery -> R.drawable.ic_low_battery
        DashboardIndicator.CriticalBattery -> R.drawable.ic_low_battery
        DashboardIndicator.EmergencyBattery -> R.drawable.ic_emgency_battery
    }
}
