package com.example.auxviewer

import android.media.AudioManager
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.example.auxviewer.databinding.ActivityMainBinding

class MainActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private val renderer by viewModels<VideoRenderer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.surface.holder.addCallback(this)

        binding.mirrorBtn.setOnClickListener { renderer.toggleMirror() }
        binding.flipBtn.setOnClickListener   { renderer.toggleFlip()   }
        binding.fmtBtn.setOnClickListener    { renderer.toggleFormat() }
        binding.audioBtn.setOnClickListener  { toggleAudio() }
    }

    private fun toggleAudio() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        // Simple audio-focus swap: keep media playing or duck it
        if (am.isMusicActive) {
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            binding.audioBtn.text = "Audio ⏸︎"
        } else {
            am.abandonAudioFocus(null)
            binding.audioBtn.text = "Audio ▶︎"
        }
    }

    /* ---------- Surface callbacks ---------- */
    override fun surfaceCreated(holder: SurfaceHolder) { renderer.open(holder.surface) }
    override fun surfaceChanged(holder: SurfaceHolder, f:Int, w:Int, h:Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { renderer.close() }
}
