package com.hezb.live

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Project Name: AndroidScreenLive
 * File Name:    IApplication
 *
 * Description: Application.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:26
 */
class IApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recorderChannel = NotificationChannel(
                RecorderService.NOTIFICATION_CHANNEL_ID,
                "屏幕录制",
                NotificationManager.IMPORTANCE_LOW
            )
            recorderChannel.setShowBadge(false)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(recorderChannel)
        }
    }

}