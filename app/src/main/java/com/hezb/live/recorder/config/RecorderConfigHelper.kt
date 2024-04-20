package com.hezb.live.recorder.config

import android.content.Context
import com.hezb.lib.live.config.RecorderConfig

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