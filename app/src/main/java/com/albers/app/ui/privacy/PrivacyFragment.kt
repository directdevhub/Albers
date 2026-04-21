package com.albers.app.ui.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.albers.app.MainActivity
import com.albers.app.databinding.FragmentPrivacyBinding

class PrivacyFragment : Fragment() {
    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.helpButton.setOnClickListener { (requireActivity() as MainActivity).showHelp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): PrivacyFragment = PrivacyFragment()
    }
}
