package com.example.auxviewer

import android.opengl.*
import android.util.Log
import androidx.lifecycle.ViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VideoRenderer : ViewModel() {

    private var mirror = false
    private var flip   = false
    private var surface: android.view.Surface? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    fun open(surf: android.view.Surface) {
        surface = surf
        // 1. init EGL
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(eglDisplay, null, 0, null, 0)
        val attrib = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, num, 0)
        val ctxAttr = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // 2. TODO — open /dev/video0 via NativeBridge and stream to a GL texture
        NativeBridge.startCapture("/dev/video0")

        // 3. very crude render loop
        Thread {
            while (surface != null) {
                NativeBridge.drawFrame(mirror, flip)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }
        }.start()
    }

    fun toggleMirror() { mirror = !mirror }
    fun toggleFlip()   { flip   = !flip   }

    fun toggleFormat() {
        // Placeholder: send ioctl via JNI
        NativeBridge.toggleFormat()
    }

    fun close() {
        surface = null
        NativeBridge.stopCapture()
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}
