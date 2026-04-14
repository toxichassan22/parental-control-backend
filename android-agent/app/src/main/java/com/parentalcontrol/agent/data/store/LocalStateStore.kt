package com.parentalcontrol.agent.data.store

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.parentalcontrol.agent.data.model.AgentLocalSnapshot
import com.parentalcontrol.agent.data.model.DevicePolicy
import com.parentalcontrol.agent.data.model.TrustedTimeWindow
import com.parentalcontrol.agent.data.model.UsageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LocalStateStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("agent_state.preferences_pb") },
    )

    val snapshot: Flow<AgentLocalSnapshot> = dataStore.data.map { preferences ->
        AgentLocalSnapshot(
            deviceId = preferences[DEVICE_ID],
            paired = preferences[PAIRED] ?: false,
            locked = preferences[LOCKED] ?: false,
            lockReason = preferences[LOCK_REASON],
            policy = DevicePolicy(
                policyVersion = preferences[POLICY_VERSION] ?: 0,
                dailyLimitBytes = preferences[DAILY_LIMIT_BYTES] ?: 0,
                allowlistPackages = preferences[ALLOWLIST_PACKAGES]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                enforcementEnabled = preferences[ENFORCEMENT_ENABLED] ?: true,
                adminTimezone = preferences[ADMIN_TIMEZONE] ?: "UTC",
            ),
            trustedTimeWindow = TrustedTimeWindow(
                dayKey = preferences[DAY_KEY] ?: "",
                windowStartMs = preferences[WINDOW_START_MS] ?: 0,
                windowEndMs = preferences[WINDOW_END_MS] ?: 0,
                serverTimeMs = preferences[SERVER_TIME_MS] ?: 0,
            ),
            latestCommandVersion = preferences[LATEST_COMMAND_VERSION] ?: 0,
            latestUsageBytes = preferences[LATEST_USAGE_BYTES] ?: 0,
            latestUsageDayKey = preferences[LATEST_USAGE_DAY_KEY] ?: "",
            latestUsageSource = UsageSource.valueOf(preferences[LATEST_USAGE_SOURCE] ?: UsageSource.TRAFFIC_STATS.name),
            bypassCount = preferences[BYPASS_COUNT] ?: 0,
        )
    }

    suspend fun currentSnapshot(): AgentLocalSnapshot = snapshot.first()

    suspend fun setPairing(deviceId: String) {
        dataStore.edit {
            it[DEVICE_ID] = deviceId
            it[PAIRED] = true
        }
    }

    suspend fun clearPairing() {
        dataStore.edit {
            it.clear()
        }
    }

    suspend fun setLocked(locked: Boolean, reason: String? = null) {
        dataStore.edit {
            it[LOCKED] = locked
            if (reason != null) {
                it[LOCK_REASON] = reason
            } else if (!locked) {
                it.remove(LOCK_REASON)
            }
            if (locked) {
                it[LOCKED_COUNT_FOR_DAY] = (it[LOCKED_COUNT_FOR_DAY] ?: 0) + 1
            }
        }
    }

    suspend fun setPolicy(policy: DevicePolicy) {
        dataStore.edit {
            it[POLICY_VERSION] = policy.policyVersion
            it[DAILY_LIMIT_BYTES] = policy.dailyLimitBytes
            it[ALLOWLIST_PACKAGES] = policy.allowlistPackages.joinToString("|")
            it[ENFORCEMENT_ENABLED] = policy.enforcementEnabled
            it[ADMIN_TIMEZONE] = policy.adminTimezone
        }
    }

    suspend fun setTrustedTimeWindow(window: TrustedTimeWindow) {
        dataStore.edit {
            it[DAY_KEY] = window.dayKey
            it[WINDOW_START_MS] = window.windowStartMs
            it[WINDOW_END_MS] = window.windowEndMs
            it[SERVER_TIME_MS] = window.serverTimeMs
            it[ELAPSED_REALTIME_AT_SYNC] = SystemClock.elapsedRealtime()
        }
    }

    suspend fun setLatestCommandVersion(version: Long) {
        dataStore.edit { it[LATEST_COMMAND_VERSION] = version }
    }

    suspend fun setLatestUsage(bytes: Long, dayKey: String, source: UsageSource) {
        dataStore.edit {
            it[LATEST_USAGE_BYTES] = bytes
            it[LATEST_USAGE_DAY_KEY] = dayKey
            it[LATEST_USAGE_SOURCE] = source.name
        }
    }

    suspend fun setLastHeartbeatAt(epochMs: Long) {
        dataStore.edit { it[LAST_HEARTBEAT_AT] = epochMs }
    }

    suspend fun setLastPollAt(epochMs: Long) {
        dataStore.edit { it[LAST_POLL_AT] = epochMs }
    }

    suspend fun setLastFcmToken(token: String) {
        dataStore.edit { it[FCM_TOKEN] = token }
    }

    suspend fun incrementBypassCount(): Int {
        var nextCount = 0
        dataStore.edit {
            nextCount = (it[BYPASS_COUNT] ?: 0) + 1
            it[BYPASS_COUNT] = nextCount
        }
        return nextCount
    }

    suspend fun resetBypassCount() {
        dataStore.edit { it[BYPASS_COUNT] = 0 }
    }

    suspend fun resetDailyCounters(nextDayKey: String) {
        dataStore.edit {
            it[LATEST_USAGE_BYTES] = 0
            it[LATEST_USAGE_DAY_KEY] = nextDayKey
            it[LOCKED_COUNT_FOR_DAY] = 0
        }
    }

    suspend fun getLockedCountForDay(): Int {
        return dataStore.data.map { it[LOCKED_COUNT_FOR_DAY] ?: 0 }.first()
    }

    suspend fun getLastHeartbeatAt(): Long = dataStore.data.map { it[LAST_HEARTBEAT_AT] ?: 0 }.first()

    suspend fun getLastPollAt(): Long = dataStore.data.map { it[LAST_POLL_AT] ?: 0 }.first()

    suspend fun getElapsedRealtimeAtSync(): Long = dataStore.data.map { it[ELAPSED_REALTIME_AT_SYNC] ?: 0 }.first()

    suspend fun getFcmToken(): String? = dataStore.data.map { it[FCM_TOKEN] }.first()

    companion object {
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val PAIRED = booleanPreferencesKey("paired")
        private val LOCKED = booleanPreferencesKey("locked")
        private val LOCK_REASON = stringPreferencesKey("lock_reason")
        private val POLICY_VERSION = longPreferencesKey("policy_version")
        private val DAILY_LIMIT_BYTES = longPreferencesKey("daily_limit_bytes")
        private val ALLOWLIST_PACKAGES = stringPreferencesKey("allowlist_packages")
        private val ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        private val ADMIN_TIMEZONE = stringPreferencesKey("admin_timezone")
        private val DAY_KEY = stringPreferencesKey("trusted_day_key")
        private val WINDOW_START_MS = longPreferencesKey("window_start_ms")
        private val WINDOW_END_MS = longPreferencesKey("window_end_ms")
        private val SERVER_TIME_MS = longPreferencesKey("server_time_ms")
        private val ELAPSED_REALTIME_AT_SYNC = longPreferencesKey("elapsed_realtime_at_sync")
        private val LATEST_COMMAND_VERSION = longPreferencesKey("latest_command_version")
        private val LATEST_USAGE_BYTES = longPreferencesKey("latest_usage_bytes")
        private val LATEST_USAGE_DAY_KEY = stringPreferencesKey("latest_usage_day_key")
        private val LATEST_USAGE_SOURCE = stringPreferencesKey("latest_usage_source")
        private val BYPASS_COUNT = intPreferencesKey("bypass_count")
        private val LOCKED_COUNT_FOR_DAY = intPreferencesKey("locked_count_for_day")
        private val LAST_HEARTBEAT_AT = longPreferencesKey("last_heartbeat_at")
        private val LAST_POLL_AT = longPreferencesKey("last_poll_at")
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
    }
}
