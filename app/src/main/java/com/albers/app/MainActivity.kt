package com.albers.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.albers.app.databinding.ActivityMainBinding
import com.albers.app.ui.connect.ConnectFragment
import com.albers.app.ui.dashboard.DashboardFragment
import com.albers.app.ui.splash.SplashFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            showSplash()
        }
    }

    fun showSplash() {
        replaceFragment(SplashFragment.newInstance(), addToBackStack = false)
    }

    fun showConnect() {
        replaceFragment(ConnectFragment.newInstance(), addToBackStack = false)
    }

    fun showDashboard() {
        replaceFragment(DashboardFragment.newInstance(), addToBackStack = true)
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
}
