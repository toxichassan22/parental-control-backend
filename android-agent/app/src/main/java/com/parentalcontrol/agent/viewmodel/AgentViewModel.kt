package com.parentalcontrol.agent.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parentalcontrol.agent.AgentContainer
import com.parentalcontrol.agent.data.local.entity.DeviceEventEntity
import com.parentalcontrol.agent.data.local.entity.UsageDailyEntity
import com.parentalcontrol.agent.data.model.AgentLocalSnapshot
import com.parentalcontrol.agent.data.model.PairingPayload
import com.parentalcontrol.agent.data.model.SyncTrigger
import com.parentalcontrol.agent.service.TrackingForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AgentUiState(
    val local: AgentLocalSnapshot = AgentLocalSnapshot(),
    val recentUsage: List<UsageDailyEntity> = emptyList(),
    val recentEvents: List<DeviceEventEntity> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

class AgentViewModel(
    private val application: Application,
    private val container: AgentContainer,
) : ViewModel() {
    private val transientState = MutableStateFlow(AgentUiState())

    val uiState: StateFlow<AgentUiState> = combine(
        container.stateStore.snapshot,
        container.database.usageDailyDao().observeRecent(),
        container.database.deviceEventDao().observeRecent(),
        transientState,
    ) { local, usage, events, transient ->
        transient.copy(
            local = local,
            recentUsage = usage,
            recentEvents = events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())

    fun pairDevice(
        deviceName: String,
        qrPayload: String,
        tokenId: String,
        pairingSecret: String,
        backupCode: String,
    ) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(loading = true, message = null)
            runCatching {
                val payload = parsePairingPayload(deviceName, qrPayload, tokenId, pairingSecret, backupCode)
                container.syncRepository.pairDevice(payload)
            }.onSuccess {
                TrackingForegroundService.start(application.applicationContext, SyncTrigger.APP_OPEN)
                transientState.value = transientState.value.copy(
                    loading = false,
                    message = "Device paired successfully. Start the protection service.",
                )
            }.onFailure { error ->
                transientState.value = transientState.value.copy(
                    loading = false,
                    message = error.message ?: "Pairing failed",
                )
            }
        }
    }

    fun refreshNow() {
        TrackingForegroundService.requestImmediateSync(application.applicationContext, SyncTrigger.MANUAL)
    }

    fun startProtection() {
        TrackingForegroundService.start(application.applicationContext, SyncTrigger.APP_OPEN)
    }

    private fun parsePairingPayload(
        deviceName: String,
        qrPayload: String,
        tokenId: String,
        pairingSecret: String,
        backupCode: String,
    ): PairingPayload {
        if (qrPayload.isNotBlank()) {
            val json = JSONObject(qrPayload)
            return PairingPayload(
                tokenId = json.getString("tokenId"),
                pairingSecret = json.optString("pairingSecret").takeIf { it.isNotBlank() },
                backupCode = json.optString("backupCode").takeIf { it.isNotBlank() },
                deviceName = deviceName,
            )
        }

        return PairingPayload(
            tokenId = tokenId,
            pairingSecret = pairingSecret.takeIf { it.isNotBlank() },
            backupCode = backupCode.takeIf { it.isNotBlank() },
            deviceName = deviceName,
        )
    }

    companion object {
        fun factory(application: Application, container: AgentContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AgentViewModel(application, container) as T
                }
            }
        }
    }
}

