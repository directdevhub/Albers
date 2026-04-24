package com.albers.app.ui.settings

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
import com.albers.app.databinding.FragmentSettingsBinding
import com.albers.app.ui.common.toDrawableRes
import com.albers.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.helpButton.setOnClickListener { (requireActivity() as MainActivity).showHelp() }
        binding.systemNominalIcon.setOnClickListener { (requireActivity() as MainActivity).showSystemStatus() }
        binding.notificationsSettingsRow.setOnClickListener { (requireActivity() as MainActivity).showNotifications() }
        binding.privacySettingsRow.setOnClickListener { (requireActivity() as MainActivity).showPrivacy() }
        binding.voiceControlSettingsRow.setOnClickListener {
            Toast.makeText(requireContext(), "Voice control is listed for future setup.", Toast.LENGTH_SHORT).show()
        }
        binding.interval60Button.setOnClickListener { viewModel.selectInterval(60) }
        binding.interval90Button.setOnClickListener { viewModel.selectInterval(90) }
        binding.interval120Button.setOnClickListener { viewModel.selectInterval(120) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderIntervalSelection(state.selectedIntervalMinutes)
                    binding.systemNominalIcon.setImageResource(state.statusBadge.toDrawableRes())
                }
            }
        }
    }

    private fun renderIntervalSelection(selectedIntervalMinutes: Int) {
        binding.interval60Button.setBackgroundResource(intervalBackground(60, selectedIntervalMinutes))
        binding.interval90Button.setBackgroundResource(intervalBackground(90, selectedIntervalMinutes))
        binding.interval120Button.setBackgroundResource(intervalBackground(120, selectedIntervalMinutes))
    }

    private fun intervalBackground(intervalMinutes: Int, selectedIntervalMinutes: Int): Int {
        return if (intervalMinutes == selectedIntervalMinutes) {
            R.drawable.bg_setting_selected
        } else {
            R.drawable.bg_button_secondary
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}
