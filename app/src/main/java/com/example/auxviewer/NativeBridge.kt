package com.example.auxviewer

object NativeBridge {
    init { System.loadLibrary("auxcapture") }

    external fun startCapture(devicePath: String)
    external fun drawFrame(mirror: Boolean, flip:Boolean)
    external fun stopCapture()
    external fun toggleFormat()
}
