package com.hezb.lib.live.model

/**
 * Project Name: AndroidScreenLive
 * File Name:    AudioBuff
 *
 * Description: 音频数据缓存.
 *
 * @author  hezhubo
 * @date    2022年07月12日 22:22
 */
class AudioBuffer(var byteArray: ByteArray) {

    /** 有效数据长度 */
    var size: Int = 0

    /** 是否为可被使用的缓存，默认为被占用状态 */
    var free: Boolean = false

}