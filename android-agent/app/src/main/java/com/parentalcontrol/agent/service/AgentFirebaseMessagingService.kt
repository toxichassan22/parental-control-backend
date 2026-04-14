package com.parentalcontrol.agent.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parentalcontrol.agent.ParentalControlApplication
import com.parentalcontrol.agent.data.model.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AgentFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        TrackingForegroundService.requestImmediateSync(applicationContext, SyncTrigger.FCM)
    }

    override fun onNewToken(token: String) {
        val container = (application as ParentalControlApplication).container
        scope.launch {
            container.stateStore.setLastFcmToken(token)
        }
        TrackingForegroundService.requestImmediateSync(applicationContext, SyncTrigger.FCM)
    }
}

