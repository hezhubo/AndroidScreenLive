package com.hezb.lib.live.mediaprojection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoRenderHandler
 *
 * Description: MediaProjection实例辅助工具.
 *
 * @author  hezhubo
 * @date    2024年04月13日 21:15
 */
class MediaProjectionHelper private constructor() {

    interface MediaProjectionCallback {
        fun onResult(requestCode: Int, mediaProjection: MediaProjection?)
    }

    companion object {
        val sInstance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            MediaProjectionHelper()
        }
    }
    private var mMediaProjection: MediaProjection? = null
    private val mMediaProjectionCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            mMediaProjection!!.unregisterCallback(this)
            mMediaProjection = null
            MediaProjectionRequireService.stopService()
        }
    }
    private val mCallbacks: HashSet<MediaProjectionCallback> = HashSet() // 回调集合

    internal fun onActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (data == null || resultCode != Activity.RESULT_OK) {
            onMediaProjectionCallback(requestCode, null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaProjectionRequireService.startRequireMediaProjection(context, requestCode, data)
        } else {
            val mediaProjection = (context
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(Activity.RESULT_OK, data)
            onMediaProjectionCallback(requestCode, mediaProjection)
        }
    }

    internal fun onMediaProjectionCallback(requestCode: Int, mediaProjection: MediaProjection?) {
        mMediaProjection = mediaProjection
        mMediaProjection?.registerCallback(mMediaProjectionCallback, null)
        if (mCallbacks.isEmpty()) {
            return
        }
        for (callback in mCallbacks) {
            callback.onResult(requestCode, mediaProjection)
        }
    }

    fun registerCallback(callback: MediaProjectionCallback) {
        mCallbacks.add(callback)
    }

    fun unregisterCallback(callback: MediaProjectionCallback) {
        mCallbacks.remove(callback)
    }

    /**
     * 申请获取MediaProjection实例
     */
    fun requireMediaProjection(context: Context, requestCode: Int) {
        if (mMediaProjection == null) {
            // 跳转请求录屏权限
            MediaProjectionRequireActivity.startRequireMediaProjection(context, requestCode)
        } else {
            onMediaProjectionCallback(requestCode, mMediaProjection)
        }
    }

    fun stopMediaProjection() {
        mMediaProjection?.stop()
    }

}