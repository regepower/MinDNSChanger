package de.regepower.mindnschanger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/** Starts the DNS VPN after boot when enabled; BOOT_COMPLETED allows the foreground-service start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).autostart || DnsVpnService.running) return
        if (VpnService.prepare(context) != null) {
            Log.w("MinDNS", "autostart skipped: VPN permission missing")
            return
        }
        DnsVpnService.start(context)
    }
}
