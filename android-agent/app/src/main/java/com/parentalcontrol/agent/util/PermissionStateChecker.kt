package com.parentalcontrol.agent.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.parentalcontrol.agent.accessibility.ProtectionAccessibilityService
import com.parentalcontrol.agent.data.model.DeviceHealthSnapshot
import com.parentalcontrol.agent.data.model.DeviceStatus

class PermissionStateChecker(private val context: Context) {
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasVpnPermission(): Boolean = VpnService.prepare(context) == null

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        return enabled.contains("${context.packageName}/${ProtectionAccessibilityService::class.java.name}")
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val powerManager = context.getSystemService<PowerManager>() ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun buildHealthSnapshot(locked: Boolean, stale: Boolean): DeviceHealthSnapshot {
        val vpnGranted = hasVpnPermission()
        val overlayGranted = hasOverlayPermission()
        val usageGranted = hasUsageStatsPermission()
        val accessibilityEnabled = isAccessibilityEnabled()
        val batteryOptimizationIgnored = isIgnoringBatteryOptimizations()

        var score = 100
        if (!vpnGranted) score -= 25
        if (!overlayGranted) score -= 20
        if (!usageGranted) score -= 20
        if (!accessibilityEnabled) score -= 20
        if (!batteryOptimizationIgnored) score -= 10
        if (stale) score -= 20

        val status = when {
            stale -> DeviceStatus.STALE
            locked -> DeviceStatus.LOCKED
            score >= 85 -> DeviceStatus.HEALTHY
            score >= 60 -> DeviceStatus.WARNING
            else -> DeviceStatus.DEGRADED
        }

        return DeviceHealthSnapshot(
            status = status,
            healthScore = score.coerceIn(0, 100),
            vpnGranted = vpnGranted,
            overlayGranted = overlayGranted,
            usageGranted = usageGranted,
            accessibilityEnabled = accessibilityEnabled,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
        )
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        android.net.Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        android.net.Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

