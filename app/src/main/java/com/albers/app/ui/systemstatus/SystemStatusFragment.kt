package com.albers.app.ui.systemstatus

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
import com.albers.app.databinding.FragmentSystemStatusBinding
import com.albers.app.ui.common.toDrawableRes
import com.albers.app.viewmodel.SystemStatusViewModel
import kotlinx.coroutines.launch

class SystemStatusFragment : Fragment() {
    private var _binding: FragmentSystemStatusBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SystemStatusViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SystemStatusViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSystemStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.helpButton.setOnClickListener { (requireActivity() as MainActivity).showHelp() }
        binding.systemNominalIcon.setOnClickListener { (requireActivity() as MainActivity).showSystemStatus() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.systemSummaryText.text = state.summary
                    binding.systemSummaryText.setTextColor(
                        resources.getColor(if (state.isNominal) R.color.status_on else R.color.status_warning, null)
                    )
                    binding.systemNominalIcon.setImageResource(state.statusBadge.toDrawableRes())
                    binding.pumpOneStateText.text = state.pump1
                    binding.pumpTwoStateText.text = state.pump2
                    binding.pumpOneIcon.setImageResource(
                        if (state.pump1Failed) R.drawable.ic_pump_error_indicator else R.drawable.ic_pump
                    )
                    binding.pumpTwoIcon.setImageResource(
                        if (state.pump2Failed) R.drawable.ic_pump_error_indicator else R.drawable.ic_pump
                    )
                    binding.pumpOneStateText.setTextColor(
                        resources.getColor(if (state.pump1Failed) R.color.status_error else R.color.status_on, null)
                    )
                    binding.pumpTwoStateText.setTextColor(
                        resources.getColor(if (state.pump2Failed) R.color.status_error else R.color.status_on, null)
                    )
                    binding.batteryStatusValueText.text = "${state.battery} • ${state.batteryMode}"
                    binding.batteryStatusValueText.setTextColor(
                        resources.getColor(if (state.isBatteryLow) R.color.status_error else R.color.white, null)
                    )
                    binding.pressureStatusText.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SystemStatusFragment = SystemStatusFragment()
    }
}
