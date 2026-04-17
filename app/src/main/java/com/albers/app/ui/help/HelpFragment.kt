package com.albers.app.ui.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.albers.app.databinding.FragmentHelpBinding
import com.albers.app.viewmodel.HelpViewModel
import kotlinx.coroutines.launch

class HelpFragment : Fragment() {
    private var _binding: FragmentHelpBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: HelpViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[HelpViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appState.collect { state ->
                    binding.helpIntroText.text = state.faultSummary.primaryMessage
                    binding.helpConnectionCard.text =
                        "Connection: turn on Albers_BLE_BAL3, pair with PIN 333333, then tap Find ALBERS Device. If disconnected, return to Start and retry."
                    binding.helpBatteryCard.text =
                        "Battery: low battery appears at 10% or less. Critical battery appears at 5% or less. Battery failure requires emergency battery guidance."
                    binding.helpPumpCard.text =
                        "Pump: one failed pump allows rinse if another pump is operable. Both failed pumps disable automatic pumping and rinse/sanitize."
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): HelpFragment = HelpFragment()
    }
}
