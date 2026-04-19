package com.albers.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.albers.app.ble.AlbersBleSession

class ConnectViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = AlbersBleSession.state

    init {
        AlbersBleSession.initialize(application)
    }

    fun startConnectionFlow() {
        AlbersBleSession.startConnectionFlow(getApplication())
    }

    fun connectSelectedDevice() {
        AlbersBleSession.connectSelectedDevice()
    }

    fun consumeDashboardNavigation() {
        AlbersBleSession.consumeDashboardNavigation()
    }
}
