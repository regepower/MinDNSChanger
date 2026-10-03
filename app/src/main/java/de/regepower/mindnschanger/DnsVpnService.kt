package de.regepower.mindnschanger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.system.OsConstants
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * DNS-only VPN: the tunnel gets an address and the chosen DNS servers but no routes.
 * Android then resolves names for the covered apps through these servers, while all
 * traffic keeps using the normal network. No packet loop, nothing read from the tunnel.
 */
class DnsVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        // ACTION_START, always-on start by the system (android.net.VpnService) or sticky restart (null intent).
        val server = Prefs(this).current()
        if (prepare(this) != null) {
            Log.w(TAG, "VPN permission missing, not starting")
            shutdown()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(server), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: RuntimeException) {
            Log.e(TAG, "startForeground failed", e)
        }
        val old = tunnel
        tunnel = establish(server)
        closeQuietly(old)
        if (tunnel == null) {
            shutdown()
            return START_NOT_STICKY
        }
        setRunning(this, server)
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeQuietly(tunnel)
        tunnel = null
        setRunning(this, null)
        super.onDestroy()
    }

    private fun shutdown() {
        closeQuietly(tunnel)
        tunnel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        setRunning(this, null)
    }

    /** Tries a few private addresses in case one collides with the current network. */
    private fun establish(server: DnsServer): ParcelFileDescriptor? {
        val prefs = Prefs(this)
        for ((address, prefix) in ADDRESSES) {
            try {
                val b = Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress(address, prefix)
                    // No routes: keep IPv6 traffic flowing over the normal network instead of blocking it.
                    .allowFamily(OsConstants.AF_INET)
                    .allowFamily(OsConstants.AF_INET6)
                    .setMtu(1500)
                    .setConfigureIntent(mainIntent())
                b.addDnsServer(server.primary)
                server.secondary?.let { b.addDnsServer(it) }
                applyAppFilter(b, prefs)
                val fd = b.establish()
                if (fd != null) return fd
            } catch (e: Exception) {
                Log.w(TAG, "establish with $address failed", e)
            }
        }
        return null
    }

    private fun applyAppFilter(b: Builder, prefs: Prefs) {
        val pkgs = prefs.packages
        if (prefs.whitelist && pkgs.isNotEmpty()) {
            pkgs.forEach { pkg ->
                try {
                    b.addAllowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    Log.i(TAG, "skip uninstalled $pkg")
                }
            }
        } else {
            b.addDisallowedApplication(packageName)
            if (!prefs.whitelist) {
                pkgs.forEach { pkg ->
                    try {
                        b.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        Log.i(TAG, "skip uninstalled $pkg")
                    }
                }
            }
        }
    }

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun notification(server: DnsServer): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title, server.name))
            .setContentText(server.addresses)
            .setContentIntent(mainIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stop).build())
            .build()
    }

    private fun closeQuietly(fd: ParcelFileDescriptor?) {
        try {
            fd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed", e)
        }
    }

    companion object {
        private const val TAG = "MinDNS"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "de.regepower.mindnschanger.START"
        const val ACTION_STOP = "de.regepower.mindnschanger.STOP"

        private val ADDRESSES = listOf(
            "172.31.255.253" to 30,
            "192.168.234.55" to 24,
            "10.111.222.1" to 32
        )

        /** Server in use while the tunnel is up, null when off. */
        @Volatile
        var active: DnsServer? = null
            private set

        val running: Boolean get() = active != null

        /** UI callbacks (main thread) for state changes. */
        val listeners = CopyOnWriteArraySet<() -> Unit>()

        private fun setRunning(ctx: Context, server: DnsServer?) {
            active = server
            TileService.requestListeningState(ctx, ComponentName(ctx, DnsTileService::class.java))
            Handler(Looper.getMainLooper()).post { listeners.forEach { it() } }
        }

        /** Caller must have checked [VpnService.prepare] == null. Also re-applies changed settings. */
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            if (running) ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
