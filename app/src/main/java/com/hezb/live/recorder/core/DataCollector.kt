package com.hezb.live.recorder.core

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    DataCollector
 *
 * Description: 编码数据收集器.
 *
 * @author  hezhubo
 * @date    2022年07月16日 21:50
 */
interface DataCollector {

    /**
     * 添加音/视频轨道
     *
     * @param format
     * @param isVideo
     */
    fun addTrack(format: MediaFormat, isVideo: Boolean)

    /**
     * 写入音/视频编码数据
     *
     * @param byteBuffer
     * @param bufferInfo
     * @param isVideo
     */
    fun writeData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean)

}