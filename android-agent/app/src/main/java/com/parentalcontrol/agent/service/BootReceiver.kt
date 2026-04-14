package com.parentalcontrol.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parentalcontrol.agent.data.model.SyncTrigger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> TrackingForegroundService.start(context, SyncTrigger.BOOT)
        }
    }
}

