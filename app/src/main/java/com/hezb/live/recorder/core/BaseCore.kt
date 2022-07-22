package com.hezb.live.recorder.core

import android.os.SystemClock
import com.hezb.live.recorder.RecorderConfig

/**
 * Project Name: AndroidScreenLive
 * File Name:    BaseCore
 *
 * Description: 核心基类.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:49
 */
abstract class BaseCore {

    protected var mOnErrorCallback: OnErrorCallback? = null

    protected var pauseTimestamp = 0L
    protected var pauseDuration = 0L
    protected var isPause = false

    abstract fun prepare(config: RecorderConfig): Int

    abstract fun start(collector: DataCollector): Int

    abstract fun stop()

    abstract fun release()

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
     * 设置错误回调
     */
    fun setOnErrorCallback(callback: OnErrorCallback) {
        mOnErrorCallback = callback
    }

    /**
     * 内部错误回调（异步线程）
     */
    interface OnErrorCallback {
        fun onError(error: String)
    }
}