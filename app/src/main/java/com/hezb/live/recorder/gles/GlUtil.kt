package com.hezb.live.recorder.gles

import android.opengl.*
import com.hezb.live.recorder.util.LogUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    GlUtil
 *
 * Description: OpenGL工具类.
 *
 * @author  hezhubo
 * @date    2022年07月19日 21:15
 */
object GlUtil {

    private const val SHORT_SIZE_BYTES = 2
    private const val FLOAT_SIZE_BYTES = 4

    private val drawIndices = shortArrayOf(0, 1, 2, 0, 2, 3)

    private val squareVertices = floatArrayOf(
        -1.0f, 1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f
    )

    private val screenTextureVertices = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    fun getDrawIndicesBuffer(): ShortBuffer {
        val result = ByteBuffer.allocateDirect(SHORT_SIZE_BYTES * drawIndices.size)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        result.put(drawIndices)
        result.position(0)
        return result
    }

    fun getShapeVerticesBuffer(): FloatBuffer {
        val result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * squareVertices.size)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        result.put(squareVertices)
        result.position(0)
        return result
    }

    fun getScreenTextureVerticesBuffer(): FloatBuffer {
        val result = ByteBuffer.allocateDirect(FLOAT_SIZE_BYTES * screenTextureVertices.size)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        result.put(screenTextureVertices)
        result.position(0)
        return result
    }

    /**
     * 创建一个指定大小的空纹理对象
     *
     * @param width
     * @param height
     * @param format
     * @return 纹理id
     */
    fun createImageTexture(width: Int, height: Int, format: Int = GLES20.GL_RGBA): Int {
        // 创建一个纹理对象
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        // 绑定纹理对象
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        // 加载2D纹理（此处为空纹理）
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            format,
            width,
            height,
            0,
            format,
            GLES20.GL_UNSIGNED_BYTE,
            null // 包含图像的实际像素数据
        )
        // 设置纹理过滤模式
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )

        // 取消绑定
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textures[0]
    }

    /**
     * 创建一个帧缓冲区对象，并连接到一个2D纹理
     *
     * @param textureId
     * @return 帧缓冲区id
     */
    fun createFramebufferLinkTexture2D(textureId: Int): Int {
        // 分配一个帧缓冲区对象（FOB）
        val framebuffer = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffer, 0)
        // 设置当前帧缓冲区对象
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
        // 连接一个2D纹理作为帧缓冲区附着
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0
        )

        // 取消绑定
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return framebuffer[0]
    }

    /**
     * 创建OpenGL着色器程序
     *
     * @param vertexShaderCode 顶点着色器代码
     * @param fragmentShaderCode 片段着色器代码
     * @return 程序(指针)，返回0则发生错误
     */
    fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(vertexShader, vertexShaderCode)
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode)
        val status = IntArray(1)
        GLES20.glCompileShader(vertexShader)
        GLES20.glGetShaderiv(vertexShader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (GLES20.GL_FALSE == status[0]) {
            LogUtil.e(msg = "vertex shader compile, failed : ${GLES20.glGetShaderInfoLog(vertexShader)}")
            return 0
        }
        GLES20.glCompileShader(fragmentShader)
        GLES20.glGetShaderiv(fragmentShader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (GLES20.GL_FALSE == status[0]) {
            LogUtil.e(msg = "fragment shader compile, failed : ${GLES20.glGetShaderInfoLog(fragmentShader)}")
            return 0
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (GLES20.GL_FALSE == status[0]) {
            LogUtil.e(msg = "link program, failed : ${GLES20.glGetProgramInfoLog(program)}")
            return 0
        }
        return program
    }

    /**
     * 绘制帧缓冲区附着的2D纹理
     *
     * @param textureTarget
     * @param textureId
     * @param framebufferId
     * @param width
     * @param height
     * @param texture2DProgram
     * @param shapeBuffer
     * @param textureBuffer
     * @param drawIndicesBuffer
     */
    fun draw2DFramebuffer(
        textureTarget: Int,
        textureId: Int,
        framebufferId: Int,
        width: Int,
        height: Int,
        texture2DProgram: Texture2DProgram,
        shapeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer,
        drawIndicesBuffer: ShortBuffer
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glUseProgram(texture2DProgram.program)
        // 激活纹理单元
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(texture2DProgram.uTextureLoc, 0)
        GLES20.glEnableVertexAttribArray(texture2DProgram.aPositionLoc)
        GLES20.glEnableVertexAttribArray(texture2DProgram.aTextureCoordLoc)
        // 指定顶点数组
        GLES20.glVertexAttribPointer(
            texture2DProgram.aPositionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            2 * 4,
            shapeBuffer
        )
        GLES20.glVertexAttribPointer(
            texture2DProgram.aTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            2 * 4,
            textureBuffer
        )
        // 指定视口尺寸
        GLES20.glViewport(0, 0, width, height)
        // 清除颜色缓冲区
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 从数组中获得数据渲染图元
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            drawIndicesBuffer.limit(),
            GLES20.GL_UNSIGNED_SHORT,
            drawIndicesBuffer
        )
        // 向图形硬件提交缓冲区里的指令
        GLES20.glFinish()
        // 解绑一系列
        GLES20.glDisableVertexAttribArray(texture2DProgram.aPositionLoc)
        GLES20.glDisableVertexAttribArray(texture2DProgram.aTextureCoordLoc)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glUseProgram(0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * 绘制2D纹理
     *
     * @param textureId
     * @param texture2DProgram
     * @param shapeBuffer
     * @param textureBuffer
     * @param drawIndicesBuffer
     */
    fun drawTexture2D(
        textureId: Int,
        texture2DProgram: Texture2DProgram,
        shapeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer,
        drawIndicesBuffer: ShortBuffer
    ) {
        GLES20.glUseProgram(texture2DProgram.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(texture2DProgram.uTextureLoc, 0)
        GLES20.glEnableVertexAttribArray(texture2DProgram.aPositionLoc)
        GLES20.glEnableVertexAttribArray(texture2DProgram.aTextureCoordLoc)
        // 指定顶点数组
        GLES20.glVertexAttribPointer(
            texture2DProgram.aPositionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            2 * 4,
            shapeBuffer
        )
        GLES20.glVertexAttribPointer(
            texture2DProgram.aTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            2 * 4,
            textureBuffer
        )
        // 清除颜色缓冲区
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 从数组中获得数据渲染图元
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            drawIndicesBuffer.limit(),
            GLES20.GL_UNSIGNED_SHORT,
            drawIndicesBuffer
        )

        GLES20.glFinish()
        // 解绑一系列
        GLES20.glDisableVertexAttribArray(texture2DProgram.aPositionLoc)
        GLES20.glDisableVertexAttribArray(texture2DProgram.aTextureCoordLoc)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

}