package com.hezb.lib.live.core

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Surface
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.model.Size
import com.hezb.lib.live.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    VideoEncoder
 *
 * Description: 视频编码器.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:10
 */
class VideoEncoder(private val config: RecorderConfig) {

    private val mEncodeFormat: MediaFormat
    private val mEncoder: MediaCodec
    private var mEncoderInputSurface: Surface? = null

    /** 是否正在录制 */
    private var isRecording = false

    private val mineType = MediaFormat.MIMETYPE_VIDEO_AVC

    init {
        mEncodeFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mineType)
            setInteger(MediaFormat.KEY_WIDTH, config.videoSize.width)
            setInteger(MediaFormat.KEY_HEIGHT, config.videoSize.height)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFrameRate)
            setInteger(MediaFormat.KEY_CAPTURE_RATE, config.videoFrameRate)
            setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / config.videoFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.videoIFrameInterval)
            if (config.videoCodecProfile != 0 && config.videoCodecProfileLevel != 0) {
                setInteger(MediaFormat.KEY_PROFILE, config.videoCodecProfile)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_LEVEL, config.videoCodecProfileLevel)
                }
            }
            if (config.videoBitrateMode >= 0) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, config.videoBitrateMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.videoMaxBFrames > 0) {
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, config.videoMaxBFrames)
            }
        }
        LogUtil.i(msg = "video encode format : $mEncodeFormat")
        mEncoder = if (!config.videoCodecName.isNullOrEmpty()) {
            MediaCodec.createByCodecName(config.videoCodecName!!)
        } else{
            MediaCodec.createEncoderByType(mineType)
        }
    }

    fun startEncoder(): MediaCodec {
        mEncoder.configure(mEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mEncoderInputSurface = mEncoder.createInputSurface()
        mEncoder.start()
        isRecording = true
        return mEncoder
    }

    fun stopEncoder() {
        isRecording = false
        mEncoder.stop()
        mEncoder.release()
    }

    fun setVideoBitrate(bitrate: Int) {
        config.videoBitrate = bitrate
        if (isRecording) {
            try {
                val bitrateBundle = Bundle()
                bitrateBundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate)
                mEncoder.setParameters(bitrateBundle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getInputSurface(): Surface? {
        return mEncoderInputSurface
    }

    fun getVideoSize(): Size {
        return config.videoSize
    }

    fun getVideoFrameRate(): Int {
        return config.videoFrameRate
    }

}