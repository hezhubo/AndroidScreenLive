package com.hezb.lib.live.rtmp.packet.pool

/**
 * Project Name: AndroidScreenLive
 * File Name:    GroupedLinkedMap
 *
 * Description: 分组循环链表用于实现LRU算法.
 *
 * @author  hezhubo
 * @date    2022年07月21日 15:14
 */
class GroupedLinkedMap {

    private val head = LinkedEntry()
    private val keyToEntry = HashMap<Int, LinkedEntry>()

    fun put(key: Int, value: ByteArray) {
        val entry = keyToEntry[key]
        if (entry == null) {
            val newEntry = LinkedEntry(key)
            makeTail(newEntry)
            keyToEntry[key] = newEntry
            newEntry.add(value)
        } else {
            entry.add(value)
        }
    }

    fun get(key: Int): ByteArray? {
        val entry = keyToEntry[key]
        return if (entry == null) {
            val newEntry = LinkedEntry(key)
            keyToEntry[key] = newEntry
            makeHead(newEntry)
            newEntry.removeLast()
        } else {
            makeHead(entry)
            entry.removeLast()
        }
    }

    fun removeLast(): ByteArray? {
        var last = head.prev
        while (last != head) {
            val removed = last.removeLast()
            if (removed != null) {
                return removed
            } else {
                removeEntry(last)
                keyToEntry.remove(last.key)
            }

            last = last.prev
        }
        return null
    }

    // Make the entry the most recently used item.
    private fun makeHead(entry: LinkedEntry) {
        removeEntry(entry)
        entry.prev = head
        entry.next = head.next
        updateEntry(entry)
    }

    // Make the entry the least recently used item.
    private fun makeTail(entry: LinkedEntry) {
        removeEntry(entry)
        entry.prev = head.prev
        entry.next = head
        updateEntry(entry)
    }

    private fun updateEntry(entry: LinkedEntry) {
        entry.next.prev = entry
        entry.prev.next = entry
    }

    private fun removeEntry(entry: LinkedEntry) {
        entry.prev.next = entry.next
        entry.next.prev = entry.prev
    }

    class LinkedEntry(val key: Int? = null) {
        private var values: MutableList<ByteArray>? = null
        var next: LinkedEntry = this
        var prev: LinkedEntry = this

        fun removeLast(): ByteArray? {
            val valueSize = size()
            return if (valueSize > 0) {
                values?.removeAt(valueSize - 1)
            } else {
                null
            }
        }

        fun size(): Int {
            return values?.size ?: 0
        }

        fun add(value: ByteArray) {
            if (values == null) {
                values = ArrayList()
            }
            values!!.add(value)
        }
    }

}