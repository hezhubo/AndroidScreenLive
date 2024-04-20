package com.hezb.lib.live.mediaprojection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoRenderHandler
 *
 * Description: MediaProjection权限请求透明页.
 *
 * @author  hezhubo
 * @date    2024年04月13日 21:16
 */
class MediaProjectionRequireActivity : Activity() {

    companion object {
        /**
         * 执行申请MediaProjection权限
         *
         * @param context     上下文
         * @param requestCode 请求码
         */
        fun startRequireMediaProjection(context: Context, requestCode: Int) {
            val intent = Intent(context, MediaProjectionRequireActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("requestCode", requestCode)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestProjection(intent.getIntExtra("requestCode", 0))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        MediaProjectionHelper.sInstance.onActivityResult(this, requestCode, resultCode, data)
        finish()
    }

    private fun requestProjection(requestCode: Int) {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), requestCode)
    }

}