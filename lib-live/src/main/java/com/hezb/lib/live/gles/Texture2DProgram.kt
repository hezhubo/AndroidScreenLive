package com.hezb.lib.live.gles

import android.opengl.GLES20

/**
 * Project Name: AndroidScreenLive
 * File Name:    Texture2dProgram
 *
 * Description: 2D纹理程序.
 *
 * @author  hezhubo
 * @date    2022年07月19日 21:16
 */
class Texture2DProgram(vertexShaderCode: String = VERTEX_SHADER, fragmentShaderCode: String) {

    companion object {
        const val VERTEX_SHADER = "" +
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main(){\n" +
                "    gl_Position   = aPosition;\n" +
                "    vTextureCoord = aTextureCoord;\n" +
                "}"

        const val FRAGMENT_SHADER_2D = "" +
                "precision highp float;\n" +
                "varying highp vec2 vTextureCoord;\n" +
                "uniform sampler2D uTexture;\n" +
                "void main(){\n" +
                "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
                "}"
        const val FRAGMENT_SHADER_SOURCE2D = "" +
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision highp float;\n" +
                "varying highp vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "void main(){\n" +
                "    gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
                "}"
    }

    var program: Int = 0
        private set

    var aPositionLoc: Int = 0
        private set

    var aTextureCoordLoc: Int = 0
        private set

    var uTextureLoc: Int = 0
        private set

    init {
        program = GlUtil.createProgram(vertexShaderCode, fragmentShaderCode)
        if (program == 0) {
            throw RuntimeException("unable to create program!")
        }
        GLES20.glUseProgram(program)
        // 获取顶点属性变量
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        // 获取统一变量的位置
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
    }

}