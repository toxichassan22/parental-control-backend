package com.parentalcontrol.agent.sync

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.parentalcontrol.agent.BuildConfig
import com.parentalcontrol.agent.data.local.AgentDatabase
import com.parentalcontrol.agent.data.local.entity.DeviceEventEntity
import com.parentalcontrol.agent.data.local.entity.UsageDailyEntity
import com.parentalcontrol.agent.data.model.AdminCommand
import com.parentalcontrol.agent.data.model.DeviceHealthSnapshot
import com.parentalcontrol.agent.data.model.DevicePolicy
import com.parentalcontrol.agent.data.model.EventSeverity
import com.parentalcontrol.agent.data.model.PairingPayload
import com.parentalcontrol.agent.data.model.SyncTrigger
import com.parentalcontrol.agent.data.model.TrustedTimeWindow
import com.parentalcontrol.agent.data.store.LocalStateStore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncRepository(
    private val stateStore: LocalStateStore,
    private val database: AgentDatabase,
    private val pairingApiClient: PairingApiClient,
) {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val functions = Firebase.functions

    suspend fun pairDevice(payload: PairingPayload): String {
        val pairingResult = pairingApiClient.exchangePairingCode(BuildConfig.PAIRING_ENDPOINT, payload)
        auth.signInWithCustomToken(pairingResult.customToken).await()
        stateStore.setPairing(pairingResult.deviceId)
        return pairingResult.deviceId
    }

    suspend fun fetchRemotePolicy(deviceId: String): DevicePolicy? {
        val snapshot = firestore.collection("devices").document(deviceId).collection("policy").document("current").get().await()
        if (!snapshot.exists()) {
            return null
        }

        return snapshot.toDevicePolicy()
    }

    suspend fun fetchPendingCommands(deviceId: String, afterVersion: Long): List<AdminCommand> {
        val snapshot = firestore
            .collection("devices")
            .document(deviceId)
            .collection("commands")
            .whereGreaterThan("commandVersion", afterVersion)
            .orderBy("commandVersion", Query.Direction.ASCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toAdminCommand()
        }
    }

    suspend fun sendHeartbeat(
        deviceId: String,
        health: DeviceHealthSnapshot,
        locked: Boolean,
        trigger: SyncTrigger,
    ): TrustedTimeWindow? {
        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        if (fcmToken != null) {
            stateStore.setLastFcmToken(fcmToken)
        }

        val result = functions
            .getHttpsCallable("markDeviceSeen")
            .call(
                mapOf(
                    "deviceId" to deviceId,
                    "status" to health.status.name,
                    "healthScore" to health.healthScore,
                    "vpnGranted" to health.vpnGranted,
                    "overlayGranted" to health.overlayGranted,
                    "usageGranted" to health.usageGranted,
                    "accessibilityEnabled" to health.accessibilityEnabled,
                    "batteryOptimizationIgnored" to health.batteryOptimizationIgnored,
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "locked" to locked,
                    "fcmToken" to fcmToken,
                    "trigger" to trigger.name,
                ),
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return null
        return TrustedTimeWindow(
            dayKey = data["dayKey"] as? String ?: return null,
            windowStartMs = (data["windowStartMs"] as? Number)?.toLong() ?: return null,
            windowEndMs = (data["windowEndMs"] as? Number)?.toLong() ?: return null,
            serverTimeMs = (data["serverTimeMs"] as? Number)?.toLong() ?: return null,
        )
    }

    suspend fun uploadUsage(deviceId: String, usage: UsageDailyEntity) {
        firestore
            .collection("devices")
            .document(deviceId)
            .collection("usageDaily")
            .document(usage.dayKey)
            .set(
                mapOf(
                    "deviceId" to deviceId,
                    "dayKey" to usage.dayKey,
                    "estimatedBytes" to usage.estimatedBytes,
                    "source" to usage.source,
                    "lockedCount" to usage.lockedCount,
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                ),
            )
            .await()
    }

    suspend fun logEvent(
        deviceId: String,
        type: String,
        severity: EventSeverity,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val eventId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        database.deviceEventDao().upsert(
            DeviceEventEntity(
                eventId = eventId,
                type = type,
                severity = severity.name,
                createdAt = createdAt,
                payload = metadata.toString(),
            ),
        )

        runCatching {
            firestore.collection("devices").document(deviceId).collection("events").document(eventId).set(
                mapOf(
                    "type" to type,
                    "severity" to severity.name.lowercase(),
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "metadata" to metadata,
                ),
            ).await()
        }.onFailure {
            Log.w("CloudSyncRepository", "Failed to upload event", it)
        }
    }

    suspend fun ackCommand(
        deviceId: String,
        command: AdminCommand,
        status: String,
        errorCode: String? = null,
    ) {
        firestore.collection("devices").document(deviceId).collection("commandAcks").document(command.commandId).set(
            mapOf(
                "commandId" to command.commandId,
                "commandVersion" to command.commandVersion,
                "status" to status,
                "errorCode" to errorCode,
                "receivedAt" to com.google.firebase.Timestamp.now(),
                "executedAt" to com.google.firebase.Timestamp.now(),
            ),
        ).await()
    }

    private fun DocumentSnapshot.toDevicePolicy(): DevicePolicy {
        return DevicePolicy(
            policyVersion = getLong("policyVersion") ?: 0,
            dailyLimitBytes = getLong("dailyLimitBytes") ?: 0,
            allowlistPackages = get("allowlistPackages") as? List<String> ?: emptyList(),
            enforcementEnabled = getBoolean("enforcementEnabled") ?: true,
            adminTimezone = getString("adminTimezone") ?: "UTC",
            resetMode = getString("resetMode") ?: "SERVER_WINDOW",
        )
    }

    private fun DocumentSnapshot.toAdminCommand(): AdminCommand? {
        val commandId = getString("commandId") ?: id
        val type = getString("type") ?: return null
        return AdminCommand(
            commandId = commandId,
            type = type,
            commandVersion = getLong("commandVersion") ?: 0,
            issuedAtServerMs = getTimestamp("issuedAtServer")?.toDate()?.time ?: 0,
            expiresAtMs = getTimestamp("expiresAt")?.toDate()?.time ?: 0,
            payload = get("payload") as? Map<String, Any?> ?: emptyMap(),
        )
    }
}
