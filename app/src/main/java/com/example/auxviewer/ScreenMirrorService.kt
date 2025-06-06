package com.example.auxviewer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Surface

class ScreenMirrorService : Service() {

    private lateinit var projMgr: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var vDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var mirrored = false

    /* --- receive toggle from steering-wheel key --- */
    private val toggleRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            mirrored = !mirrored
            VideoRenderer.instance?.setMirror(mirrored)
        }
    }

    override fun onCreate() {
        registerReceiver(toggleRx, IntentFilter("TOGGLE_MIRROR_FROM_HW"))
        createNotifChannel()
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val code = i!!.getIntExtra("code", 0)
        val data = i.getParcelableExtra<Intent>("data")!!
        projMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projMgr.getMediaProjection(code, data)

        surface = VideoRenderer.instance?.createMirrorSurface()
        vDisplay = projection!!.createVirtualDisplay(
            "AUXMirror", 1280, 720, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface, null, null
        )
        startForeground(1, notif("Mirroring ON"))
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(toggleRx)
        vDisplay?.release()
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun notif(text: String): Notification =
        Notification.Builder(this, "mirror")
            .setContentTitle(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel("mirror", "Mirror", NotificationManager.IMPORTANCE_LOW)
            ch.enableLights(false); ch.enableVibration(false); ch.lightColor = Color.BLUE
            mgr.createNotificationChannel(ch)
        }
    }
}
