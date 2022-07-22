package com.hezb.live.recorder.rtmp.packet

/**
 * Project Name: AndroidScreenLive
 * File Name:    FlvData
 *
 * Description: flv data.
 *
 * @author  hezhubo
 * @date    2022年07月21日 15:14
 */
class FlvData private constructor() {

    companion object {
        /** 视频包 */
        const val FLV_RTMP_PACKET_TYPE_VIDEO = 9

        /** 音频包 */
        const val FLV_RTMP_PACKET_TYPE_AUDIO = 8

        /** 信息包 */
        const val FLV_RTMP_PACKET_TYPE_INFO = 18

        /** 关键帧 */
        const val NALU_TYPE_IDR = 5

        private val sPoolSync = Any()
        private var sPool: FlvData? = null
        private var sPoolSize = 0

        private const val MAX_POOL_SIZE = 30

        fun obtain(): FlvData {
            synchronized(sPoolSync) {
                sPool?.let {
                    val flvData = it
                    sPool = flvData.next
                    flvData.next = null
                    sPoolSize--
                    return flvData
                }
            }
            return FlvData()
        }
    }

    private fun reset() {
        droppable = false
        dts = 0L
        byteBuffer = null
        size = 0
        flvTagType = 0
        videoFrameType = 0

    }

    fun recycle() {
        reset()
        synchronized(sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                next = sPool
                sPool = this
                sPoolSize++
            }
        }
    }

    var next: FlvData? = null

    /** 能否丢弃 */
    var droppable = false

    /** 解码时间戳 */
    var dts = 0L

    /** 数据 */
    var byteBuffer: ByteArray? = null

    /** 字节长度 */
    var size = 0

    /** 视频和音频的分类 */
    var flvTagType = 0

    /** 视频帧类型 */
    var videoFrameType = 0

    /**
     * @return 是否为关键帧
     */
    fun isKeyframe(): Boolean {
        return videoFrameType == NALU_TYPE_IDR
    }

}