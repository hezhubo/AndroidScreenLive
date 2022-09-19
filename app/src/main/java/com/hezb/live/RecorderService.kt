package com.hezb.live

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import com.hezb.live.recorder.RecorderClient
import com.hezb.live.recorder.RecorderConfig
import java.io.File
import java.text.SimpleDateFormat

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecorderService
 *
 * Description: 录制前台服务.
 *
 * @author  hezhubo
 * @date    2022年09月02日 16:49
 */
class RecorderService : Service(), RecorderClient.OnStateChangeCallback {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "recorder"

        private const val FLAG_RECORD = 0
        private const val FLAG_LIVE = 1
        private const val FLAG_LIVE_RECORD = 2
    }

    private val recorderBinder = RecorderBinder()

    private var recorderClient: RecorderClient? = null

    private lateinit var recorderConfig: RecorderConfig
    private var rtmpUrl: String? = null

    private var mediaProjection: MediaProjection? = null

    private var recorderCallback: RecorderCallback? = null

    override fun onCreate() {
        super.onCreate()
        showForeground()
    }

    private fun showForeground() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        // 点击回到应用的intent
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.setPackage(null)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("录制服务")
            .setShowWhen(false)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
        startForeground(9999, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder {
        return recorderBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        recorderClient?.let {
            it.stop(true)
            it.onStateChangeCallback = null
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createVideoPath(): String? {
        val outputDir = Environment.getExternalStorageDirectory().path + "/HezbRecorder/"
        val dirFile = File(outputDir)
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                return null
            }
        }
        return "${outputDir}${SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())}.mp4"
    }

    private inner class RecorderBinder : RecorderAidlInterface.Stub() {

        override fun onPermissionCallback(flag: Int, resultCode: Int, data: Intent?) {
            if (resultCode == PermissionRequestActivity.RESULT_CODE_OK) {
                if (mediaProjection == null) {
                    if (data != null) {
                        val mediaProjectionManager =
                            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjection =
                            mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
                    }
                }
                mediaProjection?.let { mediaProjection ->
                    recorderClient?.let {
                        it.release() // 停止并释放上一次录制
                        it.onStateChangeCallback = null
                    }
                    when (flag) {
                        FLAG_RECORD -> {
                            RecorderClient().also {
                                it.onStateChangeCallback = this@RecorderService
                                it.prepare(recorderConfig, mediaProjection)
                                it.startRecord(createVideoPath())
                                recorderClient = it
                            }
                            return
                        }
                        FLAG_LIVE -> {
                            RecorderClient().also {
                                it.onStateChangeCallback = this@RecorderService
                                it.prepare(recorderConfig, mediaProjection)
                                it.startPush(rtmpUrl)
                                recorderClient = it
                            }
                            return
                        }
                        FLAG_LIVE_RECORD -> {
                            RecorderClient().also {
                                it.onStateChangeCallback = this@RecorderService
                                it.prepare(recorderConfig, mediaProjection)
                                it.startPush(rtmpUrl, createVideoPath())
                                recorderClient = it
                            }
                            return
                        }
                        else -> {}
                    }
                }
            }

            recorderCallback?.onResult("申请权限出错！")
        }

        override fun setCallback(callback: RecorderCallback?) {
            recorderCallback = callback
        }

        override fun getCurrentState(): Int {
            return recorderClient?.currentState ?: RecorderClient.STATE_RELEASE
        }

        override fun isRunning(): Boolean {
            return recorderClient?.currentState == RecorderClient.STATE_RUNNING
        }

        override fun startRecord(recorderConfig: RecorderConfig?) {
            if (recorderConfig == null) {
                return
            }
            this@RecorderService.recorderConfig = recorderConfig
            PermissionRequestActivity.startRequestPermission(
                this@RecorderService,
                flag = FLAG_RECORD,
                recordAudio = recorderConfig.recordAudio,
                recordSave = true,
                hasProjection = mediaProjection != null
            )
        }

        override fun startLive(recorderConfig: RecorderConfig?, rtmpUrl: String?) {
            if (recorderConfig == null) {
                return
            }
            this@RecorderService.recorderConfig = recorderConfig
            this@RecorderService.rtmpUrl = rtmpUrl
            PermissionRequestActivity.startRequestPermission(
                this@RecorderService,
                flag = FLAG_LIVE,
                recordAudio = recorderConfig.recordAudio,
                recordSave = false,
                hasProjection = mediaProjection != null
            )
        }

        override fun startLiveRecord(recorderConfig: RecorderConfig?, rtmpUrl: String?) {
            if (recorderConfig == null) {
                return
            }
            this@RecorderService.recorderConfig = recorderConfig
            this@RecorderService.rtmpUrl = rtmpUrl
            PermissionRequestActivity.startRequestPermission(
                this@RecorderService,
                flag = FLAG_LIVE_RECORD,
                recordAudio = recorderConfig.recordAudio,
                recordSave = true,
                hasProjection = mediaProjection != null
            )
        }

        override fun pauseRecord() {
            recorderClient?.pauseRecord()
        }

        override fun resumeRecord() {
            recorderClient?.resumeRecord()
        }

        override fun stop() {
            recorderClient?.stop()
        }

        override fun release() {
            recorderClient?.release()
        }

        override fun testAudioFilter() {
            recorderClient?.let {
                TestFilterHelper.setVolumeAudioFilter(it)
            }
        }

        override fun testVideoFilter() {
            recorderClient?.let {
                TestFilterHelper.setVideoFilter(it, this@RecorderService)
            }
        }

    }

    override fun onStartFailure(error: Int) {
        recorderCallback?.onResult("开始失败：$error")
    }

    override fun onStopFailure(error: Int) {
        recorderCallback?.onResult("停止失败：$error")
    }

    override fun onMuxerStartSuccess() {
        recorderCallback?.onResult("混合器开启成功！")
    }

    override fun onMuxerStopSuccess(outputPath: String) {
        recorderCallback?.onResult("混合器停止成功！输出视频路径：$outputPath")
    }

    override fun onPusherStartSuccess() {
        recorderCallback?.onResult("推流器启动成功 rtmpUrl = $rtmpUrl")
    }

    override fun onPusherStop() {
        recorderCallback?.onResult("推流器已停止！")
    }

    override fun onPusherWriteError(errorTimes: Int) {
        if (errorTimes <= 10) {
            recorderCallback?.onResult("推流写入数据失败！$errorTimes")
        }
        if (errorTimes == 200) {
            // 断开
            recorderClient?.stop()
        }
    }

}