package com.hezb.live.recorder.filter.audio

/**
 * Project Name: AndroidScreenLive
 * File Name:    BaseAudioFilter
 *
 * Description: 音频(PCM数据)滤镜基类.
 *
 * @author  hezhubo
 * @date    2022年07月16日 21:03
 */
abstract class BaseAudioFilter {

    open fun init(sampleRate: Int, channels: Int) {}

    /**
     * 实现滤镜
     *
     * @param originBuffer     原始数据
     * @param originBufferSize 原始数据长度
     * @param targetBuffer     目标数据
     * @return 目标数据长度
     */
    abstract fun onFilter(originBuffer: ByteArray, originBufferSize: Int, targetBuffer: ByteArray): Int

    open fun onDestroy() {}

}