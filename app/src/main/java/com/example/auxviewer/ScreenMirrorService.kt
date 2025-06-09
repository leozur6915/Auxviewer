package com.example.auxviewer

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that holds the MediaProjection virtual-display
 * and listens for the steering-wheel toggle broadcast.
 */
class ScreenMirrorService : Service() {

    companion object {
        private const val CHANNEL_ID = "mirror_channel"
        private const val NOTIF_ID   = 1
    }

    private var projection: MediaProjection? = null
    private var running = false

    /* receive "TOGGLE_MIRROR_FROM_HW" from MainActivity.dispatchKeyEvent */
    private val toggleRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            VideoRenderer.instance?.toggleMirror()
        }
    }

    /* ---------- Service lifecycle ---------- */

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(toggleRx, IntentFilter("TOGGLE_MIRROR_FROM_HW"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running || intent == null) return START_STICKY   // already active

        /* 1 ─ promote to foreground (mandatory for MediaProjection) */
        startForeground(
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 29)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else 0
        )

        /* 2 ─ obtain the MediaProjection intent passed from MainActivity */
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("result_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("result_data")
        }

        if (resultData == null) { stopSelf(); return START_NOT_STICKY }

        val projMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection  = projMgr.getMediaProjection(Activity.RESULT_OK, resultData)

        /* 3 ─ feed frames into the existing VideoRenderer */
        val dm  = resources.displayMetrics
        val surf = VideoRenderer.instance?.createMirrorSurface()
            ?: run { stopSelf(); return START_NOT_STICKY }

        projection!!.createVirtualDisplay(
            "MirrorDisplay",
            dm.widthPixels, dm.heightPixels, dm.densityDpi,
            0, surf, null, null
        )

        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(toggleRx)
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    /* ---------- helpers ---------- */

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "Screen mirroring",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    private fun buildNotification(): Notification =
        if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Screen mirroring active")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Screen mirroring active")
                .setOngoing(true)
                .build()
        }
}
