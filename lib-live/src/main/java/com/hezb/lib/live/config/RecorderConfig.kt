package com.hezb.lib.live.config

import android.media.MediaCodecInfo
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.DisplayMetrics
import androidx.annotation.ChecksSdkIntAtLeast
import com.hezb.lib.live.model.Size

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecorderConfig
 *
 * Description: 录制配置.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
class RecorderConfig() : Parcelable {

    /**
     * 视频尺寸（分辨率）
     * 如果设置的视频尺寸宽高比与实际的屏幕尺寸宽高比不一致时，画面会以居中适应方式填充黑边
     */
    var videoSize = Size(960, 540)
    /** 视频码率 */
    var videoBitrate = 5625 * 1000
    /** 视频帧率 */
    var videoFrameRate = 25
    /** 视频关键帧间隔 */
    var videoIFrameInterval = 5
    /** 视频编码器名称（空则使用默认类型编码器） */
    var videoCodecName: String? = null
    /** 视频编码器对应支持的配置 profile */
    var videoCodecProfile: Int = 0
    /** 视频编码器对应支持的配置 level */
    var videoCodecProfileLevel: Int = 0
    /** 视频编码模式 */
    var videoBitrateMode: Int = -1
    /**
     * 视频编码I/P帧间最大的B帧数量（默认没有B帧）
     * API level 29 及以上支持设置
     * TODO test 当前测试(SM-A9080,android12)最大值只能设置到1
     */
    var videoMaxBFrames: Int = 0

    /** 音频码率 */
    var audioBitrate = 128000 // 音频码率 128kbps
    /** 音频采样率 */
    var audioSampleRate = 44100 // 音频采样率 44.1kHz
    /** 声道数量 */
    var audioChannelCount = 1 // 声道数量
    /** 音频编码器名称（空则使用默认类型编码器） */
    var audioCodecName: String? = null
    /** 音频编码器对应支持的配置 aac-profile */
    var audioCodecAACProfile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC // AAC编码规格 LC：低复杂度规格

    /** 录制声音 */
    var recordAudio = true
    /** 录制声音源 */
    var audioSourceType = AUDIO_SOURCE_TYPE_MIC

    /** 虚拟屏幕的密度 */
    var virtualDisplayDpi = DisplayMetrics.DENSITY_XHIGH

    override fun toString(): String {
        return "recorder config:\n" +
                "video:\n\t" +
                "resolution = ${videoSize.width}x${videoSize.height},\n\t" +
                "bitrate = $videoBitrate,\n\t" +
                "bitrate mode = $videoBitrateMode,\n\t" +
                "frame rate = $videoFrameRate,\n\t" +
                "I frame interval = $videoIFrameInterval,\n\t" +
                "max b frame = $videoMaxBFrames,\n\t" +
                "encoder = $videoCodecName,\n\t" +
                "profile = $videoCodecProfile,\n\t" +
                "level = $videoCodecProfileLevel,\n" +
                "audio:\n\t" +
                "bitrate = $audioBitrate,\n\t" +
                "sample rate = $audioSampleRate,\n\t" +
                "channel count = $audioChannelCount,\n\t" +
                "encoder = $audioCodecName,\n\t" +
                "aac-profile = $audioCodecAACProfile,\n" +
                "是否录音：$recordAudio, 声音源：$audioSourceType, 虚拟屏幕的密度：$virtualDisplayDpi"
    }

    constructor(parcel: Parcel) : this() {
        videoSize = parcel.readParcelable(Size::class.java.classLoader) ?: Size(0, 0)
        videoBitrate = parcel.readInt()
        videoFrameRate = parcel.readInt()
        videoIFrameInterval = parcel.readInt()
        videoCodecName = parcel.readString()
        videoCodecProfile = parcel.readInt()
        videoCodecProfileLevel = parcel.readInt()
        videoBitrateMode = parcel.readInt()
        videoMaxBFrames = parcel.readInt()
        audioBitrate = parcel.readInt()
        audioSampleRate = parcel.readInt()
        audioChannelCount = parcel.readInt()
        audioCodecName = parcel.readString()
        audioCodecAACProfile = parcel.readInt()
        recordAudio = parcel.readByte() != 0.toByte()
        audioSourceType = parcel.readInt()
        virtualDisplayDpi = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(videoSize, flags)
        parcel.writeInt(videoBitrate)
        parcel.writeInt(videoFrameRate)
        parcel.writeInt(videoIFrameInterval)
        parcel.writeString(videoCodecName)
        parcel.writeInt(videoCodecProfile)
        parcel.writeInt(videoCodecProfileLevel)
        parcel.writeInt(videoBitrateMode)
        parcel.writeInt(videoMaxBFrames)
        parcel.writeInt(audioBitrate)
        parcel.writeInt(audioSampleRate)
        parcel.writeInt(audioChannelCount)
        parcel.writeString(audioCodecName)
        parcel.writeInt(audioCodecAACProfile)
        parcel.writeByte(if (recordAudio) 1 else 0)
        parcel.writeInt(audioSourceType)
        parcel.writeInt(virtualDisplayDpi)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RecorderConfig> {
        override fun createFromParcel(parcel: Parcel): RecorderConfig {
            return RecorderConfig(parcel)
        }

        override fun newArray(size: Int): Array<RecorderConfig?> {
            return arrayOfNulls(size)
        }

        // 录制的音频源类型
        const val AUDIO_SOURCE_TYPE_ALL = 0 // 麦克风+系统输出声音
        const val AUDIO_SOURCE_TYPE_MIC = 1 // 仅麦克风
        const val AUDIO_SOURCE_TYPE_PLAYBACK = 2 // 仅系统输出声音

        /**
         * 是否支持录制系统输出声音
         */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun supportRecordPlaybackAudio(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }
    }

}