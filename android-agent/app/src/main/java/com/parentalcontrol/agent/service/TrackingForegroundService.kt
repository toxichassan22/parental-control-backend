package com.parentalcontrol.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.parentalcontrol.agent.AgentContainer
import com.parentalcontrol.agent.ParentalControlApplication
import com.parentalcontrol.agent.R
import com.parentalcontrol.agent.controller.ProtectionManager
import com.parentalcontrol.agent.data.model.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrackingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AgentContainer
    private lateinit var protectionManager: ProtectionManager
    private var loopStarted = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as ParentalControlApplication).container
        protectionManager = ProtectionManager(
            context = applicationContext,
            stateStore = container.stateStore,
            database = container.database,
            syncRepository = container.syncRepository,
            usageEstimator = container.usageEstimator,
            overlayManager = container.overlayManager,
            permissionStateChecker = container.permissionStateChecker,
        )

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECORD_BYPASS -> {
                val reason = intent.getStringExtra(EXTRA_REASON) ?: "BYPASS_ATTEMPT"
                serviceScope.launch { runSafely { protectionManager.recordBypass(reason) } }
            }

            ACTION_SYNC_NOW -> {
                serviceScope.launch {
                    runSafely {
                        protectionManager.performTick(
                            trigger = intent.getStringExtra(EXTRA_TRIGGER)?.let(SyncTrigger::valueOf) ?: SyncTrigger.MANUAL,
                            forceSync = true,
                        )
                    }
                }
            }
        }

        if (!loopStarted) {
            startLoop()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.unregisterNetworkCallback(callback)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLoop() {
        loopStarted = true
        serviceScope.launch {
            runSafely { protectionManager.refreshOnAppOpen() }
            while (true) {
                runSafely { protectionManager.performTick(SyncTrigger.TIMER) }
                delay(10_000)
            }
        }
    }

    private fun registerNetworkCallback() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch { runSafely { protectionManager.performTick(SyncTrigger.CONNECTIVITY, forceSync = true) } }
            }
        }

        networkCallback = callback
        manager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 10
        private const val ACTION_START = "com.parentalcontrol.agent.START"
        private const val ACTION_SYNC_NOW = "com.parentalcontrol.agent.SYNC_NOW"
        private const val ACTION_RECORD_BYPASS = "com.parentalcontrol.agent.RECORD_BYPASS"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_TRIGGER = "trigger"

        fun start(context: Context, trigger: SyncTrigger = SyncTrigger.APP_OPEN) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIGGER, trigger.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestImmediateSync(context: Context, trigger: SyncTrigger) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_SYNC_NOW
                putExtra(EXTRA_TRIGGER, trigger.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun recordBypass(context: Context, reason: String) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_RECORD_BYPASS
                putExtra(EXTRA_REASON, reason)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private suspend fun runSafely(block: suspend () -> Unit) {
        runCatching { block() }
            .onFailure { Log.e("TrackingService", "Protection loop failure", it) }
    }
}
