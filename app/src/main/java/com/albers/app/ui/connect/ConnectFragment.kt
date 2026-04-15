package com.albers.app.ui.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.albers.app.MainActivity
import com.albers.app.R
import com.albers.app.ble.AlbersBleManager
import com.albers.app.ble.BleConnectionState
import com.albers.app.databinding.FragmentConnectBinding
import java.util.UUID

class ConnectFragment : Fragment(), AlbersBleManager.Callback {
    private var _binding: FragmentConnectBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var bleManager: AlbersBleManager? = null
    private var selectedDevice: BluetoothDevice? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startBleScan()
        } else {
            binding.statusText.text = "Bluetooth permissions are required to scan for ALBERS."
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
        bleManager = AlbersBleManager(requireContext(), this)

        binding.scanButton.setOnClickListener {
            if (hasRequiredBlePermissions()) {
                startBleScan()
            } else {
                permissionLauncher.launch(requiredBlePermissions())
            }
        }

        binding.connectButton.setOnClickListener {
            connectToSelectedDevice()
        }

        binding.openDashboardButton.setOnClickListener {
            (requireActivity() as MainActivity).showDashboard()
        }

        refreshButtonBackgrounds()
    }

    private fun startBleScan() {
        selectedDevice = null
        binding.selectedDeviceText.text = "Selected device: none"
        binding.connectButton.isEnabled = false
        binding.openDashboardButton.isEnabled = false
        binding.statusText.text = "Scanning for ALBERS..."
        refreshButtonBackgrounds()
        bleManager?.startScan()
    }

    @SuppressLint("MissingPermission")
    private fun connectToSelectedDevice() {
        val device = selectedDevice
        if (device == null) {
            binding.statusText.text = "Scan and select an ALBERS device first."
            return
        }

        binding.statusText.text = "Connecting to ${device.name ?: device.address}..."
        binding.connectButton.isEnabled = false
        refreshButtonBackgrounds()
        bleManager?.connect(device)
    }

    private fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasRequiredBlePermissions(): Boolean {
        return requiredBlePermissions().all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PermissionChecker.PERMISSION_GRANTED
        }
    }

    override fun onScanStateChanged(isScanning: Boolean) {
        runOnUiThread {
            binding.scanButton.isEnabled = !isScanning
            if (!isScanning && selectedDevice == null) {
                binding.statusText.text = "No ALBERS device found yet."
            }
            refreshButtonBackgrounds()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(device: BluetoothDevice) {
        runOnUiThread {
            selectedDevice = device
            val deviceLabel = device.name ?: device.address
            binding.selectedDeviceText.text = "Selected device: $deviceLabel"
            binding.statusText.text = "ALBERS device found. Tap Connect."
            binding.connectButton.isEnabled = true
            refreshButtonBackgrounds()
        }
    }

    override fun onConnectionStateChanged(state: BleConnectionState) {
        runOnUiThread {
            when (state) {
                BleConnectionState.Connected -> {
                    binding.statusText.text = "Connected. Discovering services..."
                    binding.connectButton.isEnabled = false
                }

                BleConnectionState.Connecting -> {
                    binding.statusText.text = "Connecting..."
                    binding.connectButton.isEnabled = false
                }

                BleConnectionState.Disconnecting -> {
                    binding.statusText.text = "Disconnecting..."
                    binding.openDashboardButton.isEnabled = false
                }

                BleConnectionState.Disconnected -> {
                    binding.statusText.text = "Disconnected."
                    binding.connectButton.isEnabled = selectedDevice != null
                    binding.openDashboardButton.isEnabled = false
                }
            }
            refreshButtonBackgrounds()
        }
    }

    override fun onServicesDiscovered(serviceUuids: List<UUID>) {
        runOnUiThread {
            binding.statusText.text = "Connected. ${serviceUuids.size} GATT services discovered."
            binding.openDashboardButton.isEnabled = true
            refreshButtonBackgrounds()
        }
    }

    override fun onCharacteristicRead(characteristicUuid: UUID, value: ByteArray) {
        // Parsing is intentionally deferred to the next phase.
    }

    override fun onCharacteristicWrite(characteristicUuid: UUID, success: Boolean) {
        // Command write handling will be added with dashboard actions.
    }

    override fun onError(message: String, cause: Throwable?) {
        runOnUiThread {
            binding.statusText.text = message
            binding.scanButton.isEnabled = true
            binding.connectButton.isEnabled = selectedDevice != null
            refreshButtonBackgrounds()
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshButtonBackgrounds() {
        binding.scanButton.setBackgroundResource(
            if (binding.scanButton.isEnabled) R.drawable.bg_button_green else R.drawable.bg_button_disabled
        )
        binding.connectButton.setBackgroundResource(
            if (binding.connectButton.isEnabled) R.drawable.bg_button_secondary else R.drawable.bg_button_disabled
        )
        binding.openDashboardButton.setBackgroundResource(
            if (binding.openDashboardButton.isEnabled) R.drawable.bg_button_secondary else R.drawable.bg_button_disabled
        )

        val enabledTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val disabledTextColor = ContextCompat.getColor(requireContext(), R.color.disabled_text)
        binding.scanButton.setTextColor(if (binding.scanButton.isEnabled) enabledTextColor else disabledTextColor)
        binding.connectButton.setTextColor(if (binding.connectButton.isEnabled) enabledTextColor else disabledTextColor)
        binding.openDashboardButton.setTextColor(
            if (binding.openDashboardButton.isEnabled) enabledTextColor else disabledTextColor
        )
    }

    private fun runOnUiThread(action: () -> Unit) {
        activity?.runOnUiThread {
            if (_binding != null) {
                action()
            }
        }
    }

    override fun onDestroyView() {
        bleManager?.release()
        bleManager = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ConnectFragment = ConnectFragment()
    }
}
