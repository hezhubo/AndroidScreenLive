package com.hezb.live

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hezb.live.recorder.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    PermissionRequestActivity
 *
 * Description: 权限请求页.
 *
 * @author  hezhubo
 * @date    2022年09月03日 01:18
 */
class PermissionRequestActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_PERMISSION = 100
        private const val REQUEST_CODE_PROJECTION = 200

        const val RESULT_CODE_OK = 666
        const val RESULT_CODE_ERROR_PERMISSION = -10000
        const val RESULT_CODE_ERROR_PROJECTION = -20000

        fun startRequestPermission(
            context: Context,
            flag: Int,
            recordAudio: Boolean,
            recordSave: Boolean,
            hasProjection: Boolean = false
        ) {
            val intent = Intent(context, PermissionRequestActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("flag", flag)
            intent.putExtra("recordAudio", recordAudio)
            intent.putExtra("recordSave", recordSave)
            intent.putExtra("hasProjection", hasProjection)
            context.startActivity(intent)
        }
    }

    private var flag = 0 // 标记是录制、直播或边播边录
    private var recordAudio = true // 是否录制声音
    private var recordSave = true // 是否存储视频
    private var hasProjection = false // 是否已请求录屏权限

    private var recorderAidlInterface: RecorderAidlInterface? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            recorderAidlInterface = RecorderAidlInterface.Stub.asInterface(service)
            if (!toRequestPermission()) {
                if (!hasProjection) {
                    requestProjection()
                } else {
                    recorderAidlInterface?.onPermissionCallback(flag, RESULT_CODE_OK, null)
                    finish()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recorderAidlInterface = null
        }
    }
    private var isBindService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flag = intent.getIntExtra("flag", flag)
        recordAudio = intent.getBooleanExtra("recordAudio", recordAudio)
        recordSave = intent.getBooleanExtra("recordSave", recordSave)
        hasProjection = intent.getBooleanExtra("hasProjection", hasProjection)

        isBindService = bindService(
            Intent(this, RecorderService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBindService) {
            unbindService(serviceConnection)
        }
    }

    private fun toRequestPermission() : Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = ArrayList<String>()
            if (recordAudio) {
                val canRecordAudio = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!canRecordAudio) {
                    permissions.add(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (recordSave) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                    val canRecordSave = ContextCompat.checkSelfPermission(
                        this,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!canRecordSave) {
                        permissions.add(permission)
                    }
                }
            }
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    REQUEST_CODE_PERMISSION
                )
                return true
            }
        }

        return false
    }

    private fun requestProjection() {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        try {
            val captureIntent: Intent =
                mediaProjectionManager?.createScreenCaptureIntent() ?: return
            startActivityForResult(captureIntent, REQUEST_CODE_PROJECTION)
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(msg = "can not request projection!", tr = e)
            recorderAidlInterface?.onPermissionCallback(flag, RESULT_CODE_ERROR_PROJECTION, null)
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
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
            if (canRecordAudio && canRecordSave) {
                requestProjection()
                return
            }
        }
        recorderAidlInterface?.onPermissionCallback(flag, RESULT_CODE_ERROR_PERMISSION, null)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_PROJECTION && data != null) {
            recorderAidlInterface?.onPermissionCallback(flag, RESULT_CODE_OK, data)
        } else {
            recorderAidlInterface?.onPermissionCallback(flag, RESULT_CODE_ERROR_PROJECTION, null)
        }
        finish()
    }

}