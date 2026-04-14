package com.parentalcontrol.agent.monitor

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import androidx.core.content.getSystemService
import com.parentalcontrol.agent.data.model.UsageMeasurement
import com.parentalcontrol.agent.data.model.UsageSource
import com.parentalcontrol.agent.util.PermissionStateChecker

class UsageEstimator(
    private val context: Context,
    private val permissionStateChecker: PermissionStateChecker,
) {
    fun estimateUsage(windowStartMs: Long, windowEndMs: Long): UsageMeasurement {
        if (permissionStateChecker.hasUsageStatsPermission()) {
            readFromNetworkStats(windowStartMs, windowEndMs)?.let { return it }
        }

        return UsageMeasurement(
            totalBytes = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).coerceAtLeast(0),
            source = UsageSource.TRAFFIC_STATS,
        )
    }

    private fun readFromNetworkStats(startMs: Long, endMs: Long): UsageMeasurement? {
        val networkStatsManager = context.getSystemService<NetworkStatsManager>() ?: return null

        return try {
            val wifiBucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                null,
                startMs,
                endMs,
            )
            val mobileBucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startMs,
                endMs,
            )

            UsageMeasurement(
                totalBytes = wifiBucket.rxBytes + wifiBucket.txBytes + mobileBucket.rxBytes + mobileBucket.txBytes,
                source = UsageSource.NETWORK_STATS,
            )
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}

