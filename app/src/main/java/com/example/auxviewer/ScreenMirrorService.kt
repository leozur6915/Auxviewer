java.lang.NoSuchMethodError: … getParcelableExtra(String, Class)
``` :contentReference[oaicite:2]{index=2}.

---

### Drop-in fix → replace your **`ScreenMirrorService.kt`** completely

```kotlin
package com.example.auxviewer

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

class ScreenMirrorService : Service() {

    private var projection: MediaProjection? = null
    private lateinit var renderer: ScreenMirrorRenderer          // your existing renderer
    private val toggleRcvr = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            renderer.toggleMirror()                              // same helper you already call
        }
    }

    /* -------- foreground-service boilerplate -------- */
    private val chanId = "mirror_foreground"

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
            createNotificationChannel(
                NotificationChannel(
                    chanId, "Screen mirroring",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun tinyNotif(): Notification =
        Notification.Builder(this, chanId)
            .setContentTitle("Screen mirroring running")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .build()

    /* -------- entry-point -------- */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        /* compatible way to fetch the Intent we got from MainActivity */
        val projIntent: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("result_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("result_data") as? Intent
        }
        if (projIntent == null) return START_NOT_STICKY

        val mpMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpMgr.getMediaProjection(Activity.RESULT_OK, projIntent)

        // foreground – older API uses the 2-arg form
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                1, tinyNotif(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(1, tinyNotif())
        }

        renderer = ScreenMirrorRenderer(projection!!, resources.displayMetrics)
        renderer.start()

        registerReceiver(toggleRcvr, IntentFilter("TOGGLE_MIRROR_FROM_HW"))
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(toggleRcvr)
        renderer.stop()
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
