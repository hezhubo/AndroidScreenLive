package com.hezb.live.recorder.filter.video

import com.hezb.live.recorder.gles.GlUtil
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    BaseVideoFilter
 *
 * Description: 视频(opengl)滤镜基类.
 *
 * @author  hezhubo
 * @date    2022年07月16日 21:03
 */
abstract class BaseVideoFilter {

    protected var sizeWidth = 0
    protected var sizeHeight = 0
    protected var drawIndicesBuffer: ShortBuffer? = null

    open fun init(width : Int, height :Int) {
        sizeWidth = width
        sizeHeight = height
        drawIndicesBuffer = GlUtil.getDrawIndicesBuffer()
    }

    abstract fun onDraw(
        texture: Int,
        targetFramebuffer: Int,
        shapeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer
    )

    open fun onDestroy() {}

}