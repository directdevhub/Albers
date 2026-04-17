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
        binding.systemStatusButton.setOnClickListener { (requireActivity() as MainActivity).showSystemStatus() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.startRinseButton.isEnabled = state.canStart
                    binding.startRinseButton.setBackgroundResource(
                        if (state.canStart) R.drawable.bg_button_green else R.drawable.bg_button_disabled
                    )
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
        fun newInstance(): RinseFragment = RinseFragment()
    }
}
