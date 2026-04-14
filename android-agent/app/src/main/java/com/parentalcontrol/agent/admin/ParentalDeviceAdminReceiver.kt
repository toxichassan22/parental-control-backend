package com.parentalcontrol.agent.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.parentalcontrol.agent.service.TrackingForegroundService

class ParentalDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        TrackingForegroundService.recordBypass(context, "DEVICE_ADMIN_ENABLED")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        TrackingForegroundService.recordBypass(context, "DEVICE_ADMIN_DISABLED")
    }
}

