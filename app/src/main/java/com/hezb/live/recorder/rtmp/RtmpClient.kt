package com.hezb.live.recorder.rtmp

import androidx.annotation.Keep
import com.hezb.live.recorder.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    RtmpClient
 *
 * Description: rtmp客户端.
 *
 * @author  hezhubo
 * @date    2022年07月21日 13:00
 */
@Keep
object RtmpClient {

    init {
        try {
            System.loadLibrary("rtmp")
        } catch (t: Throwable) {
            LogUtil.e(msg = "load rtmp so error!", tr = t)
        }
    }

    external fun open(url: String, isPublishMode: Boolean): Long

    external fun read(rtmpPointer: Long, data: ByteArray, offset: Int, size: Int): Int

    external fun write(rtmpPointer: Long, data: ByteArray, size: Int, type: Int, ts: Int): Int

    external fun close(rtmpPointer: Long): Int

    external fun getIpAddr(rtmpPointer: Long): String?

}