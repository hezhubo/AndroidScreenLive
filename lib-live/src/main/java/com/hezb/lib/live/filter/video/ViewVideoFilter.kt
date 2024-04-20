package com.hezb.lib.live.filter.video

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import android.widget.FrameLayout
import com.hezb.lib.live.gles.GlUtil
import com.hezb.lib.live.gles.Texture2DProgram
import com.hezb.lib.live.model.Size
import java.nio.FloatBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    ViewVideoFilter
 *
 * Description: View视频滤镜.
 *
 * @author  hezhubo
 * @date    2022年08月22日 14:46
 */
class ViewVideoFilter(
    private val fakeView: FakeView,
    private val viewWidth: Int,
    private val viewHeight: Int
) : BaseVideoFilter() {

    private var textureId: Int = GlUtil.NO_TEXTURE
    private var framebuffer: Int = 0

    private val vsProgram = Texture2DProgram.VERTEX_SHADER
    private val fsProgram = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying mediump vec2 vTextureCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform samplerExternalOES uViewTexture;\n" +
            "void main() {\n" +
            "   vec4 c2 = texture2D(uViewTexture, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y));\n" +
            "   vec4 c1 = texture2D(uTexture, vTextureCoord);\n" +
            "   lowp vec4 outputColor = c2 + c1 * c1.a * (1.0 - c2.a);\n" +
            "   gl_FragColor = outputColor;\n" +
            "}"

    private var glProgram: Int = 0
    private var glPositionLoc: Int = 0
    private var glTextureCoordLoc: Int = 0
    private var glTextureLoc: Int = 0
    private var glViewTextureLoc: Int = 0

    private var viewTextureId: Int = GlUtil.NO_TEXTURE
    private var viewSurfaceTexture: SurfaceTexture? = null
    private var viewSurface: Surface? = null

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
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        viewTextureId = textures[0]
        if (viewTextureId == GlUtil.NO_TEXTURE) {
            return
        }
        fakeView.init(viewWidth, viewHeight)
        viewSurfaceTexture = SurfaceTexture(viewTextureId).also {
            it.setDefaultBufferSize(fakeView.viewWidth, fakeView.viewHeight)
            viewSurface = Surface(it)
        }
        GLES20.glUseProgram(glProgram)
        glPositionLoc = GLES20.glGetAttribLocation(glProgram, "aPosition")
        glTextureCoordLoc = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
        glTextureLoc = GLES20.glGetUniformLocation(glProgram, "uTexture")
        glViewTextureLoc = GLES20.glGetUniformLocation(glProgram, "uViewTexture")
        fakeView.startAnim()
    }

    override fun onDraw(
        sourceTexture: Int,
        shapeVerticesBuffer: FloatBuffer,
        textureVerticesBuffer: FloatBuffer
    ): Int {
        viewSurface?.let {
            try {
                val canvas = it.lockCanvas(null)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                fakeView.draw(canvas)
                it.unlockCanvasAndPost(canvas)
                viewSurfaceTexture?.updateTexImage()
            } catch (e: Exception) {}
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glUseProgram(glProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture)
        GLES20.glUniform1i(glTextureLoc, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewTextureId)
        GLES20.glUniform1i(glViewTextureLoc, 1)
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
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
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
        if (viewTextureId != GlUtil.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, intArrayOf(viewTextureId), 0)
        }
        viewTextureId = GlUtil.NO_TEXTURE
        fakeView.stopAnim()
        viewSurface?.release()
        viewSurfaceTexture?.release()
        if (textureId != GlUtil.NO_TEXTURE) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        textureId = GlUtil.NO_TEXTURE
    }

    abstract class FakeView(context: Context) : FrameLayout(context) {
        var viewWidth = 0
            protected set
        var viewHeight = 0
            protected set

        fun init(width: Int, height: Int) {
            viewWidth = width
            viewHeight = height
            addView()
            update()
        }

        abstract fun addView()

        private fun update() {
            measure(viewWidth, viewHeight)
            layout(0, 0, viewWidth, viewHeight)
        }

        open fun startAnim() {}

        open fun stopAnim() {}
    }

    companion object {

        /**
         * 以屏幕尺寸为主，按输出比例计算view实际的宽高，防止拉伸变形
         *
         * @param screenWidth
         * @param screenHeight
         * @param videoWidth
         * @param videoHeight
         */
        fun getCenterScaleViewSize(
            screenWidth: Int,
            screenHeight: Int,
            videoWidth: Int,
            videoHeight: Int
        ): Size {
            val targetWidth: Int
            val targetHeight: Int
            val screenRatio = screenWidth / screenHeight.toFloat()
            val videoRatio = videoWidth / videoHeight.toFloat()
            if (screenRatio > videoRatio) {
                targetWidth = screenWidth
                targetHeight = (targetWidth / videoRatio).toInt()
            } else {
                targetHeight = screenHeight
                targetWidth = (targetHeight * videoRatio).toInt()
            }
            return Size(targetWidth, targetHeight)
        }
    }

}