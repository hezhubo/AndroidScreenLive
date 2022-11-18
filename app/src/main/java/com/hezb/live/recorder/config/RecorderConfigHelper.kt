package com.hezb.live.recorder.config

import android.content.Context
import android.os.Build

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecorderHelper
 *
 * Description: 录制配置辅助工具.
 *
 * @author  hezhubo
 * @date    2022年09月21日 11:25
 */
object RecorderConfigHelper {

    const val SP_NAME = "recorder"

    const val DEFAULT_VIDEO_WIDTH = 960
    const val DEFAULT_VIDEO_HEIGHT = 540
    const val DEFAULT_VIDEO_BITRATE = 5625 * 1000
    const val DEFAULT_VIDEO_FRAME_RATE = 25
    const val DEFAULT_VIDEO_I_FRAME_INTERVAL = 2

    const val DEFAULT_AUDIO_CHANNEL_COUNT = 1 // 声道数量
    const val DEFAULT_AUDIO_BITRATE = 128000 // 音频码率 128kbps
    const val DEFAULT_AUDIO_SAMPLE_RATE = 44100  // 音频采样率 44.1kHz

    // 录制的音频源类型
    const val AUDIO_SOURCE_TYPE_ALL = 0 // 麦克风+系统输出声音
    const val AUDIO_SOURCE_TYPE_MIC = 1 // 仅麦克风
    const val AUDIO_SOURCE_TYPE_PLAYBACK = 2 // 仅系统输出声音

    /**
     * 是否支持录制系统输出声音
     */
    fun supportRecordPlaybackAudio(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * 默认的录音源类型
     */
    fun getDefaultAudioSourceType(): Int {
        return if (supportRecordPlaybackAudio()) {
            AUDIO_SOURCE_TYPE_ALL
        } else {
            AUDIO_SOURCE_TYPE_MIC
        }
    }

    /**
     * 保存配置
     *
     * @param context
     * @param config
     */
    fun saveConfig(context: Context, config: RecorderConfig) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt("videoWidth", config.videoSize.width)
            putInt("videoHeight", config.videoSize.height)
            putInt("videoBitrate", config.videoBitrate)
            putInt("videoIFrameInterval", config.videoIFrameInterval)
            putString("videoCodecName", config.videoCodecName)
            putInt("videoCodecProfile", config.videoCodecProfile)
            putInt("videoCodecProfileLevel", config.videoCodecProfileLevel)
            putInt("videoBitrateMode", config.videoBitrateMode)
            putInt("videoMaxBFrames", config.videoMaxBFrames)
            putInt("audioBitrate", config.audioBitrate)
            putInt("audioSampleRate", config.audioSampleRate)
            putInt("audioChannelCount", config.audioChannelCount)
            putString("audioCodecName", config.audioCodecName)
            putInt("audioCodecAACProfile", config.audioCodecAACProfile)
            putBoolean("recordAudio", config.recordAudio)
            putInt("audioSourceType", config.audioSourceType)
            putInt("virtualDisplayDpi", config.virtualDisplayDpi)
            apply()
        }
    }

    /**
     * 读取配置
     *
     * @param context
     */
    fun readConfig(context: Context): RecorderConfig {
        val config = RecorderConfig()
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).apply {
            config.videoSize.width = getInt("videoWidth", config.videoSize.width)
            config.videoSize.height = getInt("videoHeight", config.videoSize.height)
            config.videoBitrate = getInt("videoBitrate", config.videoBitrate)
            config.videoIFrameInterval = getInt("videoIFrameInterval", config.videoIFrameInterval)
            config.videoCodecName = getString("videoCodecName", config.videoCodecName)
            config.videoCodecProfile = getInt("videoCodecProfile", config.videoCodecProfile)
            config.videoCodecProfileLevel = getInt("videoCodecProfileLevel", config.videoCodecProfileLevel)
            config.videoBitrateMode = getInt("videoBitrateMode", config.videoBitrateMode)
            config.videoMaxBFrames = getInt("videoMaxBFrames", config.videoMaxBFrames)
            config.audioBitrate = getInt("audioBitrate", config.audioBitrate)
            config.audioSampleRate = getInt("audioSampleRate", config.audioSampleRate)
            config.audioChannelCount = getInt("audioChannelCount", config.audioChannelCount)
            config.audioCodecName = getString("audioCodecName", config.audioCodecName)
            config.audioCodecAACProfile = getInt("audioCodecAACProfile", config.audioCodecAACProfile)
            config.recordAudio = getBoolean("recordAudio", config.recordAudio)
            config.audioSourceType = getInt("audioSourceType", config.audioSourceType)
            config.virtualDisplayDpi = getInt("virtualDisplayDpi", config.virtualDisplayDpi)
        }
        return config
    }

}