package com.parentalcontrol.agent.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream

class BlockingVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE -> {
                tearDownVpn()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ENABLE -> {
                val allowlist = intent.getStringArrayListExtra(EXTRA_ALLOWLIST).orEmpty()
                establishBlockingVpn(allowlist)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        tearDownVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        tearDownVpn()
        super.onRevoke()
    }

    private fun establishBlockingVpn(allowlistPackages: List<String>) {
        tearDownVpn()
        val builder = Builder()
            .setSession("ParentalControlHardLock")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(1500)

        allowlistPackages.forEach { packageName ->
            runCatching {
                builder.addDisallowedApplication(packageName)
            }
        }

        vpnInterface = builder.establish()
        vpnInterface?.let { descriptor ->
            serviceScope.launch {
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    val buffer = ByteArray(4096)
                    while (isActive) {
                        if (input.read(buffer) <= 0) break
                    }
                }
            }
        }
    }

    private fun tearDownVpn() {
        vpnInterface?.close()
        vpnInterface = null
    }

    companion object {
        private const val ACTION_ENABLE = "com.parentalcontrol.agent.ENABLE_LOCK"
        private const val ACTION_DISABLE = "com.parentalcontrol.agent.DISABLE_LOCK"
        private const val EXTRA_ALLOWLIST = "allowlist"

        fun enableLock(context: Context, allowlistPackages: List<String>) {
            val intent = Intent(context, BlockingVpnService::class.java).apply {
                action = ACTION_ENABLE
                putStringArrayListExtra(EXTRA_ALLOWLIST, ArrayList(allowlistPackages))
            }
            context.startService(intent)
        }

        fun disableLock(context: Context) {
            val intent = Intent(context, BlockingVpnService::class.java).apply {
                action = ACTION_DISABLE
            }
            context.startService(intent)
        }
    }
}
