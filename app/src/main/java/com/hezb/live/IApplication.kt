package com.hezb.live

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import kotlin.properties.Delegates

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

    companion object {
        private var iApplication: IApplication by Delegates.notNull()

        fun getInstance(): IApplication {
            return iApplication
        }
    }

    override fun onCreate() {
        super.onCreate()
        iApplication = this

        Thread.setDefaultUncaughtExceptionHandler(MyUncaughtExceptionHandler())
    }

}