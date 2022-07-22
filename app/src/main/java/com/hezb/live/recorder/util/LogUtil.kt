package com.hezb.live.recorder.util

import android.util.Log

/**
 * Project Name: AndroidScreenLive
 * File Name:    LogUtil
 *
 * Description: Log.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:33
 */
object LogUtil {

    const val TAG = "ScreenLive"

    @JvmStatic
    @Volatile
    var writeLogs = true

    @JvmStatic
    fun i(tag: String = TAG, msg: String) {
        if (writeLogs) Log.i(tag, msg)
    }

    @JvmStatic
    fun d(tag: String = TAG, msg: String) {
        if (writeLogs) Log.d(tag, msg)
    }

    @JvmStatic
    fun e(tag: String = TAG, msg: String) {
        Log.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String = TAG, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
    }

    @JvmStatic
    fun v(tag: String = TAG, msg: String) {
        if (writeLogs) Log.v(tag, msg)
    }

    @JvmStatic
    fun w(tag: String = TAG, msg: String) {
        if (writeLogs) Log.w(tag, msg)
    }

}