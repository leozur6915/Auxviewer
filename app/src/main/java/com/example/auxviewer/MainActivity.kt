package com.example.auxviewer

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.auxviewer.databinding.ActivityMainBinding

class MainActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private val renderer = VideoRenderer()

    /* ---------- persistent prefs ---------- */
    private val prefs by lazy { getSharedPreferences("mirror_prefs", MODE_PRIVATE) }
    private var mirrorKeyCode = -1            // set later, after Context ready

    /* ---------- MediaProjection launcher ---------- */
    private val projMgr by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val projLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                // Pass the projection Intent to the foreground-service
                val svc = Intent(this, ScreenMirrorService::class.java)
                svc.putExtra("result_data", res.data)
                startService(svc)                     // Service promotes itself
                mirroring = true
                binding.mirrorBtn.isEnabled = false
            }
        }

    /* ---------- lifecycle ---------- */
    private var mirroring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mirrorKeyCode = prefs.getInt("pref_mirror_key", -1)

        binding.surface.holder.addCallback(this)

        binding.mirrorBtn.setOnClickListener {
            projLauncher.launch(projMgr.createScreenCaptureIntent())
        }
        binding.flipBtn.setOnClickListener { renderer.toggleFlip() }
        binding.fmtBtn.setOnClickListener  { renderer.toggleFormat() }
        binding.audioBtn.setOnClickListener { toggleAudio() }
        binding.calibBtn.setOnClickListener { startCalibrationDialog() }
    }

    /* ---------- steering-wheel calibration ---------- */
    private fun startCalibrationDialog() {
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle("Calibrate mirror key")
            .setMessage("Press the steering-wheel button you want to use…")
            .setCancelable(false)
            .create()
        dlg.setOnKeyListener { _, keyCode, _ ->
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                mirrorKeyCode = keyCode
                prefs.edit().putInt("pref_mirror_key", keyCode).apply()
                Toast.makeText(this, "Saved keyCode $keyCode", Toast.LENGTH_SHORT).show()
                dlg.dismiss(); true
            } else false
        }
        dlg.show()
    }

    /* ---------- intercept wheel keys while mirroring ---------- */
    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (mirroring &&
            ev.action == KeyEvent.ACTION_DOWN &&
            ev.keyCode == mirrorKeyCode
        ) {
            sendBroadcast(Intent("TOGGLE_MIRROR_FROM_HW"))
            return true        // consume event
        }
        return super.dispatchKeyEvent(ev)
    }

    /* ---------- audio toggle (stub) ---------- */
    private fun toggleAudio() { /* TODO: implement if needed */ }

    /* ---------- Surface callbacks ---------- */
    override fun surfaceCreated(h: SurfaceHolder)   = renderer.open(h.surface)
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) = Unit
    override fun surfaceDestroyed(h: SurfaceHolder) = renderer.close()
}
