package com.hezb.lib.live.mediaprojection

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import com.hezb.lib.live.R

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoRenderHandler
 *
 * Description: MediaProjection权限请求前台服务.
 *
 * @author  hezhubo
 * @date    2024年04月13日 21:18
 */
class MediaProjectionRequireService : Service() {

    companion object {
        const val NOTIFICATION_ID = 9999
        var NOTIFICATION_CHANNEL_ID = "ServiceNotificationChannel"
        var NOTIFICATION_ICON = 0

        private var service: MediaProjectionRequireService? = null

        /**
         * 执行请求MediaProjection
         *
         * @param context     上下文
         * @param requestCode 请求录屏权限的请求码
         * @param data        请求录屏权限返回的data
         */
        fun startRequireMediaProjection(context: Context, requestCode: Int, data: Intent?) {
            val intent = Intent(context, MediaProjectionRequireService::class.java)
            intent.putExtra("requestCode", requestCode)
            intent.putExtra("data", data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止此服务（主要是为了清除前台通知栏通知消息）
         */
        fun stopService() {
            service?.stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        service = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val nc = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, getString(R.string.service_notification_title),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(nc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        showForeground()
        val data = intent.getParcelableExtra<Intent>("data")
        val requestCode = intent.getIntExtra("requestCode", 0)
        if (data != null) {
            val mediaProjection =
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                    Activity.RESULT_OK,
                    data
                )
            MediaProjectionHelper.sInstance.onMediaProjectionCallback(requestCode, mediaProjection)
        }
        return START_NOT_STICKY // 被系统杀死不再自动重启
    }

    private fun showForeground() {
        val builder = Notification.Builder(this)
        var icon = NOTIFICATION_ICON
        if (icon == 0) {
            if (applicationInfo.icon != 0) {
                icon = applicationInfo.icon
            } else if (applicationInfo.logo != 0) {
                icon = applicationInfo.logo
            }
        }
        builder.setSmallIcon(icon)
            .setContentTitle(getString(R.string.service_notification_title))
            .setShowWhen(false)
            .setAutoCancel(false)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
        }
        startForeground(NOTIFICATION_ID, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        service = null
    }
}