package com.hezb.live.recorder.filter.audio

/**
 * Project Name: AndroidScreenLive
 * File Name:    VolumeAudioFilter
 *
 * Description: 音量大小滤镜.
 *
 * @author  hezhubo
 * @date    2022年07月24日 23:14
 */
class VolumeAudioFilter : BaseAudioFilter() {

    private var volumeScale: Float = 1f

    /**
     * @param scale 0.0~1.0
     */
    fun setVolumeScale(scale: Float) {
        volumeScale = scale
    }

    override fun onFilter(originBuffer: ByteArray, originBufferSize: Int, targetBuffer: ByteArray) : Int {
        if (originBufferSize < 2) {
            return 0
        }
        var origin: Short
        var i = 0
        while (i < originBufferSize) {
            origin = (originBuffer[i + 1].toInt() shl 8 or (originBuffer[i].toInt() and 0xff)).toShort()
            origin = (origin * volumeScale).toInt().toShort()
            originBuffer[i + 1] = (origin.toInt() shr 8).toByte()
            originBuffer[i] = origin.toByte()
            i += 2
        }
        return originBufferSize
    }
}