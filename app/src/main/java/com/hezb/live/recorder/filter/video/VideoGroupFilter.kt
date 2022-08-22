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
class VideoGroupFilter(filterList: MutableList<BaseVideoFilter>) : BaseVideoFilter() {

    private val filterWrapperList = LinkedList<FilterWrapper>()

    init {
        for ((i, filter) in filterList.withIndex()) {
            if (i > 0) {
                // 添加空滤镜处理纹理上下颠倒问题
                filterWrapperList.add(FilterWrapper(FixUpsideDownFilter()))
            }
            filterWrapperList.add(FilterWrapper(filter))
        }
    }

    override fun init(width: Int, height: Int) {
        super.init(width, height)
        if (filterWrapperList.isEmpty()) {
            return
        }
        for (filterWrapper in filterWrapperList) {
            filterWrapper.videoFilter.init(width, height)
            filterWrapper.texture = GlUtil.createImageTexture(width, height)
            filterWrapper.framebuffer = GlUtil.createFramebufferLinkTexture2D(filterWrapper.texture)
        }
    }

    override fun onDraw(
        texture: Int,
        targetFramebuffer: Int,
        shapeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer
    ) {
        if (filterWrapperList.isEmpty()) {
            return
        }
        var preFilterWrapper: FilterWrapper? = null
        for ((i, filterWrapper) in filterWrapperList.withIndex()) {
            val currentTexture = preFilterWrapper?.texture ?: texture
            if (i == filterWrapperList.size - 1) {
                filterWrapper.videoFilter.onDraw(
                    currentTexture,
                    targetFramebuffer,
                    shapeBuffer,
                    textureBuffer
                )
            } else {
                filterWrapper.videoFilter.onDraw(
                    currentTexture,
                    filterWrapper.framebuffer,
                    shapeBuffer,
                    textureBuffer
                )
            }
            preFilterWrapper = filterWrapper
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (filterWrapperList.isEmpty()) {
            return
        }
        for (filterWrapper in filterWrapperList) {
            filterWrapper.videoFilter.onDestroy()
            GLES20.glDeleteFramebuffers(1, intArrayOf(filterWrapper.framebuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(filterWrapper.texture), 0)
        }
    }

    private class FilterWrapper(val videoFilter: BaseVideoFilter) {
        var texture: Int = GlUtil.NO_TEXTURE
        var framebuffer: Int = 0
    }

    private class FixUpsideDownFilter : BaseVideoFilter() {

        private var glProgram: Int = 0
        private var glPositionLoc: Int = 0
        private var glTextureCoordLoc: Int = 0
        private var glTextureLoc: Int = 0

        override fun init(width: Int, height: Int) {
            super.init(width, height)
            if (width == 0 || height == 0) {
                return
            }
            glProgram = GlUtil.createProgram(Texture2DProgram.VERTEX_SHADER, Texture2DProgram.FRAGMENT_SHADER_2D)
            if (glProgram == 0) {
                return
            }
            GLES20.glUseProgram(glProgram)
            glPositionLoc = GLES20.glGetAttribLocation(glProgram, "aPosition")
            glTextureCoordLoc = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
            glTextureLoc = GLES20.glGetUniformLocation(glProgram, "uTexture")
        }

        override fun onDraw(
            texture: Int,
            targetFramebuffer: Int,
            shapeBuffer: FloatBuffer,
            textureBuffer: FloatBuffer
        ) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer)
            GLES20.glUseProgram(glProgram)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(glTextureLoc, 0)
            GLES20.glEnableVertexAttribArray(glPositionLoc)
            GLES20.glEnableVertexAttribArray(glTextureCoordLoc)
            shapeBuffer.position(0)
            GLES20.glVertexAttribPointer(
                glPositionLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                2 * 4,
                shapeBuffer
            )
            textureBuffer.position(0)
            GLES20.glVertexAttribPointer(
                glTextureCoordLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                2 * 4,
                textureBuffer
            )
            GLES20.glViewport(0, 0, sizeWidth, sizeHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawIndicesBuffer?.let {
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, it.limit(), GLES20.GL_UNSIGNED_SHORT, it)
            }
            GLES20.glFinish()
            GLES20.glDisableVertexAttribArray(glPositionLoc)
            GLES20.glDisableVertexAttribArray(glTextureCoordLoc)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUseProgram(0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        override fun onDestroy() {
            super.onDestroy()
            if (glProgram != 0) {
                GLES20.glDeleteProgram(glProgram)
            }
            glProgram = 0
        }
    }

}