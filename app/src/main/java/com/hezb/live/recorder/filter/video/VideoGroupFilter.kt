package com.hezb.live.recorder.filter.video

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.hezb.live.recorder.gles.GlUtil
import com.hezb.live.recorder.gles.Texture2DProgram
import java.nio.FloatBuffer
import java.util.*

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoGroupFilter
 *
 * Description: 视频(opengl)滤镜组.
 *
 * @author  hezhubo
 * @date    2022年07月24日 23:46
 */
class VideoGroupFilter(private val filterList: MutableList<BaseVideoFilter>) : BaseVideoFilter() {

    override fun init(width: Int, height: Int) {
        super.init(width, height)
        if (filterList.isEmpty()) {
            return
        }
        for (filter in filterList) {
            filter.init(width, height)
        }
    }

    override fun onDraw(
        sourceTexture: Int,
        shapeVerticesBuffer: FloatBuffer,
        textureVerticesBuffer: FloatBuffer
    ): Int {
        var texture = sourceTexture
        for (filter in filterList) {
            texture = filter.onDraw(texture, shapeVerticesBuffer, textureVerticesBuffer)
        }
        return texture
    }

    override fun onDestroy() {
        super.onDestroy()
        if (filterList.isEmpty()) {
            return
        }
        for (filter in filterList) {
            filter.onDestroy()
        }
    }

}