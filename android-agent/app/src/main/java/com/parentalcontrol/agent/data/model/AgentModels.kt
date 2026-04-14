package com.parentalcontrol.agent.data.model

import kotlinx.serialization.Serializable

enum class DeviceStatus {
    HEALTHY,
    WARNING,
    DEGRADED,
    LOCKED,
    STALE,
}

enum class UsageSource {
    NETWORK_STATS,
    TRAFFIC_STATS,
}

enum class SyncTrigger {
    APP_OPEN,
    APP_RESUME,
    BOOT,
    TIMER,
    FCM,
    CONNECTIVITY,
    MANUAL,
}

enum class EventSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class DevicePolicy(
    val policyVersion: Long = 0,
    val dailyLimitBytes: Long = 0,
    val allowlistPackages: List<String> = emptyList(),
    val enforcementEnabled: Boolean = true,
    val adminTimezone: String = "UTC",
    val resetMode: String = "SERVER_WINDOW",
)

data class TrustedTimeWindow(
    val dayKey: String = "",
    val windowStartMs: Long = 0,
    val windowEndMs: Long = 0,
    val serverTimeMs: Long = 0,
)

data class UsageMeasurement(
    val totalBytes: Long,
    val source: UsageSource,
)

data class AdminCommand(
    val commandId: String,
    val type: String,
    val commandVersion: Long,
    val issuedAtServerMs: Long,
    val expiresAtMs: Long,
    val payload: Map<String, Any?> = emptyMap(),
)

data class DeviceHealthSnapshot(
    val status: DeviceStatus,
    val healthScore: Int,
    val vpnGranted: Boolean,
    val overlayGranted: Boolean,
    val usageGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
)

@Serializable
data class PairingPayload(
    val tokenId: String,
    val pairingSecret: String? = null,
    val backupCode: String? = null,
    val deviceName: String,
)

@Serializable
data class PairingResult(
    val deviceId: String,
    val customToken: String,
)

data class AgentLocalSnapshot(
    val deviceId: String? = null,
    val paired: Boolean = false,
    val locked: Boolean = false,
    val lockReason: String? = null,
    val policy: DevicePolicy = DevicePolicy(),
    val trustedTimeWindow: TrustedTimeWindow = TrustedTimeWindow(),
    val latestCommandVersion: Long = 0,
    val latestUsageBytes: Long = 0,
    val latestUsageDayKey: String = "",
    val latestUsageSource: UsageSource = UsageSource.TRAFFIC_STATS,
    val bypassCount: Int = 0,
)
