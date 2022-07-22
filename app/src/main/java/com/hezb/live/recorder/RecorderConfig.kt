package com.hezb.live.recorder

import android.util.DisplayMetrics
import com.hezb.live.recorder.model.Size

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecorderConfig
 *
 * Description: 录制配置.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
class RecorderConfig {

    /** 视频尺寸（分辨率） */
    var videoSize = Size(DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT)
    /** 视频码率 */
    var videoBitrate = DEFAULT_VIDEO_BITRATE
    /** 视频帧率 */
    var videoFrameRate = DEFAULT_VIDEO_FRAME_RATE
    /** 视频关键帧间隔 */
    var videoFrameInterval = DEFAULT_VIDEO_FRAME_INTERVAL

    /** 音频码率 */
    var audioBitrate = DEFAULT_AUDIO_BITRATE
    /** 音频采样率 */
    var audioSampleRate = DEFAULT_AUDIO_SAMPLE_RATE
    /** 声道数量 */
    var audioChannelCount = DEFAULT_AUDIO_CHANNEL_STEREO

    /** 录制声音 */
    var recordAudio = true
    /** 是否竖屏录制 */
    var isPortrait = false

    /** 虚拟屏幕的密度 */
    var virtualDisplayDpi = DisplayMetrics.DENSITY_XHIGH

    companion object {
        const val DEFAULT_VIDEO_WIDTH = 960
        const val DEFAULT_VIDEO_HEIGHT = 540
        const val DEFAULT_VIDEO_BITRATE = 5625 * 1000
        const val DEFAULT_VIDEO_FRAME_RATE = 25
        const val DEFAULT_VIDEO_FRAME_INTERVAL = 1

        const val DEFAULT_AUDIO_CHANNEL_STEREO = 1 // 声道数量
        const val DEFAULT_AUDIO_BITRATE = 128000 // 音频码率 128kbps
        const val DEFAULT_AUDIO_SAMPLE_RATE = 44100  // 音频采样率 44.1kHz
    }

}