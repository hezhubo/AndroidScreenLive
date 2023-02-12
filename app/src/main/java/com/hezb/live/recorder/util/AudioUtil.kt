package com.hezb.live.recorder.util

/**
 * Project Name: AndroidScreenLive
 * File Name:    AudioUtil
 *
 * Description: 音频工具类.
 *
 * @author  hezhubo
 * @date    2023年02月13日 01:02
 */
object AudioUtil {

    /**
     * 16位PCM byte转short
     *
     * @param buffer
     * @param lowIndex
     * @return short
     */
    fun convertPcm16BitByteToShort(buffer: ByteArray, lowIndex: Int): Short {
        val low = buffer[lowIndex]      // 低8位
        val high = buffer[lowIndex + 1] // 高8位
        return ((high.toInt() shl 8) or (low.toInt() and 0xff)).toShort()
    }

    /**
     * 16位PCM short转byte
     *
     * @param short
     * @param buffer
     * @param lowIndex
     */
    fun convertPcm16BitShortToByte(short: Short, buffer: ByteArray, lowIndex: Int) {
        buffer[lowIndex] = short.toByte() // 低8位
        buffer[lowIndex + 1] = (short.toInt() shr 8).toByte() // 高8位
    }

}