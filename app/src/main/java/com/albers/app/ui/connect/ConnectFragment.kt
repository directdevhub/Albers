package com.albers.app.ui.connect

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.albers.app.MainActivity
import com.albers.app.R
import com.albers.app.ble.BleSessionPhase
import com.albers.app.databinding.FragmentConnectBinding
import com.albers.app.viewmodel.ConnectViewModel
import kotlinx.coroutines.launch

class ConnectFragment : Fragment() {
    private var _binding: FragmentConnectBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: ConnectViewModel
    private var hasRequestedInitialConnectionFlow = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (hasRequiredBlePermissions()) {
            viewModel.startConnectionFlow()
        } else {
            val deniedPermissions = permissions
                .filterValues { granted -> !granted }
                .keys
                .joinToString()
            binding.statusText.text = "Bluetooth and Location permissions are required to scan for ALBERS. Denied: $deniedPermissions"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ConnectViewModel::class.java]

        binding.scanButton.setOnClickListener {
            requestConnectionFlow()
        }

        binding.connectButton.setOnClickListener {
            viewModel.connectSelectedDevice()
        }

        binding.openDashboardButton.setOnClickListener {
            (requireActivity() as MainActivity).showDashboard(addToBackStack = false)
        }

        binding.connectBottomNav.helpNavButton.setOnClickListener {
            (requireActivity() as MainActivity).showHelp()
        }

        observeConnectState()
        refreshButtonBackgrounds()
        binding.root.post {
            if (!hasRequestedInitialConnectionFlow) {
                hasRequestedInitialConnectionFlow = true
                requestConnectionFlow()
            }
        }
    }

    private fun requestConnectionFlow() {
        if (hasRequiredBlePermissions()) {
            viewModel.startConnectionFlow()
        } else {
            permissionLauncher.launch(requiredBlePermissions())
        }
    }

    private fun requiredBlePermissions(): Array<String> {
        val permissions = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        return permissions.toTypedArray()
    }

    private fun hasRequiredBlePermissions(): Boolean {
        return requiredBlePermissions().all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun refreshButtonBackgrounds() {
        binding.scanButton.alpha = if (binding.scanButton.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        binding.startStopButton.alpha = DISABLED_ALPHA
        binding.startStopButton.isEnabled = false

        binding.connectButton.setBackgroundResource(
            if (binding.connectButton.isEnabled) R.drawable.bg_button_secondary else R.drawable.bg_button_disabled
        )
        binding.openDashboardButton.visibility = View.GONE

        val enabledTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val disabledTextColor = ContextCompat.getColor(requireContext(), R.color.disabled_text)
        binding.connectButton.setTextColor(if (binding.connectButton.isEnabled) enabledTextColor else disabledTextColor)

        binding.connectBottomNav.systemStatusNavButton.isEnabled = false
        binding.connectBottomNav.settingsNavButton.isEnabled = false
        binding.connectBottomNav.rinseNavButton.isEnabled = false
        binding.connectBottomNav.helpNavButton.isEnabled = true
        binding.connectBottomNav.systemStatusNavButton.alpha = DISABLED_ALPHA
        binding.connectBottomNav.settingsNavButton.alpha = DISABLED_ALPHA
        binding.connectBottomNav.rinseNavButton.alpha = DISABLED_ALPHA
        binding.connectBottomNav.helpNavButton.alpha = ENABLED_ALPHA
    }

    private fun observeConnectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val locationHint = if (state.phase == BleSessionPhase.Scanning && !isLocationEnabled()) {
                        " If no device appears, turn phone Location/GPS on and scan again."
                    } else {
                        ""
                    }
                    binding.statusText.text = state.statusMessage + locationHint
                    binding.selectedDeviceText.text = state.selectedDeviceLabel
                    binding.scanButton.isEnabled = state.canScan
                    binding.connectButton.isEnabled = state.canConnect
                    binding.openDashboardButton.isEnabled = false
                    refreshButtonBackgrounds()

                    if (state.shouldOpenDashboard) {
                        viewModel.consumeDashboardNavigation()
                        (requireActivity() as MainActivity).showDashboard(addToBackStack = false)
                    }
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

        fun newInstance(): ConnectFragment = ConnectFragment()
    }
}
