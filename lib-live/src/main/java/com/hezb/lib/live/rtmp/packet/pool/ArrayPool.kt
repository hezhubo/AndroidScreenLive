package com.hezb.lib.live.rtmp.packet.pool

/**
 * Project Name: AndroidScreenLive
 * File Name:    ArrayPool
 *
 * Description: ByteArray缓存池接口.
 *
 * @author  hezhubo
 * @date    2022年07月21日 15:14
 */
interface ArrayPool {

    fun get(size: Int): ByteArray

    fun put(byteArray: ByteArray)

    fun clearMemory()
}