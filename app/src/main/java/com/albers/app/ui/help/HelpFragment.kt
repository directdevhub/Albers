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
                        "Connection: make sure the ALBERS battery is ON, BLE is enabled on the phone, ALBERS is selected, and pairing/authentication with PIN 333333 completes if Android asks. If protected reads fail, forget the device in Android Bluetooth settings, pair again, then retry in the app."
                    binding.helpBatteryCard.text =
                        "Battery: low battery appears at 10% or less. Critical battery appears at 5% or less. Emergency battery means external power guidance should be followed, and battery failure means the battery path should be inspected before continued use."
                    binding.helpPumpCard.text =
                        "Pump: PUMP NOW uses the selected automatic interval command immediately. One failed pump can still allow rinse if the other pump is operable. Both failed pumps disable rinse and automatic pump actions."
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
