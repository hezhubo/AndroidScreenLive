package com.hezb.live.recorder.rtmp.packet

import android.media.MediaFormat
import com.hezb.live.recorder.rtmp.packet.pool.LruArrayPool
import java.nio.ByteBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    FlvHelper
 *
 * Description: flv辅助类.
 *
 * @author  hezhubo
 * @date    2022年07月21日 15:14
 */
object FlvHelper {

    const val FLV_TAG_LENGTH = 11
    const val FLV_VIDEO_TAG_LENGTH = 5
    const val FLV_AUDIO_TAG_LENGTH = 2
    const val FLV_TAG_FOOTER_LENGTH = 4
    const val NALU_HEADER_LENGTH = 4

    private fun intToByteArrayFull(dst: ByteArray, pos: Int, integer: Int) {
        dst[pos] = (integer shr 24 and 0xFF).toByte()
        dst[pos + 1] = (integer shr 16 and 0xFF).toByte()
        dst[pos + 2] = (integer shr 8 and 0xFF).toByte()
        dst[pos + 3] = (integer and 0xFF).toByte()
    }

    private fun intToByteArrayTwoByte(dst: ByteArray, pos: Int, integer: Int) {
        dst[pos] = (integer shr 8 and 0xFF).toByte()
        dst[pos + 1] = (integer and 0xFF).toByte()
    }

    /**
     * flv data add video tag
     *
     * @param dst
     * @param pos
     * @param isAVCSequenceHeader
     * @param isIDR
     * @param readDataLength
     */
    private fun fillFlvVideoTag(
        dst: ByteArray,
        pos: Int,
        isAVCSequenceHeader: Boolean,
        isIDR: Boolean,
        readDataLength: Int
    ) {
        // FrameType&CodecID
        dst[pos] = if (isIDR) 0x17.toByte() else 0x27.toByte()
        // AVCPacketType
        dst[pos + 1] = if (isAVCSequenceHeader) 0x00.toByte() else 0x01.toByte()
        // LAKETODO CompositionTime
        dst[pos + 2] = 0x00
        dst[pos + 3] = 0x00
        dst[pos + 4] = 0x00
        if (!isAVCSequenceHeader) {
            // NALU HEADER
            intToByteArrayFull(dst, pos + 5, readDataLength)
        }
    }

    /**
     * flv data add audio tag
     *
     * @param dst
     * @param pos
     * @param isAACSequenceHeader
     */
    private fun fillFlvAudioTag(dst: ByteArray, pos: Int, isAACSequenceHeader: Boolean) {
        /**
         * UB[4] 10=AAC
         * UB[2] 3=44kHz
         * UB[1] 1=16-bit
         * UB[1] 0=MonoSound
         */
        dst[pos] = 0xAE.toByte()
        dst[pos + 1] = if (isAACSequenceHeader) 0x00.toByte() else 0x01.toByte()
    }

    /**
     * 构建avc解码配置
     *
     * @param mediaFormat
     * @return
     */
    private fun generateAVCDecoderConfiguration(mediaFormat: MediaFormat): ByteArray {
        val spsByteBuff = mediaFormat.getByteBuffer("csd-0")
        val ppsByteBuff = mediaFormat.getByteBuffer("csd-1")
        if (spsByteBuff == null || ppsByteBuff == null) {
            return ByteArray(11)
        }
        val spsByteBuffPosition = spsByteBuff.position()
        val ppsByteBuffPosition = ppsByteBuff.position()
        spsByteBuff.position(4)
        ppsByteBuff.position(4)
        val spsLength = spsByteBuff.remaining()
        val ppsLength = ppsByteBuff.remaining()
        val length = 11 + spsLength + ppsLength
        val result = ByteArray(length)
        spsByteBuff.get(result, 8, spsLength)
        ppsByteBuff.get(result, 8 + spsLength + 3, ppsLength)
        // reset position
        spsByteBuff.position(spsByteBuffPosition)
        ppsByteBuff.position(ppsByteBuffPosition)
        /**
         * UB[8]configurationVersion
         * UB[8]AVCProfileIndication
         * UB[8]profile_compatibility
         * UB[8]AVCLevelIndication
         * UB[8]lengthSizeMinusOne
         */
        result[0] = 0x01
        result[1] = result[9]
        result[2] = result[10]
        result[3] = result[11]
        result[4] = 0xFF.toByte()
        /**
         * UB[8]numOfSequenceParameterSets
         * UB[16]sequenceParameterSetLength
         */
        result[5] = 0xE1.toByte()
        intToByteArrayTwoByte(result, 6, spsLength)
        /**
         * UB[8]numOfPictureParameterSets
         * UB[16]pictureParameterSetLength
         */
        val pos = 8 + spsLength
        result[pos] = 0x01.toByte()
        intToByteArrayTwoByte(result, pos + 1, ppsLength)
        return result
    }

    /**
     * 封装发送视频头包（配置包）
     *
     * @param lruArrayPool
     * @param mediaFormat
     * @return
     */
    fun getVideoConfigFlvData(lruArrayPool: LruArrayPool, mediaFormat: MediaFormat): FlvData {
        val byteArray = generateAVCDecoderConfiguration(mediaFormat)
        val packetLen = FLV_VIDEO_TAG_LENGTH + byteArray.size
        val flvByteArray = lruArrayPool.get(packetLen)
        fillFlvVideoTag(
            flvByteArray,
            0,
            isAVCSequenceHeader = true,
            isIDR = true,
            readDataLength = byteArray.size
        )
        System.arraycopy(
            byteArray, 0, flvByteArray, FLV_VIDEO_TAG_LENGTH,
            byteArray.size
        )
        val flvData = FlvData.obtain()
        flvData.droppable = false
        flvData.byteBuffer = flvByteArray
        flvData.size = packetLen
        flvData.dts = 0 // 配置默认时间戳从0开始
        flvData.flvTagType = FlvData.FLV_RTMP_PACKET_TYPE_VIDEO
        flvData.videoFrameType = FlvData.NALU_TYPE_IDR
        return flvData
    }

    /**
     * 封装发送视频数据包
     *
     * @param lruArrayPool
     * @param tms
     * @param realData
     * @return
     */
    fun getVideoFlvData(lruArrayPool: LruArrayPool, tms: Long, realData: ByteBuffer): FlvData {
        val realDataLength = realData.remaining()
        val packetLen = FLV_VIDEO_TAG_LENGTH + NALU_HEADER_LENGTH + realDataLength
        val flvByteArray = lruArrayPool.get(packetLen)
        realData.get(flvByteArray, FLV_VIDEO_TAG_LENGTH + NALU_HEADER_LENGTH, realDataLength)
        val frameType = flvByteArray[FLV_VIDEO_TAG_LENGTH + NALU_HEADER_LENGTH].toInt() and 0x1F
        val flvData = FlvData.obtain()
        flvData.videoFrameType = frameType
        fillFlvVideoTag(flvByteArray, 0, false, flvData.isKeyframe(), realDataLength)
        flvData.droppable = !flvData.isKeyframe()
        flvData.byteBuffer = flvByteArray
        flvData.size = packetLen
        flvData.dts = tms
        flvData.flvTagType = FlvData.FLV_RTMP_PACKET_TYPE_VIDEO
        return flvData
    }

    /**
     * 封装发送音频头包（配置包）
     *
     * @param lruArrayPool
     * @param mediaFormat
     * @return
     */
    fun getAudioConfigFlvData(lruArrayPool: LruArrayPool, mediaFormat: MediaFormat): FlvData? {
        val spsByteBuff = mediaFormat.getByteBuffer("csd-0") ?: return null
        val packetLen = FLV_AUDIO_TAG_LENGTH + spsByteBuff.remaining()
        val flvByteArray = lruArrayPool.get(packetLen)
        val spsByteBuffPosition = spsByteBuff.position()
        spsByteBuff.get(flvByteArray, FLV_AUDIO_TAG_LENGTH, spsByteBuff.remaining())
        // reset position
        spsByteBuff.position(spsByteBuffPosition)
        fillFlvAudioTag(flvByteArray, 0, true)
        val flvData = FlvData.obtain()
        flvData.droppable = false
        flvData.byteBuffer = flvByteArray
        flvData.size = packetLen
        flvData.dts = 0 // 配置默认时间戳从0开始
        flvData.flvTagType = FlvData.FLV_RTMP_PACKET_TYPE_AUDIO
        return flvData
    }

    /**
     * 封装发送音频数据包
     *
     * @param lruArrayPool
     * @param tms
     * @param realData
     * @return
     */
    fun getAudioFlvData(lruArrayPool: LruArrayPool, tms: Long, realData: ByteBuffer): FlvData {
        val packetLen: Int = FLV_AUDIO_TAG_LENGTH + realData.remaining()
        val flvByteArray = lruArrayPool.get(packetLen)
        realData.get(flvByteArray, FLV_AUDIO_TAG_LENGTH, realData.remaining())
        fillFlvAudioTag(flvByteArray, 0, false)
        val flvData = FlvData.obtain()
        flvData.droppable = true
        flvData.byteBuffer = flvByteArray
        flvData.size = packetLen
        flvData.dts = tms
        flvData.flvTagType = FlvData.FLV_RTMP_PACKET_TYPE_AUDIO
        return flvData
    }

}