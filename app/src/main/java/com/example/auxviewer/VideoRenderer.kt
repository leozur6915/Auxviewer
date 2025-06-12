package com.example.auxviewer

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import android.view.Surface

/**
 * Handles all EGL / OpenGL work for showing either
 * the MediaProjection stream or any other OES texture.
 */
class VideoRenderer {

    companion object {
        /** Global reference so the service can reach the renderer. */
        var instance: VideoRenderer? = null
            private set

        private const val TAG = "VideoRenderer"
    }

    /* ---------- EGL objects ---------- */
    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    /* ---------- GL objects ---------- */
    private var oesTexId = 0
    private var shaderProgram = 0

    /* ---------- state flags ---------- */
    private var mirror = false
    private var flipY  = false

    /* Surface given to MediaProjection */
    private var projSurface: Surface? = null
    private var projSurfaceTex: SurfaceTexture? = null

    /* ---------- public API ---------- */

    fun open(windowSurface: Surface) {
        instance = this
        initEgl(windowSurface)
        initGlObjects()
    }

    fun close() {
        projSurface?.release()
        projSurface = null
        projSurfaceTex = null

        if (eglDisplay != null && eglSurface != null) {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        instance = null
    }

    /** Service calls this to get a Surface for MediaProjection. */
    fun createMirrorSurface(): Surface {
        if (projSurface == null) {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            oesTexId = tex[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
            )
            projSurfaceTex = SurfaceTexture(oesTexId).apply {
                setOnFrameAvailableListener { drawFrame(this) }
            }
            projSurface = Surface(projSurfaceTex)
        }
        return projSurface!!
    }

    /* UI buttons & steering-wheel actions */
    fun setMirror(enable: Boolean) { mirror = enable }
    fun toggleMirror() { mirror = !mirror }
    fun toggleFlip()   { flipY  = !flipY  }
    fun toggleFormat() { /* placeholder – NTSC/PAL no-op */ }

    /* ---------- core drawing ---------- */

    private fun drawFrame(st: SurfaceTexture) {
        /* Make sure EGL context is current on this callback thread */
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        try {
            st.updateTexImage()
        } catch (ise: IllegalStateException) {
            Log.w(TAG, "updateTexImage failed – dropping frame", ise)
            return
        }

        GLES20.glViewport(0, 0, 1280, 720)
        GLES20.glUseProgram(shaderProgram)

        val uTex   = GLES20.glGetUniformLocation(shaderProgram, "uTex")
        val uMirror= GLES20.glGetUniformLocation(shaderProgram, "uMirror")
        val uFlip  = GLES20.glGetUniformLocation(shaderProgram, "uFlip")

        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform1i(uMirror, if (mirror) 1 else 0)
        GLES20.glUniform1i(uFlip,   if (flipY)  1 else 0)

        // Draw full-screen quad (hard-coded clip-rect)
        val verts = floatArrayOf(
            -1f, -1f,   0f, 1f,
             1f, -1f,   1f, 1f,
            -1f,  1f,   0f, 0f,
             1f,  1f,   1f, 0f
        )
        val vb = java.nio.ByteBuffer
            .allocateDirect(verts.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts).apply { position(0) }

        val aPos = GLES20.glGetAttribLocation(shaderProgram, "aPos")
        val aTex = GLES20.glGetAttribLocation(shaderProgram, "aTex")

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vb)
        vb.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, vb)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)

        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    /* ---------- init helpers ---------- */

    private fun initEgl(windowSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("No EGL display")

        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,    EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val cfg = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribs, 0, cfg, 0, 1, num, 0)
        eglConfig = cfg[0]

        val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0
        )

        val surfAttrib = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, windowSurface, surfAttrib, 0
        )

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun initGlObjects() {
        shaderProgram = createProgram(VS_SRC, FS_SRC)
    }

    private fun createProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val vsId = compile(GLES20.GL_VERTEX_SHADER, vs)
        val fsId = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vsId)
        GLES20.glAttachShader(prog, fsId)
        GLES20.glLinkProgram(prog)
        return prog
    }

    /* ---------- simple OES shader ---------- */

    private val VS_SRC = """
        attribute vec4 aPos;
        attribute vec2 aTex;
        varying vec2 vTex;
        void main() {
            gl_Position = aPos;
            vTex = aTex;
        }
    """.trimIndent()

    private val FS_SRC = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTex;
        uniform int uMirror;
        uniform int uFlip;
        varying vec2 vTex;
        void main() {
            vec2 uv = vTex;
            if (uMirror == 1) uv.x = 1.0 - uv.x;
            if (uFlip   == 1) uv.y = 1.0 - uv.y;
            gl_FragColor = texture2D(uTex, uv);
        }
    """.trimIndent()
}
