package com.hezb.live

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.hezb.live.recorder.RecorderClient
import com.hezb.live.recorder.RecorderConfig
import com.hezb.live.recorder.util.LogUtil
import java.io.File
import java.lang.StringBuilder
import java.text.SimpleDateFormat

/**
 * Project Name: AndroidScreenLive
 * File Name:    MainActivity
 *
 * Description: Demo页面.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:26
 */
class MainActivity : Activity() {

    private lateinit var mMsgTextView: TextView

    private var mMediaProjectionManager: MediaProjectionManager? = null

    private val mClient = RecorderClient()

    private val rtmpUrl = "rtmp://192.168.3.101/live/livestream"

    private var msg = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mMsgTextView = findViewById(R.id.tv_msg)
        mMsgTextView.movementMethod = ScrollingMovementMethod()

        mClient.onStateChangeCallback = object : RecorderClient.OnStateChangeCallback {
            override fun onStartFailure(error: Int) {
                msg.append("开始失败：$error \n")
                mMsgTextView.text = msg.toString()
            }

            override fun onStopFailure(error: Int) {
                msg.append("停止失败：$error \n")
                mMsgTextView.text = msg.toString()
            }

            override fun onMuxerStartSuccess() {
                msg.append("混合器开启成功！\n")
                mMsgTextView.text = msg.toString()
            }

            override fun onMuxerStopSuccess(outputPath: String) {
                msg.append("混合器停止成功！输出视频路径：$outputPath\n")
                mMsgTextView.text = msg.toString()
            }

            override fun onPusherStartSuccess() {
                msg.append("推流器启动成功 rtmpUrl = $rtmpUrl\n")
                mMsgTextView.text = msg.toString()
            }

            override fun onPusherStop() {
                msg.append("推流器已停止！\n")
                mMsgTextView.text = msg.toString()
            }

            override fun onPusherWriteError(errorTimes: Int) {
                mMsgTextView.text = "$msg\n\n 推流写入数据失败次数：$errorTimes"
            }
        }

        findViewById<Button>(R.id.btn_start_record).setOnClickListener {
            if (requestPermission()) {
                return@setOnClickListener
            }
            if (mClient.currentState >= RecorderClient.STATE_PREPARED) {
                mClient.startRecord(createVideoPath())
            } else {
                requestProjection(888)
            }
        }

        findViewById<Button>(R.id.btn_stop_record).setOnClickListener {
            mClient.stop()
        }

        findViewById<Button>(R.id.btn_pause_record).setOnClickListener {
            mClient.pauseRecord()
        }

        findViewById<Button>(R.id.btn_resume_record).setOnClickListener {
            mClient.resumeRecord()
        }

        findViewById<Button>(R.id.btn_start_push).setOnClickListener {
            if (mClient.currentState >= RecorderClient.STATE_PREPARED) {
//                mClient.startPush(rtmpUrl) // 仅直播
                mClient.startPush(rtmpUrl, createVideoPath())
            } else {
                requestProjection(999)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mClient.release()
        mClient.onStateChangeCallback = null
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

    /**
     * 请求必要权限
     */
    private fun requestPermission() : Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val writeExternalStorage = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val recordAudio = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            val permissions = ArrayList<String>()
            if (writeExternalStorage != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (recordAudio != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }
            if (permissions.isEmpty()) {
                return false
            }
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 123)
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
        }
    }

    /**
     * 请求录屏权限
     */
    private fun requestProjection(requestCode: Int) {
        mMediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val captureIntent: Intent =
                mMediaProjectionManager?.createScreenCaptureIntent() ?: return
            startActivityForResult(captureIntent, requestCode)
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(msg = "can not request projection!", tr = e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val mediaProjection =
                mMediaProjectionManager?.getMediaProjection(resultCode, data) ?: return
            val config = RecorderConfig()
            mClient.prepare(config, mediaProjection)
            if (requestCode == 888) {
                mClient.startRecord(createVideoPath())
            } else {
                mClient.startPush(rtmpUrl, createVideoPath())
            }
        }
    }

}