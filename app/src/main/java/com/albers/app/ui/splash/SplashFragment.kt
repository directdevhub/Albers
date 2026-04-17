package com.albers.app.ui.splash

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.albers.app.MainActivity
import com.albers.app.databinding.FragmentSplashBinding

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val handler = Handler(Looper.getMainLooper())
    private val navigateToConnect = Runnable {
        if (isAdded) {
            (requireActivity() as MainActivity).showConnect()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handler.postDelayed(navigateToConnect, SPLASH_DURATION_MS)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(navigateToConnect)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1_200L

        fun newInstance(): SplashFragment = SplashFragment()
    }
}
