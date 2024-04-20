package com.hezb.lib.live.rtmp.packet.pool

import java.util.*

/**
 * Project Name: AndroidScreenLive
 * File Name:    LruArrayPool
 *
 * Description: 基于LRU算法的ByteArray缓存池.
 *
 * @author  hezhubo
 * @date    2022年07月21日 15:14
 */
class LruArrayPool(val maxSize: Int = DEFAULT_SIZE) : ArrayPool {

    companion object {
        /** 最大缓存池4MB */
        const val DEFAULT_SIZE = 4 * 1024 * 1024
        /** 所需的大小需在此倍数下，缓存才能返回使用 */
        private const val MAX_OVER_SIZE_MULTIPLE = 8
        /** 单个字节数组最大占比：1/2 */
        private const val SINGLE_ARRAY_MAX_SIZE_DIVISOR = 2
    }

    private val groupedMap = GroupedLinkedMap()
    private val sortedSizes = TreeMap<Int, Int>()

    private var currentSize = 0

    @Synchronized
    override fun get(size: Int): ByteArray {
        val possibleSize = sortedSizes.ceilingKey(size) // 获得等于或大于size的key
        val targetSize =
            if (possibleSize != null && (isNoMoreThanHalfFull() || possibleSize <= (MAX_OVER_SIZE_MULTIPLE * size))) {
                possibleSize // 若所需大小在其8倍以内，方可使用缓存池中的ByteArray
            } else {
                size
            }
        val byteArray = groupedMap.get(targetSize)
        if (byteArray != null) {
            currentSize -= byteArray.size
            decrementArrayOfSize(byteArray.size)
        }
        return byteArray ?: ByteArray(targetSize)
    }

    private fun isNoMoreThanHalfFull(): Boolean {
        return currentSize == 0 || maxSize / currentSize >= 2
    }

    @Synchronized
    override fun put(byteArray: ByteArray) {
        val size = byteArray.size
        if (size > maxSize / SINGLE_ARRAY_MAX_SIZE_DIVISOR) {
            return // 该字节数组大于缓存池的一半，太大不去缓存
        }
        groupedMap.put(size, byteArray)
        val current = sortedSizes[size]
        sortedSizes[size] = if (current == null) {
            1
        } else {
            current + 1
        }
        currentSize += size
        evict()
    }

    private fun evict(size: Int = maxSize) {
        while (currentSize > size) {
            val evicted = groupedMap.removeLast()
            if (evicted != null) {
                currentSize -= evicted.size
                decrementArrayOfSize(evicted.size)
            }
        }
    }

    private fun decrementArrayOfSize(size: Int) {
        val current = sortedSizes[size]
        if (current != null) {
            if (current == 1) {
                sortedSizes.remove(size)
            } else {
                sortedSizes[size] = current - 1
            }
        }
    }

    @Synchronized
    override fun clearMemory() {
        evict(0)
    }

    fun getCurrentSize(): Int {
        return currentSize
    }

}