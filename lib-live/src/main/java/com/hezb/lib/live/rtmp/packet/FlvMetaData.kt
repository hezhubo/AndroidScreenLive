package com.hezb.lib.live.rtmp.packet

import com.hezb.lib.live.config.RecorderConfig
import java.util.ArrayList

/**
 * Project Name: AndroidScreenLive
 * File Name:    FlvMetaData
 *
 * Description: flv metadata. TODO 待优化包封装
 *
 * @author  hezhubo
 * @date    2022年07月21日 14:29
 */
class FlvMetaData(config: RecorderConfig) {

    private val NAME = "onMetaData"
    private val objEndMarker = byteArrayOf(0x00, 0x00, 0x09)
    private val emptySize = 21
    private var metaData: ArrayList<ByteArray> = ArrayList()
    private var dataSize = 0
    private var pointer = 0
    private var metaDataFrame: ByteArray? = null

    init {
        // Audio AAC
        setProperty("audiocodecid", 10)
        when (config.audioBitrate) {
            32 * 1024 -> setProperty("audiodatarate", 32)
            48 * 1024 -> setProperty("audiodatarate", 48)
            64 * 1024 -> setProperty("audiodatarate", 64)
            else -> {
                setProperty("audiodatarate", config.audioBitrate / 1024)
            }
        }
        when (config.audioSampleRate) {
            44100 -> setProperty("audiosamplerate", 44100)
            else -> {
                setProperty("audiosamplerate", config.audioSampleRate)
            }
        }
        // Video h264
        setProperty("videocodecid", 7)
        setProperty("framerate", config.videoFrameRate)
        setProperty("width", config.videoSize.width)
        setProperty("height", config.videoSize.height)
    }

    fun setProperty(key: String, value: Int) {
        addProperty(toFlvString(key), 0.toByte(), toFlvNum(value.toDouble()))
    }

    fun setProperty(key: String, value: String) {
        addProperty(toFlvString(key), 2.toByte(), toFlvString(value))
    }

    private fun addProperty(key: ByteArray, dataType: Byte, data: ByteArray) {
        val propertySize = key.size + 1 + data.size
        val property = ByteArray(propertySize)
        System.arraycopy(key, 0, property, 0, key.size)
        property[key.size] = dataType
        System.arraycopy(data, 0, property, key.size + 1, data.size)
        metaData.add(property)
        dataSize += propertySize
    }

    fun getMetaData(): ByteArray {
        return ByteArray(dataSize + emptySize).also {
            metaDataFrame = it
            pointer = 0
            // SCRIPTDATA.name
            addByte(2)
            addByteArray(toFlvString(NAME))
            // SCRIPTDATA.value ECMA array
            addByte(8)
            addByteArray(toUI(metaData.size.toLong(), 4))
            for (property in metaData) {
                addByteArray(property)
            }
            addByteArray(objEndMarker)
        }
    }

    private fun addByte(value: Int) {
        metaDataFrame?.let {
            it[pointer] = value.toByte()
            pointer++
        }
    }

    private fun addByteArray(value: ByteArray) {
        metaDataFrame?.let {
            System.arraycopy(value, 0, it, pointer, value.size)
            pointer += value.size
        }
    }

    private fun toFlvString(text: String): ByteArray {
        val flvString = ByteArray(text.length + 2)
        System.arraycopy(toUI(text.length.toLong(), 2), 0, flvString, 0, 2)
        System.arraycopy(text.toByteArray(), 0, flvString, 2, text.length)
        return flvString
    }

    private fun toUI(value: Long, bytes: Int): ByteArray {
        val ui = ByteArray(bytes)
        for (i in 0 until bytes) {
            ui[bytes - 1 - i] = (value shr (8 * i) and 0xff).toByte()
        }
        return ui
    }

    private fun toFlvNum(value: Double): ByteArray {
        val tmp = java.lang.Double.doubleToLongBits(value)
        return toUI(tmp, 8)
    }

}