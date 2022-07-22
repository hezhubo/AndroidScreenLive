package com.hezb.live.recorder.gles

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Build
import android.view.Surface
import com.hezb.live.recorder.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    EglCore
 *
 * Description: EGL核心（display, context, config）.
 *
 * @author  hezhubo
 * @date    2022年07月20日 13:54
 */
class EglCore(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {

    private val EGL_RECORDABLE_ANDROID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        EGLExt.EGL_RECORDABLE_ANDROID
    } else {
        0x3142
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT

    init {
        // 打开与EGL显示服务器的连接
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display!")
        }
        // 初始化EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("unable to initialize EGL14")
        }
        // 让EGL选择匹配的EGLConfig
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,     // R
            EGL14.EGL_GREEN_SIZE, 8,   // G
            EGL14.EGL_BLUE_SIZE, 8,    // B
            EGL14.EGL_ALPHA_SIZE, 8,   // A
//            EGL14.EGL_DEPTH_SIZE, 0,   // 深度缓冲区位数
//            EGL14.EGL_STENCIL_SIZE, 0, // 模板缓冲区位数
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // egl版本 2.0
            EGL_RECORDABLE_ANDROID, 1, // android特定配置
            EGL14.EGL_NONE // 结束符
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val success =
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        if (!success || numConfigs[0] <= 0) {
            throw RuntimeException("unable to find RGBA8888 / 2 EGLConfig!")
        }
        eglConfig = configs[0]
        if (eglConfig == null) {
            throw RuntimeException("unable to find a suitable EGLConfig!")
        }
        // 创建渲染上下文
        val contextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, //
            EGL14.EGL_NONE
        )
        eglContext =
            EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext, contextAttribList, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext, failed : ${GLUtils.getEGLErrorString(EGL14.eglGetError())}")
        }
        // confirm with query
        val values = IntArray(1)
        EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        LogUtil.i(msg = "EGLContext created, client version ${values[0]}")
    }

    /**
     * 释放资源（可能会抛异常）
     */
    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    /**
     * 销毁EGLSurface
     *
     * @param eglSurface
     */
    fun releaseSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    /**
     * 创建EGL渲染窗口
     *
     * @param surface
     */
    fun createWindowSurface(surface: Any?): EGLSurface {
        if (surface !is Surface && surface !is SurfaceTexture) {
            throw RuntimeException("invalid surface : $surface")
        }
        return EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        ) ?: throw RuntimeException(
            "eglCreateWindowSurface, failed : ${GLUtils.getEGLErrorString(EGL14.eglGetError())}"
        )
    }

    /**
     * 指定上下文（关联特定的EGLContext和渲染表面）
     * （EGL规范要求eglMakeCurrent实现进行一次刷新）
     *
     * @param eglSurface
     */
    fun makeCurrent(eglSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent, failed : ${GLUtils.getEGLErrorString(EGL14.eglGetError())}")
        }
    }

    /**
     * 设置pts
     *
     * @param eglSurface
     * @param nanoseconds
     */
    fun setPresentationTime(eglSurface: EGLSurface, nanoseconds: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nanoseconds)
    }

    /**
     * Display与Surface数据交换
     *
     * @param eglSurface
     */
    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

}