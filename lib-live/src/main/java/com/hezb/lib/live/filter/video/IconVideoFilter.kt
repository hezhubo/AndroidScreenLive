package com.hezb.lib.live.filter.video

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import com.hezb.lib.live.gles.GlUtil
import com.hezb.lib.live.gles.Texture2DProgram
import java.nio.FloatBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    IconVideoFilter
 *
 * Description: 图标（水印）视频滤镜.
 *
 * @author  hezhubo
 * @date    2022年07月24日 23:26
 */
class IconVideoFilter(private val icon: Bitmap, private val iconRectF: RectF) : BaseVideoFilter() {

    private var textureId: Int = GlUtil.NO_TEXTURE
    private var framebuffer: Int = 0

    private val vsProgram = Texture2DProgram.VERTEX_SHADER
    private val fsProgram = "" +
            "precision mediump float;\n" +
            "varying mediump vec2 vTextureCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform sampler2D uImageTexture;\n" +
            "uniform vec4 imageRect;\n" +
            "void main() {\n" +
            "   lowp vec4 c1 = texture2D(uTexture, vTextureCoord);\n" +
            "   lowp vec2 vTextureCoord2 = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);\n" +
            "   if (vTextureCoord2.x > imageRect.r && vTextureCoord2.x < imageRect.b && vTextureCoord2.y > imageRect.g && vTextureCoord2.y < imageRect.a) {\n" +
            "       vec2 imageXY = vec2((vTextureCoord2.x - imageRect.r) / (imageRect.b - imageRect.r), (vTextureCoord2.y - imageRect.g) / (imageRect.a - imageRect.g));\n" +
            "       lowp vec4 c2 = texture2D(uImageTexture, imageXY);\n" +
            "       lowp vec4 outputColor = c2 + c1 * c1.a * (1.0 - c2.a);\n" +
            "       outputColor.a = 1.0;\n" +
            "       gl_FragColor = outputColor;\n" +
            "   } else {\n" +
            "       gl_FragColor = c1;\n" +
            "   }\n" +
            "}"

    private var glProgram: Int = 0
    private var glPositionLoc: Int = 0
    private var glTextureCoordLoc: Int = 0
    private var glTextureLoc: Int = 0
    private var glImageTextureLoc: Int = 0
    private var glImageRectLoc: Int = 0

    private var imageTextureId: Int = GlUtil.NO_TEXTURE

    override fun init(width: Int, height: Int) {
        super.init(width, height)
        if (width == 0 || height == 0) {
            return
        }
        textureId = GlUtil.createImageTexture(width, height)
        if (textureId == GlUtil.NO_TEXTURE) {
            return
        }
        framebuffer = GlUtil.createFramebufferLinkTexture2D(textureId)

        glProgram = GlUtil.createProgram(vsProgram, fsProgram)
        if (glProgram == 0) {
            return
        }
        GLES20.glUseProgram(glProgram)
        glPositionLoc = GLES20.glGetAttribLocation(glProgram, "aPosition")
        glTextureCoordLoc = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
        glTextureLoc = GLES20.glGetUniformLocation(glProgram, "uTexture")
        glImageTextureLoc = GLES20.glGetUniformLocation(glProgram, "uImageTexture")
        glImageRectLoc = GLES20.glGetUniformLocation(glProgram, "imageRect")
    }

    override fun onDraw(
        sourceTexture: Int,
        shapeVerticesBuffer: FloatBuffer,
        textureVerticesBuffer: FloatBuffer
    ): Int {
        imageTextureId = GlUtil.loadTexture(icon, imageTextureId)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glUseProgram(glProgram)
        GLES20.glUniform4f(
            glImageRectLoc,
            iconRectF.left,
            iconRectF.top,
            iconRectF.right,
            iconRectF.bottom
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture)
        GLES20.glUniform1i(glTextureLoc, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
        GLES20.glUniform1i(glImageTextureLoc, 1)
        GLES20.glEnableVertexAttribArray(glPositionLoc)
        GLES20.glEnableVertexAttribArray(glTextureCoordLoc)

        shapeVerticesBuffer.position(0)
        GLES20.glVertexAttribPointer(
            glPositionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            shapeVerticesBuffer
        )
        textureVerticesBuffer.position(0)
        GLES20.glVertexAttribPointer(
            glTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureVerticesBuffer
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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return textureId
    }

    override fun onDestroy() {
        super.onDestroy()
        if (glProgram != 0) {
            GLES20.glDeleteProgram(glProgram)
        }
        glProgram = 0
        if (imageTextureId != GlUtil.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, intArrayOf(imageTextureId), 0)
        }
        imageTextureId = GlUtil.NO_TEXTURE
        if (textureId != GlUtil.NO_TEXTURE) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        textureId = GlUtil.NO_TEXTURE
    }

    companion object {

        /**
         * 计算图标绘制矩形范围(比例值)
         *
         * @param rect         原设计稿屏幕位置
         * @param screenWidth  设计稿屏幕宽
         * @param screenHeight 设计稿屏幕高
         * @param videoWidth   视频输出宽
         * @param videoHeight  视频输出高
         */
        fun getFitCenterRectF(
            rect: Rect,
            screenWidth: Int,
            screenHeight: Int,
            videoWidth: Int,
            videoHeight: Int
        ): RectF {
            val rectF = RectF()
            val targetWidth: Int
            val targetHeight: Int
            val scale: Float // 缩放比
            val screenRatio = screenWidth / screenHeight.toFloat()
            val videoRatio = videoWidth / videoHeight.toFloat()
            if (screenRatio > videoRatio) {
                targetWidth = videoWidth
                targetHeight = (targetWidth / screenRatio).toInt()
                scale = screenWidth / targetWidth.toFloat()

                val space = (videoHeight - targetHeight) / 2
                rectF.left = rect.left / scale / videoWidth
                rectF.right = rect.right / scale / videoWidth
                rectF.top = (rect.top / scale + space) / videoHeight
                rectF.bottom = (rect.bottom / scale + space) / videoHeight

            } else {
                targetHeight = videoHeight
                targetWidth = (targetHeight * screenRatio).toInt()
                scale = screenHeight / targetHeight.toFloat()

                val space = (videoWidth - targetWidth) / 2
                rectF.left = (rect.left / scale + space) / videoWidth
                rectF.right = (rect.right / scale + space) / videoWidth
                rectF.top = rect.top / scale / videoHeight
                rectF.bottom = rect.bottom / scale / videoHeight
            }
            return rectF
        }

    }

}