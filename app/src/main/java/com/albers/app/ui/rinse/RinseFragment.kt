package com.albers.app.ui.rinse

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
import com.albers.app.databinding.FragmentRinseBinding
import com.albers.app.viewmodel.RinseStatusIcon
import com.albers.app.viewmodel.RinseViewModel
import kotlinx.coroutines.launch

class RinseFragment : Fragment() {
    private var _binding: FragmentRinseBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: RinseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[RinseViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRinseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.helpButton.setOnClickListener { (requireActivity() as MainActivity).showHelp() }
        binding.systemNominalIcon.setOnClickListener { (requireActivity() as MainActivity).showSystemStatus() }
        binding.settingsNavButton.setOnClickListener { (requireActivity() as MainActivity).showSettings() }
        binding.startRinseButton.setOnClickListener { viewModel.startRinseCycle() }
        binding.emergencyStopRinseButton.setOnClickListener { viewModel.emergencyStop() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.startRinseButton.isEnabled = state.canStart
                    binding.emergencyStopRinseButton.isEnabled = state.canStart
                    binding.startRinseButton.alpha = if (state.canStart) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.emergencyStopRinseButton.alpha = if (state.canStart) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.rinseTimerText.alpha = if (state.canStart) ENABLED_ALPHA else DISABLED_ALPHA
                    binding.systemNominalIcon.setImageResource(state.statusIcon.toDrawableRes())
                    binding.rinseAvailabilityText.text = state.availabilityMessage
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

        fun newInstance(): RinseFragment = RinseFragment()
    }
}

private fun RinseStatusIcon.toDrawableRes(): Int {
    return when (this) {
        RinseStatusIcon.Nominal -> R.drawable.ic_system_icon
        RinseStatusIcon.LowBattery -> R.drawable.ic_low_battery
        RinseStatusIcon.EmergencyBattery -> R.drawable.ic_emgency_battery
        RinseStatusIcon.PumpError -> R.drawable.ic_pump_error
    }
}
