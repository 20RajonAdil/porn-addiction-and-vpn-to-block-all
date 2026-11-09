package com.maar.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Unlock.enabled(c) && VpnService.prepare(c) == null) {
            c.startService(Intent(c, BlockerVpnService::class.java))
        }
    }
}
