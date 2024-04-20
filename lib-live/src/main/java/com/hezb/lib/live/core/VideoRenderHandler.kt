package com.hezb.lib.live.core

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.view.Surface
import com.hezb.lib.live.filter.video.BaseVideoFilter
import com.hezb.lib.live.gles.EglCore
import com.hezb.lib.live.gles.GlUtil
import com.hezb.lib.live.gles.Texture2DProgram
import com.hezb.lib.live.util.LogUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoRenderHandler
 *
 * Description: 视频帧渲染Handler.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:15
 */
class VideoRenderHandler private constructor(looper: Looper) : Handler(looper) {

    companion object {
        const val MSG_WHAT_INIT = 1
        const val MSG_WHAT_ON_FRAME_AVAILABLE = 2
        const val MSG_WHAT_DRAW_ENCODER = 3
        const val MSG_WHAT_STOP = 4

        /**
         * 启动渲染handler线程
         */
        fun startRender(callbackRender: (renderer: VideoRenderHandler) -> Unit): HandlerThread {
            return object : HandlerThread("VideoRenderHandlerThread") {
                override fun onLooperPrepared() {
                    callbackRender(VideoRenderHandler(looper))
                }
            }.apply {
                start()
            }
        }
    }

    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var sourceTexture: Int = 0
    private var sourceFramebuffer: Int = 0

    /** 屏幕(颜色)纹理附着程序 */
    private var source2dProgram: Texture2DProgram? = null
    /** 最终绘制到编码器的纹理附着程序 */
    private var drawProgram: Texture2DProgram? = null

    private val drawIndicesBuffer = GlUtil.getDrawIndicesBuffer()
    private val shapeVerticesBuffer = GlUtil.getShapeVerticesBuffer()
    private val source2DVerticesBuffer = GlUtil.getScreenTextureVerticesBuffer()
    private val targetVerticesBuffer = GlUtil.getTargetTextureVerticesBuffer()

    private var mEncoderInputSurface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0
    /** 渲染帧间隔（单位：毫秒） */
    private var frameInterval: Long = 1000 / 25

    private val mStopSyncObj = Object()
    /** 滤镜锁 */
    private val mFilterLock = ReentrantLock(false)
    /** 滤镜 */
    private var mVideoFilter: BaseVideoFilter? = null
    /** 使用中的滤镜 */
    private var mInnerVideoFilter: BaseVideoFilter? = null

    /** 渲染出错回调 */
    var onErrorCallback: ((e: Exception) -> Unit)? = null

    /**
     * 设置滤镜
     *
     * @param baseVideoFilter
     */
    fun setVideoFilter(baseVideoFilter: BaseVideoFilter?) {
        mFilterLock.lock()
        mVideoFilter = baseVideoFilter
        mFilterLock.unlock()
    }

    fun init(videoEncoder: VideoEncoder) {
        mEncoderInputSurface = videoEncoder.getInputSurface()
        videoWidth = videoEncoder.getVideoSize().width
        videoHeight = videoEncoder.getVideoSize().height
        frameInterval = (1000 / videoEncoder.getVideoFrameRate()).toLong()
        sendEmptyMessage(MSG_WHAT_INIT)
    }

    fun onFrameAvailable(surfaceTexture: SurfaceTexture, surfaceTextureId: Int) {
        sendMessage(
            Message.obtain(
                this,
                MSG_WHAT_ON_FRAME_AVAILABLE,
                surfaceTextureId,
                0,
                surfaceTexture
            )
        )
    }

    fun stop(thread: HandlerThread) {
        removeCallbacksAndMessages(null)
        sendEmptyMessage(MSG_WHAT_STOP)
        synchronized(mStopSyncObj) {
            try {
                mStopSyncObj.wait() // 阻塞
            } catch (e: Exception) {
                e.printStackTrace()
            }
            removeCallbacksAndMessages(null)
            thread.quitSafely()
            thread.join()
            try {
                thread.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "video render handler thread join error!", tr = e)
            }
        }
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_WHAT_INIT -> {
                try {
                    eglCore = EglCore().also {
                        eglSurface = it.createWindowSurface(mEncoderInputSurface)
                        it.makeCurrent(eglSurface)
                    }
                    // 创建空纹理及帧缓冲区对象
                    sourceTexture = GlUtil.createImageTexture(videoWidth, videoHeight)
                    sourceFramebuffer = GlUtil.createFramebufferLinkTexture2D(sourceTexture)
                    // 开启 GL_TEXTURE_EXTERNAL_OES 纹理
                    GLES20.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                    // 创建纹理程序
                    drawProgram = Texture2DProgram(fragmentShaderCode = Texture2DProgram.FRAGMENT_SHADER_2D)
                    source2dProgram = Texture2DProgram(fragmentShaderCode = Texture2DProgram.FRAGMENT_SHADER_SOURCE2D)

                } catch (e: Exception) {
                    LogUtil.e(msg = "video render handler thread init error!", tr = e)
                    onErrorCallback?.invoke(e)
                }
            }

            MSG_WHAT_ON_FRAME_AVAILABLE -> {
                try {
                    eglCore?.let {
                        it.makeCurrent(eglSurface)
                        (msg.obj as? SurfaceTexture)?.updateTexImage()

                        source2dProgram?.let { program ->
                            GlUtil.draw2DFramebuffer(
                                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                msg.arg1, // arg1存放TextureId
                                sourceFramebuffer,
                                videoWidth,
                                videoHeight,
                                program,
                                shapeVerticesBuffer,
                                source2DVerticesBuffer,
                                drawIndicesBuffer
                            )

                            if (!hasMessages(MSG_WHAT_DRAW_ENCODER) && looper.thread.isAlive) {
                                sendMessage(
                                    obtainMessage(
                                        MSG_WHAT_DRAW_ENCODER,
                                        System.currentTimeMillis() + frameInterval
                                    )
                                )
                            }
                        }
                    }

                } catch (e: Exception) {
                    LogUtil.e(msg = "video render handler thread on frame available error!", tr = e)
                    onErrorCallback?.invoke(e)
                }
            }

            MSG_WHAT_DRAW_ENCODER -> {
                val time = msg.obj as Long
                val interval = time + frameInterval - System.currentTimeMillis()
                if (interval > 0) {
                    sendMessageDelayed(
                        obtainMessage(
                            MSG_WHAT_DRAW_ENCODER,
                            System.currentTimeMillis() + interval
                        ), interval
                    )
                } else {
                    // 时间间隔不足，立马补帧
                    sendMessage(
                        obtainMessage(
                            MSG_WHAT_DRAW_ENCODER,
                            System.currentTimeMillis() + frameInterval
                        )
                    )
                }
                if (!TimestampHelper.isPause) {
                    try {
                        eglCore?.let {
                            it.makeCurrent(eglSurface)

                            drawProgram?.let { program ->
                                val filterTexture = drawFilterFramebuffer()
                                if (filterTexture == GlUtil.NO_TEXTURE) {
                                    GlUtil.drawTexture2D(
                                        sourceTexture,
                                        program,
                                        shapeVerticesBuffer,
                                        targetVerticesBuffer,
                                        drawIndicesBuffer
                                    )
                                } else {
                                    GlUtil.drawTexture2D(
                                        filterTexture,
                                        program,
                                        shapeVerticesBuffer,
                                        targetVerticesBuffer,
                                        drawIndicesBuffer
                                    )
                                }
                                it.setPresentationTime(eglSurface, TimestampHelper.getPresentationTimeUs() * 1000)
                                it.swapBuffers(eglSurface)
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(msg = "video render handler thread draw encoder error!", tr = e)
                        onErrorCallback?.invoke(e)
                    }
                }
            }

            MSG_WHAT_STOP -> {
                removeCallbacksAndMessages(null)
                try {
                    eglCore?.let {
                        it.makeCurrent(eglSurface)
                        // 销毁滤镜
                        mInnerVideoFilter?.let { filter ->
                            mFilterLock.lock()
                            filter.onDestroy()
                            mFilterLock.unlock()
                        }
                        GLES20.glDeleteProgram(drawProgram?.program ?: 0)
                        GLES20.glDeleteProgram(source2dProgram?.program ?: 0)
                        GLES20.glDeleteFramebuffers(1, intArrayOf(sourceFramebuffer), 0)
                        GLES20.glDeleteTextures(1, intArrayOf(sourceTexture), 0)
                        it.releaseSurface(eglSurface)
                        eglSurface = EGL14.EGL_NO_SURFACE
                        it.release()
                    }
                    eglCore = null
                } catch (e: Exception) {
                    LogUtil.e(msg = "video render handler thread stop error!", tr = e)
                }

                synchronized(mStopSyncObj) {
                    try {
                        mStopSyncObj.notify() // 唤醒stop
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun drawFilterFramebuffer(): Int {
        if (lockVideoFilter()) {
            if (mVideoFilter != mInnerVideoFilter) {
                mInnerVideoFilter?.onDestroy()
                mInnerVideoFilter = mVideoFilter
                mInnerVideoFilter?.init(videoWidth, videoHeight)
            }
            val filterTexture = mInnerVideoFilter?.onDraw(
                sourceTexture,
                shapeVerticesBuffer,
                targetVerticesBuffer
            ) ?: GlUtil.NO_TEXTURE
            unlockVideoFilter()
            return filterTexture
        }
        return GlUtil.NO_TEXTURE
    }

    /**
     * @return true if filter locked & filter!=null
     */
    private fun lockVideoFilter(): Boolean {
        try {
            val locked: Boolean = mFilterLock.tryLock(3L, TimeUnit.MILLISECONDS)
            return if (locked) {
                if (mVideoFilter != null) {
                    true
                } else {
                    mFilterLock.unlock()
                    false
                }
            } else {
                false
            }
        } catch (_: InterruptedException) {
        }
        return false
    }

    private fun unlockVideoFilter() {
        mFilterLock.unlock()
    }
}