package com.parentalcontrol.agent

import android.content.Context
import com.parentalcontrol.agent.data.local.AgentDatabase
import com.parentalcontrol.agent.data.store.LocalStateStore
import com.parentalcontrol.agent.monitor.UsageEstimator
import com.parentalcontrol.agent.overlay.LockOverlayManager
import com.parentalcontrol.agent.sync.CloudSyncRepository
import com.parentalcontrol.agent.sync.PairingApiClient
import com.parentalcontrol.agent.util.PermissionStateChecker

class AgentContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AgentDatabase by lazy { AgentDatabase.create(appContext) }
    val stateStore: LocalStateStore by lazy { LocalStateStore(appContext) }
    val permissionStateChecker: PermissionStateChecker by lazy { PermissionStateChecker(appContext) }
    val usageEstimator: UsageEstimator by lazy { UsageEstimator(appContext, permissionStateChecker) }
    val pairingApiClient: PairingApiClient by lazy { PairingApiClient() }
    val syncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(
            stateStore = stateStore,
            database = database,
            pairingApiClient = pairingApiClient,
        )
    }
    val overlayManager: LockOverlayManager by lazy { LockOverlayManager(appContext) }
}
