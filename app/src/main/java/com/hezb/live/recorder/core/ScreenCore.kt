package com.hezb.live.recorder.core

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.*
import android.view.Surface
import com.hezb.live.recorder.config.RecorderConfig
import com.hezb.live.recorder.filter.video.BaseVideoFilter
import com.hezb.live.recorder.gles.EglCore
import com.hezb.live.recorder.gles.GlUtil
import com.hezb.live.recorder.gles.Texture2DProgram
import com.hezb.live.recorder.util.LogUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Project Name: AndroidScreenLive
 * File Name:    ScreenCore
 *
 * Description: 屏幕录制核心.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:33
 */
class ScreenCore(private var mediaProjection: MediaProjection?) : BaseCore() {

    private var mEncodeFormat: MediaFormat? = null
    private var mEncoder: MediaCodec? = null
    private var mEncoderInputSurface: Surface? = null
    private var screenTextureId: Int = GlUtil.NO_TEXTURE
    private var mScreenSurfaceTexture: SurfaceTexture? = null
    private var mRenderHandler: VideoRenderHandler? = null
    private var mRenderHandlerThread: HandlerThread? = null
    private var mEncoderOutputThread: VideoEncoderOutputThread? = null
    private var mVirtualDisplay: VirtualDisplay? = null

    private val mStopSyncObj = Object()
    /** 滤镜锁 */
    private val mFilterLock = ReentrantLock(false)
    /** 滤镜 */
    private var mVideoFilter: BaseVideoFilter? = null

    private val mineType = MediaFormat.MIMETYPE_VIDEO_AVC
    private var videoCodecName: String? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var dpi = 1
    /** 渲染帧间隔（单位：毫秒） */
    private var frameInterval: Long = 1000 / 25

    /** 是否正在录制 */
    @Volatile
    private var isRecording = false
    /** 是否有可渲染帧 */
    @Volatile
    private var isFrameAvailable = false

    override fun prepare(config: RecorderConfig): Int {
        videoCodecName = config.videoCodecName
        videoWidth = config.videoSize.width
        videoHeight = config.videoSize.height
        dpi = config.virtualDisplayDpi
        frameInterval = (1000 / config.videoFrameRate).toLong()

        if (!createVideoCodec(config)) {
            return ErrorCode.VIDEO_FORMAT_ERROR
        }

        return ErrorCode.NO_ERROR
    }

    private fun createVideoCodec(config: RecorderConfig): Boolean {
        mEncodeFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mineType)
            setInteger(MediaFormat.KEY_WIDTH, videoWidth)
            setInteger(MediaFormat.KEY_HEIGHT, videoHeight)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFrameRate)
            setInteger(MediaFormat.KEY_CAPTURE_RATE, config.videoFrameRate)
            setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / config.videoFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.videoIFrameInterval)
            if (config.videoCodecProfile != 0 && config.videoCodecProfileLevel != 0) {
                setInteger(MediaFormat.KEY_PROFILE, config.videoCodecProfile)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_LEVEL, config.videoCodecProfileLevel)
                }
            }
            if (config.videoBitrateMode >= 0) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, config.videoBitrateMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.videoMaxBFrames > 0) {
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, config.videoMaxBFrames)
            }
        }
        LogUtil.i(msg = "video encode format : ${mEncodeFormat.toString()}")
        mEncoder = createEncoder(videoCodecName)
        return mEncoder != null
    }

    private fun createEncoder(videoCodecName: String?): MediaCodec? {
        if (!videoCodecName.isNullOrEmpty()) {
            try {
                return MediaCodec.createByCodecName(videoCodecName)
            } catch (e: Exception) {
                LogUtil.e(
                    msg = "error video codec name : ${videoCodecName}, can't create video encoder!",
                    tr = e
                )
            }
        }
        return try {
            MediaCodec.createEncoderByType(mineType)
        } catch (e: Exception) {
            LogUtil.e(msg = "can't create video encoder!", tr = e)
            null
        }
    }

    override fun start(collector: DataCollector): Int {
        // 启动解码器
        try {
            val encoder = mEncoder ?: createEncoder(videoCodecName).also { mEncoder = it }
            ?: return ErrorCode.VIDEO_START_ENCODER_ERROR
            encoder.configure(mEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mEncoderInputSurface = encoder.createInputSurface()
            encoder.start()
        } catch (e: Exception) {
            LogUtil.e(msg = "can't start video encoder!", tr = e)
            return ErrorCode.VIDEO_START_ENCODER_ERROR
        }

        // 生成一个纹理id
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        screenTextureId = textures[0]
        mScreenSurfaceTexture = SurfaceTexture(screenTextureId).apply {
            setDefaultBufferSize(videoWidth, videoHeight)
            setOnFrameAvailableListener {
                // 帧可用，通知渲染
                mRenderHandler?.let { renderHandler ->
                    if (mRenderHandlerThread?.isAlive == true && isRecording) {
                        renderHandler.sendEmptyMessage(MSG_WHAT_ON_FRAME_AVAILABLE)
                        return@setOnFrameAvailableListener
                    }
                }
                if (screenTextureId != GlUtil.NO_TEXTURE) {
                    isFrameAvailable = true
                }
            }
        }

        // 创建VirtualDisplay
        try {
            mVirtualDisplay = mediaProjection?.createVirtualDisplay(
                "Recorder-Display",
                videoWidth,
                videoHeight,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                Surface(mScreenSurfaceTexture),
                null,
                null
            )
        } catch (e: Exception) {
            LogUtil.e(msg = "can't create virtual display!", tr = e)
            return ErrorCode.VIRTUAL_DISPLAY_ERROR
        }

        // 启动渲染handler线程
        mRenderHandlerThread = object : HandlerThread("VideoRenderHandlerThread") {
            override fun onLooperPrepared() {
                mRenderHandler = VideoRenderHandler(looper).also {
                    it.sendEmptyMessage(MSG_WHAT_INIT)
                }
            }
        }.apply {
            start()
        }
        // 启动编码输出线程
        mEncoderOutputThread = VideoEncoderOutputThread(collector).apply { start() }

        return ErrorCode.NO_ERROR
    }

    override fun stop() {
        mEncoderOutputThread?.let {
            it.quit()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "video encoder output thread join error!", tr = e)
            }
        }
        mEncoderOutputThread = null

        mRenderHandler?.let {
            it.removeCallbacksAndMessages(null)
            it.sendEmptyMessage(MSG_WHAT_STOP)
            synchronized(mStopSyncObj) {
                try {
                    mStopSyncObj.wait() // 阻塞
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                it.removeCallbacksAndMessages(null)
                stopRenderThread()
                stopAll()
            }
        } ?: stopAll()
        mRenderHandler = null
    }

    private fun stopRenderThread() {
        mRenderHandlerThread?.let {
            it.quitSafely()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "video render handler thread join error!", tr = e)
            }
        }
        mRenderHandlerThread = null
    }

    private fun stopAll() {
        mEncoder?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                LogUtil.e(msg = "video encoder stop error!", tr = e)
            }
        }
        mEncoder = null

        try {
            mVirtualDisplay?.release()
            mVirtualDisplay = null
            mScreenSurfaceTexture?.let {
                mScreenSurfaceTexture = null
                it.setOnFrameAvailableListener(null)
                it.release()
                isFrameAvailable = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun release() {
        mediaProjection = null
    }

    /**
     * 获取滤镜
     *
     * @return
     */
    fun acquireVideoFilter(): BaseVideoFilter? {
        mFilterLock.lock()
        return mVideoFilter
    }

    /**
     * 释放滤镜
     */
    fun releaseVideoFilter() {
        mFilterLock.unlock()
    }

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

    /**
     * 动态设置码率
     *
     * @param bitrate
     */
    fun resetVideoBitrate(bitrate: Int) {
        mEncodeFormat?.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        if (isRecording) {
            mRenderHandler?.let {
                it.sendMessage(it.obtainMessage(MSG_WHAT_RESET_BITRATE, bitrate, 0))
            }
        }
    }

    companion object {
        const val MSG_WHAT_INIT = 1
        const val MSG_WHAT_ON_FRAME_AVAILABLE = 2
        const val MSG_WHAT_DRAW_ENCODER = 3
        const val MSG_WHAT_STOP = 4
        const val MSG_WHAT_RESET_BITRATE = 5
    }

    private inner class VideoRenderHandler(looper: Looper) : Handler(looper) {

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

        /** 滤镜 */
        private var mInnerVideoFilter: BaseVideoFilter? = null

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

                        isRecording = true

                        if (isFrameAvailable && !hasMessages(MSG_WHAT_ON_FRAME_AVAILABLE)) {
                            sendEmptyMessage(MSG_WHAT_ON_FRAME_AVAILABLE)
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        mOnErrorCallback?.onError("video render handler init error! ${e.message}")
                    }
                }

                MSG_WHAT_ON_FRAME_AVAILABLE -> {
                    try {
                        eglCore?.let {
                            it.makeCurrent(eglSurface)
                            mScreenSurfaceTexture?.updateTexImage()

                            source2dProgram?.let { program ->
                                GlUtil.draw2DFramebuffer(
                                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                    screenTextureId,
                                    sourceFramebuffer,
                                    videoWidth,
                                    videoHeight,
                                    program,
                                    shapeVerticesBuffer,
                                    source2DVerticesBuffer,
                                    drawIndicesBuffer
                                )

                                if (!hasMessages(MSG_WHAT_DRAW_ENCODER) && isRecording && looper.thread.isAlive) {
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
                        e.printStackTrace()
                        mOnErrorCallback?.onError("video render handler on frame available error! ${e.message}")
                    }
                }

                MSG_WHAT_DRAW_ENCODER -> {
                    if (isRecording) {
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
                        if (!isPause) {
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
                                        it.setPresentationTime(eglSurface, getPresentationTimeUs() * 1000)
                                        it.swapBuffers(eglSurface)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                mOnErrorCallback?.onError("video render handler draw encoder error! ${e.message}")
                            }
                        }
                    }
                }

                MSG_WHAT_STOP -> {
                    isRecording = false
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
                            screenTextureId = GlUtil.NO_TEXTURE
                        }
                        eglCore = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    synchronized(mStopSyncObj) {
                        try {
                            mStopSyncObj.notify() // 唤醒stop
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                MSG_WHAT_RESET_BITRATE -> {
                    try {
                        val bitrateBundle = Bundle()
                        bitrateBundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, msg.arg1)
                        mEncoder?.setParameters(bitrateBundle)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        /**
         * 获取时间戳（单位：微秒）
         */
        private fun getPresentationTimeUs(): Long {
            val millis = if (pauseDuration > 0) {
                SystemClock.uptimeMillis() - pauseDuration
            } else {
                SystemClock.uptimeMillis()
            }
            return millis * 1000
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
            } catch (e: InterruptedException) {
            }
            return false
        }

        private fun unlockVideoFilter() {
            mFilterLock.unlock()
        }
    }


    /**
     * 编码输出线程
     */
    private inner class VideoEncoderOutputThread(var collector: DataCollector?) : Thread() {
        private var isRunning = true

        fun quit() {
            isRunning = false
            collector = null
        }

        override fun run() {
            val outputBufferInfo = MediaCodec.BufferInfo()
            while (isRunning) {
                mEncoder?.let { encoder ->
                    val eobIndex = try {
                        encoder.dequeueOutputBuffer(outputBufferInfo, 5000)
                    } catch (e: Exception) {
                        LogUtil.e(msg = "dequeue video output buffer error!", tr = e)
                        if (isRunning) {
                            mOnErrorCallback?.onError("dequeue video output buffer error!")
                        }
                        quit() // 出错直接停止运行线程
                        return
                    }
                    if (!isRunning) {
                        return
                    }
                    collector?.let {
                        when (eobIndex) {
                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                it.addTrack(encoder.outputFormat, true)
                            }
                            else -> {
                                if (outputBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && outputBufferInfo.size != 0) {
                                    try {
                                        val encodedData = encoder.getOutputBuffer(eobIndex) ?: return@let
                                        encodedData.position(outputBufferInfo.offset + 4) // H264 NALU: 00 00 00 01(4字节) 起始码(4字节)；后一个字节为NALU type
                                        encodedData.limit(outputBufferInfo.offset + outputBufferInfo.size)
                                        it.writeData(encodedData, outputBufferInfo, true)
                                    } catch (e : Exception) {
                                        LogUtil.e(msg = "video write data error!", tr = e)
                                    }
                                }
                                encoder.releaseOutputBuffer(eobIndex, false)

                                if (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    isRunning = false
                                }
                            }
                        }
                    }
                } ?: break
            }
        }
    }

}