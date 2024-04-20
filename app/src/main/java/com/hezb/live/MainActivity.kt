package com.hezb.live

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hezb.lib.live.LiveClient
import com.hezb.lib.live.LiveClientListener
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.mediaprojection.MediaProjectionHelper
import com.hezb.live.recorder.config.RecorderConfigHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


/**
 * Project Name: AndroidScreenLive
 * File Name:    MainActivity
 *
 * Description: Demo页面.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:26
 */
class MainActivity : AppCompatActivity(), MediaProjectionHelper.MediaProjectionCallback {

    private val REQUEST_CODE_PERMISSION_RECORD_AUDIO = 100
    private val REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 101
    private val REQUEST_CODE_PERMISSION_ALL =
        REQUEST_CODE_PERMISSION_RECORD_AUDIO + REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE
    private val REQUEST_CODE_MEDIAPROJECTION = 200
    private val REQUEST_CODE_CONFIG = 333

    private val FLAG_RECORD = 0
    private val FLAG_LIVE = 1
    private val FLAG_LIVE_RECORD = 2
    private var flag: Int = FLAG_RECORD

    private lateinit var recorderMsgState: MutableState<String>

    private var rtmpUrl = "rtmp://192.168.10.5/live/livestream"

    private lateinit var recorderConfig: RecorderConfig
    private var liveClient: LiveClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MediaProjectionHelper.sInstance.registerCallback(this)

        recorderConfig = RecorderConfigHelper.readConfig(this)

        setContent {
            Column(modifier = Modifier.padding(horizontal = 5.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val intent = Intent(this@MainActivity, RecorderConfigActivity::class.java)
                    intent.putExtra("recorderConfig", recorderConfig)
                    startActivityForResult(intent, REQUEST_CODE_CONFIG)
                }) {
                    Text("编码器参数设置")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            flag = FLAG_RECORD
                            if (!toRequestPermission(recorderConfig.recordAudio, true)) {
                                toRequestMediaProjection()
                            }
                        }) {
                        Text(text = "开始录制")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            liveClient?.pauseRecord()
                        }) {
                        Text(text = "暂停")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            liveClient?.resumeRecord()
                        }) {
                        Text(text = "恢复")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            liveClient?.stop()
                        }) {
                        Text(text = "停止录制")
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                val rtmpUrlState: MutableState<String> = remember { mutableStateOf(rtmpUrl) }
                TextField(
                    value = rtmpUrlState.value,
                    onValueChange = {
                        rtmpUrlState.value = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(text = "填入推流地址")
                    },
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        if (rtmpUrlState.value.isEmpty()) {
                            Toast.makeText(this@MainActivity, "请填入推流地址", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        flag = FLAG_LIVE
                        if (!toRequestPermission(recorderConfig.recordAudio, false)) {
                            toRequestMediaProjection()
                        }
                    }) {
                        Text(text = "开始推流")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        if (rtmpUrlState.value.isEmpty()) {
                            Toast.makeText(this@MainActivity, "请填入推流地址", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        flag = FLAG_LIVE_RECORD
                        if (!toRequestPermission(recorderConfig.recordAudio, true)) {
                            toRequestMediaProjection()
                        }
                    }) {
                        Text(text = "边推边录")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        liveClient?.stop()
                    }) {
                        Text(text = "停止推流")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        liveClient?.let {
                            TestFilterHelper.setVolumeAudioFilter(it)
                        }
                    }) {
                        Text(text = "audio filter test")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        liveClient?.let {
                            TestFilterHelper.setVideoFilter(it, this@MainActivity, recorderConfig.videoSize)
                        }
                    }) {
                        Text(text = "video filter test")
                    }
                }

                recorderMsgState = remember { mutableStateOf("") }
                Text(
                    text = recorderMsgState.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaProjectionHelper.sInstance.unregisterCallback(this)
        liveClient?.stop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_CODE_CONFIG) {
                val targetConfig = data.getParcelableExtra<RecorderConfig>("recorderConfig")
                if (targetConfig != null) {
                    recorderConfig = targetConfig
                }
            }
        }
    }

    private fun toRequestPermission(recordAudio: Boolean, recordSave: Boolean) : Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = ArrayList<String>()
            var requestCode = 0
            if (recordAudio) {
                val canRecordAudio = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!canRecordAudio) {
                    permissions.add(Manifest.permission.RECORD_AUDIO)
                    requestCode += REQUEST_CODE_PERMISSION_RECORD_AUDIO
                }
            }
            if (recordSave) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                    val canRecordSave = ContextCompat.checkSelfPermission(
                        this,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!canRecordSave) {
                        permissions.add(permission)
                        requestCode += REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE
                    }
                }
            }
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    requestCode
                )
                return true
            }
        }

        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var canRecordAudio = true
        var canRecordSave = true
        for ((index, permission) in permissions.withIndex()) {
            if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                if (permission == Manifest.permission.RECORD_AUDIO) {
                    canRecordAudio = false
                }
                if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    canRecordSave = false
                }
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSION_RECORD_AUDIO && canRecordAudio
            || (requestCode == REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE && canRecordSave)
            || (requestCode == REQUEST_CODE_PERMISSION_ALL && canRecordAudio && canRecordSave)) {
            toRequestMediaProjection()
        }
    }

    private fun toRequestMediaProjection() {
        MediaProjectionHelper.sInstance.requireMediaProjection(this, REQUEST_CODE_MEDIAPROJECTION)
    }

    override fun onResult(requestCode: Int, mediaProjection: MediaProjection?) {
        if (mediaProjection == null) {
            Toast.makeText(this@MainActivity, "无录屏权限", Toast.LENGTH_SHORT).show()
            return
        }
        runLiveClient(mediaProjection)
    }

    private fun runLiveClient(mediaProjection: MediaProjection) {
        if (liveClient == null) {
            liveClient = LiveClient().also {
                it.liveClientListener = object : LiveClientListener {
                    override fun onStartFailure(e: Exception) {
                        refreshMessage("开始失败：${e.message}")
                    }

                    override fun onStopped() {
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                            // API34开始，每个MediaProjection实例只能使用一次
                            MediaProjectionHelper.sInstance.stopMediaProjection()
                        }
                        if (isDestroyed) {
                            liveClient?.liveClientListener = null
                            return
                        }
                        refreshMessage("客户端已停止")
                    }

                    override fun onEncodingError(e: Exception) {
                        refreshMessage("编码器编码出错：${e.message}")
                    }

                    override fun onMuxerStartSuccess() {
                        refreshMessage("混合器开启成功！")
                    }

                    override fun onMuxerStartFailure(e: Exception) {
                        refreshMessage("混合器启动出错：${e.message}")
                    }

                    override fun onMuxerStopSuccess(outputPath: String) {
                        refreshMessage("混合器停止成功！输出视频路径：$outputPath")
                    }

                    override fun onMuxerStopFailure(e: Exception) {
                        refreshMessage("混合器停止出错：${e.message}")
                    }

                    override fun onPusherStartSuccess() {
                        refreshMessage("推流器开启成功！rtmpUrl = $rtmpUrl")
                    }

                    override fun onPushingError(e: Exception) {
                        refreshMessage("推流器推流出错：${e.message}")
                    }
                }
            }
        }
        when (flag) {
            FLAG_RECORD -> {
                liveClient!!.startRecord(recorderConfig, createVideoPath(), mediaProjection)
                return
            }
            FLAG_LIVE -> {
                liveClient!!.startPush(recorderConfig, rtmpUrl, mediaProjection)
                return
            }
            FLAG_LIVE_RECORD -> {
                liveClient!!.startPush(recorderConfig, rtmpUrl, mediaProjection, createVideoPath())
                return
            }
            else -> {}
        }
    }

    private fun refreshMessage(msg: String) {
        runOnUiThread {
            recorderMsgState.value += "\n${SimpleDateFormat("HH:mm:ss").format(Date())} $msg"
        }
    }

    private fun createVideoPath(): String? {
        val outputDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // dir : /storage/emulated/0/Android/data/$packageName/files/Recorder
            File(getExternalFilesDir(null), "Recorder")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // dir : /storage/emulated/0/Pictures/Screenshots
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.DIRECTORY_SCREENSHOTS
            )
        } else {
            // dir : /storage/emulated/0/Movies
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                return null
            }
        }
        return File(
            outputDir,
            "${SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())}.mp4"
        ).absolutePath
    }

}