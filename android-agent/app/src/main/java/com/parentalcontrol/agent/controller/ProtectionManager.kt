package com.parentalcontrol.agent.controller

import android.content.Context
import android.os.SystemClock
import com.parentalcontrol.agent.data.local.AgentDatabase
import com.parentalcontrol.agent.data.local.entity.UsageDailyEntity
import com.parentalcontrol.agent.data.model.AdminCommand
import com.parentalcontrol.agent.data.model.DeviceHealthSnapshot
import com.parentalcontrol.agent.data.model.DevicePolicy
import com.parentalcontrol.agent.data.model.EventSeverity
import com.parentalcontrol.agent.data.model.SyncTrigger
import com.parentalcontrol.agent.data.model.UsageMeasurement
import com.parentalcontrol.agent.data.store.LocalStateStore
import com.parentalcontrol.agent.monitor.UsageEstimator
import com.parentalcontrol.agent.overlay.LockOverlayManager
import com.parentalcontrol.agent.service.BlockingVpnService
import com.parentalcontrol.agent.sync.CloudSyncRepository
import com.parentalcontrol.agent.util.PermissionStateChecker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class ProtectionManager(
    private val context: Context,
    private val stateStore: LocalStateStore,
    private val database: AgentDatabase,
    private val syncRepository: CloudSyncRepository,
    private val usageEstimator: UsageEstimator,
    private val overlayManager: LockOverlayManager,
    private val permissionStateChecker: PermissionStateChecker,
) {
    private val mutex = Mutex()

    suspend fun performTick(trigger: SyncTrigger, forceSync: Boolean = false) {
        mutex.withLock {
            val local = stateStore.currentSnapshot()
            val deviceId = local.deviceId ?: return
            if (!local.paired) return

            val stale = System.currentTimeMillis() - stateStore.getLastHeartbeatAt() > STALE_MS
            val health = permissionStateChecker.buildHealthSnapshot(
                locked = local.locked,
                stale = stale && trigger != SyncTrigger.APP_OPEN,
            )

            val shouldHeartbeat = forceSync || shouldHeartbeat()
            val shouldPoll = forceSync || shouldPoll(health)

            if (shouldHeartbeat) {
                syncRepository.sendHeartbeat(deviceId, health, local.locked, trigger)?.let { window ->
                    val previousDay = local.trustedTimeWindow.dayKey
                    stateStore.setTrustedTimeWindow(window)
                    stateStore.setLastHeartbeatAt(System.currentTimeMillis())
                    if (window.dayKey.isNotBlank() && previousDay != window.dayKey) {
                        rollWindowIfNeeded(previousDay, window.dayKey, local.lockReason)
                    }
                }
            }

            if (shouldPoll) {
                syncRepository.fetchRemotePolicy(deviceId)?.let { policy ->
                    stateStore.setPolicy(policy)
                }

                val latestCommandVersion = stateStore.currentSnapshot().latestCommandVersion
                syncRepository.fetchPendingCommands(deviceId, latestCommandVersion).forEach { command ->
                    applyCommand(deviceId, command)
                }
                stateStore.setLastPollAt(System.currentTimeMillis())
            }

            val freshState = stateStore.currentSnapshot()
            if (freshState.trustedTimeWindow.dayKey.isBlank()) {
                return
            }

            detectClockTamper(deviceId, freshState.trustedTimeWindow.serverTimeMs)

            val measurement = usageEstimator.estimateUsage(
                freshState.trustedTimeWindow.windowStartMs,
                freshState.trustedTimeWindow.windowEndMs,
            )
            persistUsage(freshState.trustedTimeWindow.dayKey, measurement, freshState.locked)
            stateStore.setLatestUsage(measurement.totalBytes, freshState.trustedTimeWindow.dayKey, measurement.source)

            if (freshState.policy.enforcementEnabled &&
                freshState.policy.dailyLimitBytes > 0 &&
                measurement.totalBytes >= freshState.policy.dailyLimitBytes
            ) {
                enforceHardLock(deviceId, freshState.policy, "LIMIT_EXCEEDED")
            } else if (!freshState.locked) {
                releaseLocalEnforcement()
            }

            val afterEnforcement = stateStore.currentSnapshot()
            if (afterEnforcement.locked) {
                applyLocalEnforcement(afterEnforcement.policy, afterEnforcement.lockReason ?: "LOCKED")
            }

            if (shouldPoll || forceSync) {
                val usageEntry = database.usageDailyDao().findByDayKey(afterEnforcement.trustedTimeWindow.dayKey)
                if (usageEntry != null) {
                    syncRepository.uploadUsage(deviceId, usageEntry)
                }
            }
        }
    }

    suspend fun recordBypass(reason: String) {
        mutex.withLock {
            val snapshot = stateStore.currentSnapshot()
            val deviceId = snapshot.deviceId ?: return
            val count = stateStore.incrementBypassCount()
            syncRepository.logEvent(
                deviceId = deviceId,
                type = reason,
                severity = if (count >= 3) EventSeverity.CRITICAL else EventSeverity.WARNING,
                metadata = mapOf("count" to count),
            )

            if (count >= 3) {
                enforceHardLock(deviceId, snapshot.policy, "BYPASS_ESCALATION")
            }
        }
    }

    suspend fun refreshOnAppOpen() {
        performTick(SyncTrigger.APP_OPEN, forceSync = true)
    }

    private suspend fun applyCommand(deviceId: String, command: AdminCommand) {
        val snapshot = stateStore.currentSnapshot()
        if (command.commandVersion <= snapshot.latestCommandVersion) {
            syncRepository.ackCommand(deviceId, command, "SKIPPED")
            return
        }

        when (command.type) {
            "LOCK" -> enforceHardLock(deviceId, snapshot.policy, "REMOTE_LOCK")
            "UNLOCK" -> clearLock("REMOTE_UNLOCK")
            "SYNC_POLICY", "PING" -> Unit
        }

        stateStore.setLatestCommandVersion(command.commandVersion)
        syncRepository.ackCommand(deviceId, command, "APPLIED")
    }

    private suspend fun enforceHardLock(deviceId: String, policy: DevicePolicy, reason: String) {
        val snapshot = stateStore.currentSnapshot()
        if (!snapshot.locked) {
            stateStore.setLocked(true, reason)
        } else if (snapshot.lockReason != reason) {
            stateStore.setLocked(true, reason)
        }

        applyLocalEnforcement(policy, reason)
        syncRepository.logEvent(
            deviceId = deviceId,
            type = reason,
            severity = EventSeverity.WARNING,
            metadata = mapOf("lockReason" to reason),
        )
    }

    private suspend fun clearLock(reason: String) {
        stateStore.setLocked(false)
        releaseLocalEnforcement()
        stateStore.resetBypassCount()
        val deviceId = stateStore.currentSnapshot().deviceId ?: return
        syncRepository.logEvent(
            deviceId = deviceId,
            type = reason,
            severity = EventSeverity.INFO,
        )
    }

    private fun applyLocalEnforcement(policy: DevicePolicy, reason: String) {
        overlayManager.show(reason)
        BlockingVpnService.enableLock(
            context = context,
            allowlistPackages = (policy.allowlistPackages + SAFE_NETWORK_ALLOWLIST + context.packageName).distinct(),
        )
    }

    private fun releaseLocalEnforcement() {
        overlayManager.hide()
        BlockingVpnService.disableLock(context)
    }

    private suspend fun rollWindowIfNeeded(previousDay: String, nextDay: String, lockReason: String?) {
        if (previousDay == nextDay) return
        stateStore.resetDailyCounters(nextDay)
        if (lockReason == "LIMIT_EXCEEDED") {
            stateStore.setLocked(false)
            releaseLocalEnforcement()
        }
    }

    private suspend fun persistUsage(dayKey: String, measurement: UsageMeasurement, locked: Boolean) {
        val lockedCount = stateStore.getLockedCountForDay()
        database.usageDailyDao().upsert(
            UsageDailyEntity(
                dayKey = dayKey,
                estimatedBytes = measurement.totalBytes,
                source = measurement.source.name,
                lockedCount = if (locked) lockedCount.coerceAtLeast(1) else lockedCount,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun detectClockTamper(deviceId: String, serverTimeMs: Long) {
        val elapsedAtSync = stateStore.getElapsedRealtimeAtSync()
        if (serverTimeMs == 0L || elapsedAtSync == 0L) return

        val expectedNow = serverTimeMs + (SystemClock.elapsedRealtime() - elapsedAtSync)
        val skew = abs(expectedNow - System.currentTimeMillis())
        if (skew > CLOCK_TAMPER_THRESHOLD_MS) {
            syncRepository.logEvent(
                deviceId = deviceId,
                type = "CLOCK_TAMPER_SUSPECTED",
                severity = EventSeverity.WARNING,
                metadata = mapOf("skewMs" to skew),
            )
        }
    }

    private suspend fun shouldHeartbeat(): Boolean {
        return System.currentTimeMillis() - stateStore.getLastHeartbeatAt() >= HEARTBEAT_MS
    }

    private suspend fun shouldPoll(health: DeviceHealthSnapshot): Boolean {
        val interval = if (health.status.name == "LOCKED" || health.status.name == "DEGRADED") {
            POLL_LOCKED_MS
        } else {
            POLL_NORMAL_MS
        }
        return System.currentTimeMillis() - stateStore.getLastPollAt() >= interval
    }

    companion object {
        private const val HEARTBEAT_MS = 60_000L
        private const val POLL_NORMAL_MS = 60_000L
        private const val POLL_LOCKED_MS = 30_000L
        private const val STALE_MS = 180_000L
        private const val CLOCK_TAMPER_THRESHOLD_MS = 5 * 60 * 1000L

        private val SAFE_NETWORK_ALLOWLIST = listOf(
            "com.google.android.gms",
            "com.google.android.gsf",
        )
    }
}
