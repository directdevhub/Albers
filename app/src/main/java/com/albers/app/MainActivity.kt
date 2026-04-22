package com.albers.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.albers.app.ble.AlbersBleSession
import com.albers.app.data.repository.NotificationStore
import com.albers.app.databinding.ActivityMainBinding
import com.albers.app.ui.connect.ConnectFragment
import com.albers.app.ui.dashboard.DashboardFragment
import com.albers.app.ui.help.HelpFragment
import com.albers.app.ui.notifications.NotificationsFragment
import com.albers.app.ui.notifications.NotificationDetailFragment
import com.albers.app.ui.privacy.PrivacyFragment
import com.albers.app.ui.rinse.RinseFragment
import com.albers.app.ui.settings.SettingsFragment
import com.albers.app.ui.splash.SplashFragment
import com.albers.app.ui.systemstatus.SystemStatusFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Alerts still appear in-app when notification permission is denied.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NotificationStore.initialize(applicationContext)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            showSplash()
        }
        requestNotificationPermissionIfNeeded()
    }

    fun showSplash() {
        replaceFragment(SplashFragment.newInstance(), addToBackStack = false)
    }

    fun showConnect() {
        replaceFragment(ConnectFragment.newInstance(), addToBackStack = false)
    }

    fun showDashboard(addToBackStack: Boolean = true) {
        replaceFragment(DashboardFragment.newInstance(), addToBackStack = addToBackStack)
    }

    fun showSystemStatus() {
        replaceFragment(SystemStatusFragment.newInstance(), addToBackStack = true)
    }

    fun showSettings() {
        replaceFragment(SettingsFragment.newInstance(), addToBackStack = true)
    }

    fun showRinse() {
        replaceFragment(RinseFragment.newInstance(), addToBackStack = true)
    }

    fun showNotifications() {
        replaceFragment(NotificationsFragment.newInstance(), addToBackStack = true)
    }

    fun showNotificationDetail(notificationId: Long) {
        replaceFragment(NotificationDetailFragment.newInstance(notificationId), addToBackStack = true)
    }

    fun showHelp() {
        replaceFragment(HelpFragment.newInstance(), addToBackStack = true)
    }

    fun showPrivacy() {
        replaceFragment(PrivacyFragment.newInstance(), addToBackStack = true)
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .apply {
                if (addToBackStack) {
                    addToBackStack(fragment::class.java.simpleName)
                }
            }
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            AlbersBleSession.release()
        }
        super.onDestroy()
    }
}
