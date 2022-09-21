package com.hezb.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
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
import com.hezb.live.recorder.config.RecorderConfig
import com.hezb.live.recorder.config.RecorderConfigHelper
import java.text.SimpleDateFormat
import java.util.*


/**
 * Project Name: AndroidScreenLive
 * File Name:    MainActivity
 *
 * Description: Demo页面.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:26
 */
class MainActivity : AppCompatActivity() {

    private lateinit var recorderMsgState: MutableState<String>

    private var rtmpUrl = "rtmp://192.168.3.101/live/livestream"

    private lateinit var recorderConfig: RecorderConfig

    private var recorderAidlInterface: RecorderAidlInterface? = null
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            recorderAidlInterface = RecorderAidlInterface.Stub.asInterface(service)
            recorderAidlInterface?.setCallback(object : RecorderCallback.Stub() {
                val format = SimpleDateFormat("HH:mm:ss")
                override fun onResult(msg: String?) {
                    if (isBindService) {
                        runOnUiThread {
                            recorderMsgState.value += "\n${format.format(Date())} $msg"
                        }
                    }
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recorderAidlInterface = null
            isBindService = false
        }
    }

    private var isBindService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startService(Intent(this, RecorderService::class.java))

        isBindService = bindService(
            Intent(this, RecorderService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        recorderConfig = RecorderConfigHelper.readConfig(this)

        setContent {
            Column(modifier = Modifier.padding(horizontal = 5.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val intent = Intent(this@MainActivity, RecorderConfigActivity::class.java)
                    intent.putExtra("recorderConfig", recorderConfig)
                    startActivityForResult(intent, 333)
                }) {
                    Text("编码器参数设置")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (isBindService) {
                                recorderAidlInterface?.startRecord(recorderConfig)
                            }
                        }) {
                        Text(text = "开始录制")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (isBindService) {
                                recorderAidlInterface?.pauseRecord()
                            }
                        }) {
                        Text(text = "暂停")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (isBindService) {
                                recorderAidlInterface?.resumeRecord()
                            }
                        }) {
                        Text(text = "恢复")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(
                        modifier = Modifier.weight(1f, true),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (isBindService) {
                                recorderAidlInterface?.stop()
                            }
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
                        if (isBindService) {
                            recorderAidlInterface?.startLive(recorderConfig, rtmpUrlState.value)
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
                        if (isBindService) {
                            recorderAidlInterface?.startLiveRecord(recorderConfig, rtmpUrlState.value)
                        }
                    }) {
                        Text(text = "边推边录")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        if (isBindService) {
                            recorderAidlInterface?.stop()
                        }
                    }) {
                        Text(text = "停止推流")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        if (isBindService) {
                            recorderAidlInterface?.testAudioFilter()
                        }
                    }) {
                        Text(text = "audio filter test")
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Button(modifier = Modifier.weight(1f, true), onClick = {
                        if (isBindService) {
                            recorderAidlInterface?.testVideoFilter()
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
        if (isBindService) {
            recorderAidlInterface?.let {
                it.release()
                it.setCallback(null)
            }
            unbindService(serviceConnection)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == 333) {
                val targetConfig = data.getParcelableExtra<RecorderConfig>("recorderConfig")
                if (targetConfig != null) {
                    recorderConfig = targetConfig
                }
            }
        }
    }

}