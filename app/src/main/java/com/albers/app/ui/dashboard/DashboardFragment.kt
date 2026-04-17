package com.albers.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
                    binding.deviceStateText.text = state.deviceState
                    binding.timerText.text = state.countdownText
                    binding.pumpStatusText.text = state.pumpStatus
                    binding.pumpStatusText.setBackgroundResource(
                        if (state.isCritical) R.drawable.bg_button_red else R.drawable.bg_status_chip_on
                    )
                    binding.dashboardWarningText.text = state.warningText
                    binding.dashboardWarningText.visibility = if (state.showWarning) View.VISIBLE else View.GONE
                    if (state.isCritical && state.showWarning) {
                        if (binding.dashboardWarningText.animation == null) {
                            binding.dashboardWarningText.startAnimation(createAttentionAnimation())
                        }
                    } else {
                        binding.dashboardWarningText.clearAnimation()
                    }
                    binding.pumpingStateBanner.visibility = if (state.showPumping) View.VISIBLE else View.GONE
                    if (state.showPumping) {
                        if (binding.pumpingStateBanner.animation == null) {
                            binding.pumpingStateBanner.startAnimation(createAttentionAnimation())
                        }
                    } else {
                        binding.pumpingStateBanner.clearAnimation()
                    }
                    binding.pumpNowButton.isEnabled = state.canPumpNow
                    binding.pumpNowButton.setBackgroundResource(
                        if (state.canPumpNow) R.drawable.bg_button_secondary else R.drawable.bg_button_disabled
                    )
                    binding.dashboardBottomNav.rinseNavButton.isEnabled = state.canRinseSanitize
                    binding.dashboardBottomNav.rinseNavButton.alpha = if (state.canRinseSanitize) 1f else 0.45f
                    binding.pumpStatusText.setCompoundDrawablesWithIntrinsicBounds(
                        when (state.indicator) {
                            DashboardIndicator.Nominal -> R.drawable.ic_system_status
                            DashboardIndicator.Hazard -> R.drawable.ic_hazard_warning
                            DashboardIndicator.LowBattery -> R.drawable.ic_low_battery
                            DashboardIndicator.CriticalBattery -> R.drawable.ic_emergency_battery
                            DashboardIndicator.EmergencyBattery -> R.drawable.ic_emergency_battery
                        },
                        0,
                        0,
                        0
                    )
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

    private fun createAttentionAnimation(): Animation {
        return AlphaAnimation(1f, 0.35f).apply {
            duration = 500L
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
    }

    override fun onDestroyView() {
        binding.pumpingStateBanner.clearAnimation()
        binding.dashboardWarningText.clearAnimation()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): DashboardFragment = DashboardFragment()
    }
}
