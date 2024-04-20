package com.hezb.lib.live.core

import android.os.SystemClock

/**
 * Project Name: AndroidScreenLive
 * File Name:    TimestampHelper
 *
 * Description: 时间戳辅助工具.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:06
 */
object TimestampHelper {

    private var pauseTimestamp = 0L
    private var pauseDuration = 0L
    @Volatile
    var isPause = false
        private set

    @Synchronized
    fun start() {
        pauseTimestamp = 0L
        pauseDuration = 0L
        isPause = false
    }

    @Synchronized
    fun pause() {
        pauseTimestamp = SystemClock.uptimeMillis()
        isPause = true
    }

    @Synchronized
    fun resume() {
        pauseDuration += SystemClock.uptimeMillis() - pauseTimestamp
        pauseTimestamp = 0L
        isPause = false
    }

    /**
     * 获取时间戳（单位：微秒）
     */
    fun getPresentationTimeUs(): Long {
        val millis = if (pauseDuration > 0) {
            SystemClock.uptimeMillis() - pauseDuration
        } else {
            SystemClock.uptimeMillis()
        }
        return millis * 1000
    }

}