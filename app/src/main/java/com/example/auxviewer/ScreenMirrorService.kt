package com.example.auxviewer

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenMirrorService : Service() {

    companion object {
        const val NOTIF_CH_ID = "mirror_channel"
        const val NOTIF_ID    = 42
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1️⃣  promote to foreground *before* touching MediaProjection
        promoteToForeground()

        // 2️⃣  obtain MediaProjection and create the VirtualDisplay
        val mgr = getSystemService(MediaProjectionManager::class.java)
        val projIntent = intent?.getParcelableExtra(
            "result_data", Intent::class.java) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val projection = mgr.getMediaProjection(Activity.RESULT_OK, projIntent)
        val surface = VideoRenderer.instance?.createMirrorSurface()
            ?: error("renderer not ready")
        val dm = resources.displayMetrics
        projection.createVirtualDisplay(
            "MirrorDisplay",
            dm.widthPixels, dm.heightPixels, dm.densityDpi,
            0, surface, null, null
        )

        return START_STICKY
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(NOTIF_CH_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CH_ID, "Screen mirror",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val notif = NotificationCompat.Builder(this, NOTIF_CH_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Screen-mirror running")
                .setContentText("Tap to stop")
                .setOngoing(true)
                .build()

            // Pass the proper foreground-service type flag (Android 12+)
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
