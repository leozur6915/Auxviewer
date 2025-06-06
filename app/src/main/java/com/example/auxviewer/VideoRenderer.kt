package com.example.auxviewer

import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface

class VideoRenderer {

    companion object {
        /** Global reference so the Service can reach the renderer */
        var instance: VideoRenderer? = null
            private set
    }

    // --- GL objects ---
    private var eglDisplay: EGLDisplay? = null
    private var eglSurface: EGLSurface? = null
    private var eglContext: EGLContext? = null

    private var oesTex   = 0
    private var shader   = 0
    private var mirror   = false

    // Surface given to MediaProjection
    private var projectionSurface: Surface? = null

    // --- Public API --------------------------------------------------------

    /** Called from MainActivity.surfaceCreated() */
    fun open(windowSurface: Surface) {
        instance = this
        initEGL(windowSurface)
        initGLObjects()
    }

    fun close() {
        releaseGL()
        instance = null
    }

    /** Toggle mirror flag from button & steering-wheel key */
    fun setMirror(enable: Boolean) { mirror = enable }

    /** Service calls this to obtain a Surface to feed captured frames */
    fun createMirrorSurface(): Surface {
        if (projectionSurface == null) {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            oesTex = tex[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            val st = SurfaceTexture(oesTex)
            st.setOnFrameAvailableListener { drawFrame(st) }
            projectionSurface = Surface(st)
        }
        return projectionSurface!!
    }

    // --- EGL + GL helpers --------------------------------------------------

    private fun initEGL(windowSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)
        val attrib = intArrayOf(EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, num, 0)
        val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
        val surfAttrib = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], windowSurface, surfAttrib, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun initGLObjects() {
        shader = createProgram(VS, FS)
    }

    private fun drawFrame(st: SurfaceTexture) {
        st.updateTexImage()
        GLES20.glViewport(0, 0, 1280, 720)
        GLES20.glUseProgram(shader)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader, "uTex"), 0)
        GLES20.glUniform1i(
            GLES20.glGetUniformLocation(shader, "uMirror"),
            if (mirror) 1 else 0)
        // draw a full-screen quad (VAO setup omitted for brevity) …
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun releaseGL() {
        projectionSurface?.release(); projectionSurface = null
        if (eglDisplay != null) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
    }

    // --- Simple OES shader -------------------------------------------------

    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        fun compile(type:Int, src:String):Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src); GLES20.glCompileShader(id)
            return id
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs); GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private val VS = """
        attribute vec4 aPos;
        attribute vec2 aTex;
        varying vec2 vTex;
        void main() {
            gl_Position = aPos;
            vTex = aTex;
        }""".trimIndent()

    private val FS = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTex;
        uniform int uMirror;
        varying vec2 vTex;
        void main() {
            vec2 uv = vTex;
            if (uMirror == 1) uv.x = 1.0 - uv.x;
            gl_FragColor = texture2D(uTex, uv);
        }""".trimIndent()
}
