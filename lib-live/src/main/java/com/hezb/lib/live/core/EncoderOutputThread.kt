package com.hezb.lib.live.core

import android.media.MediaCodec
import com.hezb.lib.live.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    EncoderOutputThread
 *
 * Description: 编码输出线程.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:04
 */
class EncoderOutputThread(
    private val encoder: MediaCodec,
    private val collector: EncodeDataCollector,
    private val isVideo: Boolean
) : Thread("EncoderOutputThread") {

    private val TIMEOUT = if (isVideo) 5000L else 3000L
    private var isRunning = true

    fun quit() {
        isRunning = false
        try {
            join()
        } catch (e: InterruptedException) {
            LogUtil.e(msg = "encoder output thread join error!", tr = e)
        }
    }

    override fun run() {
        val outputBufferInfo = MediaCodec.BufferInfo()
        while (isRunning) {
            val eobIndex = try {
                encoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT)
            } catch (e: Exception) {
                LogUtil.e(msg = "dequeue video($isVideo) output buffer error!", tr = e)
                if (isRunning) {
                    collector.onError(e)
                }
                quit() // 出错直接停止运行线程
                break
            }
            if (!isRunning) {
                break
            }
            when (eobIndex) {
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    collector.addTrack(encoder.outputFormat, isVideo)
                }
                else -> {
                    if (outputBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && outputBufferInfo.size != 0) {
                        try {
                            val encodedData = encoder.getOutputBuffer(eobIndex) ?: return
                            var position = outputBufferInfo.offset
                            if (isVideo) {
                                position += 4 // H264 NALU: 00 00 00 01(4字节) 起始码(4字节)；后一个字节为NALU type
                            }
                            encodedData.position(position)
                            encodedData.limit(outputBufferInfo.offset + outputBufferInfo.size)
                            collector.writeData(encodedData, outputBufferInfo, isVideo)
                        } catch (e : Exception) {
                            LogUtil.e(msg = "video($isVideo) write data error!", tr = e)
                        }
                    }
                    encoder.releaseOutputBuffer(eobIndex, false)

                    if (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isRunning = false
                    }
                }
            }
        }
    }

}