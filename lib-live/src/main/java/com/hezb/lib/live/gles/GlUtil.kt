package com.hezb.lib.live.gles

import android.graphics.Bitmap
import android.opengl.*
import com.hezb.lib.live.util.LogUtil
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

    /** 无效纹理id */
    const val NO_TEXTURE = -1

    private const val SHORT_SIZE_BYTES = 2 // short占2字节
    private const val FLOAT_SIZE_BYTES = 4 // float占4字节

    /** 绘制的(两个)三角形顶点序号标记 */
    private val drawIndices = shortArrayOf(0, 1, 2, 0, 2, 3)

    /** 四边形(共用一边的两个三角形)顶点坐标 (gl_Position) */
    private val squareVertices = floatArrayOf(
        -1.0f, 1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f
    )

    /** 屏幕纹理顶点坐标 (vTextureCoord) */
    private val screenTextureVertices = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    /** 绘制输出纹理顶点坐标 */
    private val targetTextureVertices = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    )

    fun getDrawIndicesBuffer(): ShortBuffer {
        // 创建内存块缓冲区
        val buffer = ByteBuffer
            .allocateDirect(SHORT_SIZE_BYTES * drawIndices.size)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        buffer.put(drawIndices)
        buffer.position(0)
        return buffer
    }

    fun getShapeVerticesBuffer(): FloatBuffer {
        val buffer = ByteBuffer
            .allocateDirect(FLOAT_SIZE_BYTES * squareVertices.size)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(squareVertices)
        buffer.position(0)
        return buffer
    }

    fun getScreenTextureVerticesBuffer(): FloatBuffer {
        val buffer = ByteBuffer
            .allocateDirect(FLOAT_SIZE_BYTES * screenTextureVertices.size)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(screenTextureVertices)
        buffer.position(0)
        return buffer
    }

    fun getTargetTextureVerticesBuffer(): FloatBuffer {
        val buffer = ByteBuffer
            .allocateDirect(FLOAT_SIZE_BYTES * targetTextureVertices.size)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(targetTextureVertices)
        buffer.position(0)
        return buffer
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
     * @param shapeVerticesBuffer
     * @param textureVerticesBuffer
     * @param drawIndicesBuffer
     */
    fun draw2DFramebuffer(
        textureTarget: Int,
        textureId: Int,
        framebufferId: Int,
        width: Int,
        height: Int,
        texture2DProgram: Texture2DProgram,
        shapeVerticesBuffer: FloatBuffer,
        textureVerticesBuffer: FloatBuffer,
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
            0,
            shapeVerticesBuffer
        )
        GLES20.glVertexAttribPointer(
            texture2DProgram.aTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureVerticesBuffer
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
     * @param shapeVerticesBuffer
     * @param textureVerticesBuffer
     * @param drawIndicesBuffer
     */
    fun drawTexture2D(
        textureId: Int,
        texture2DProgram: Texture2DProgram,
        shapeVerticesBuffer: FloatBuffer,
        textureVerticesBuffer: FloatBuffer,
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
            0,
            shapeVerticesBuffer
        )
        GLES20.glVertexAttribPointer(
            texture2DProgram.aTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureVerticesBuffer
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

    /**
     * bitmap转2D纹理
     *
     * @param image
     * @param reUseTexture
     * @return 纹理id
     */
    fun loadTexture(image: Bitmap, reUseTexture: Int): Int {
        val textures = IntArray(1)
        if (reUseTexture == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
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
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, image, 0)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reUseTexture)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, image)
            textures[0] = reUseTexture
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

}