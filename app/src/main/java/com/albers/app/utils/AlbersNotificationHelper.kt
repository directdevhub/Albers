package com.albers.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.albers.app.R
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.FaultSummary

class AlbersNotificationHelper(private val context: Context) {

    fun notifyIfCritical(summary: FaultSummary) {
        if (!summary.shouldAlert || summary.highestSeverity != FaultSeverity.Critical) return
        if (!hasNotificationPermission()) return

        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hazard_warning)
            .setContentTitle("ALBERS critical alert")
            .setContentText(summary.primaryMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(CRITICAL_ALERT_ID, notification)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ALBERS alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical ALBERS battery, pump, and connection alerts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        private const val CHANNEL_ID = "albers_alerts"
        private const val CRITICAL_ALERT_ID = 1001
    }
}
