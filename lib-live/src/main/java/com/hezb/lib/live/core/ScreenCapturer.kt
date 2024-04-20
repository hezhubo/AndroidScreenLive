package com.hezb.lib.live.core

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.opengl.GLES20
import android.view.Surface
import com.hezb.lib.live.model.Size

/**
 * Project Name: AndroidScreenLive
 * File Name:    ScreenCapturer
 *
 * Description: 屏幕采集器.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:10
 */
class ScreenCapturer(mediaProjection: MediaProjection, videoSize: Size, dpi: Int) {

    private val mVirtualDisplay: VirtualDisplay
    private val screenTextureId: Int
    private val mScreenSurfaceTexture: SurfaceTexture

    private var onFrameAvailableListener: ((surfaceTexture: SurfaceTexture, surfaceTextureId: Int) -> Unit)? = null

    init {
        // 生成一个纹理id
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        screenTextureId = textures[0]
        mScreenSurfaceTexture = SurfaceTexture(screenTextureId).apply {
            setDefaultBufferSize(videoSize.width, videoSize.height)
            setOnFrameAvailableListener { surfaceTexture ->
                // 帧可用，通知渲染
                onFrameAvailableListener?.invoke(surfaceTexture, screenTextureId)
            }
        }
        // 创建VirtualDisplay
        mVirtualDisplay = mediaProjection.createVirtualDisplay(
            "Capturer-Display",
            videoSize.width,
            videoSize.height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            Surface(mScreenSurfaceTexture),
            null,
            null
        )
    }

    fun setOnFrameAvailableListener(listener: ((surfaceTexture: SurfaceTexture, surfaceTextureId: Int) -> Unit)?) {
        onFrameAvailableListener = listener
    }

    fun release() {
        mVirtualDisplay.release()
        mScreenSurfaceTexture.setOnFrameAvailableListener(null)
        mScreenSurfaceTexture.release()
    }

}