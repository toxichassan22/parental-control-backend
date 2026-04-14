package com.parentalcontrol.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.parentalcontrol.agent.service.TrackingForegroundService

class ProtectionAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val className = event?.className?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        when {
            className.contains("Settings", ignoreCase = true) -> {
                TrackingForegroundService.recordBypass(this, "SETTINGS_OPENED")
            }
            className.contains("Vpn", ignoreCase = true) -> {
                TrackingForegroundService.recordBypass(this, "VPN_SETTINGS_OPENED")
            }
            className.contains("UsageAccess", ignoreCase = true) -> {
                TrackingForegroundService.recordBypass(this, "USAGE_ACCESS_OPENED")
            }
        }
    }

    override fun onInterrupt() = Unit
}
