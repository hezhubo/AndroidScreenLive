package com.hezb.live.recorder.model

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

}