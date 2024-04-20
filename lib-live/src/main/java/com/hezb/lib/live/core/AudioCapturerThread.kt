package com.hezb.lib.live.core

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.model.AudioBuffer
import com.hezb.lib.live.util.AudioUtil
import com.hezb.lib.live.util.LogUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    AudioCapturerThread
 *
 * Description: 音频采集器线程.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:06
 */
@SuppressLint("MissingPermission")
class AudioCapturerThread(
    private val encoder: AudioEncoderThread,
    config: RecorderConfig,
    mediaProjection: MediaProjection? = null
) : Thread("AudioCapturerThread") {

    private var mAudioRecord: AudioRecord? = null
    private var mPlaybackAudioRecord: AudioRecord? = null

    private var isRunning = true

    init {
        val audioChannel = if (config.audioChannelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.audioSampleRate,
            audioChannel,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeInBytes = minBufferSize * 2 // 录制16位PCM数据，保证byte缓存是双字节

        if (config.audioSourceType != RecorderConfig.AUDIO_SOURCE_TYPE_PLAYBACK
            || !RecorderConfig.supportRecordPlaybackAudio()) {
            try {
                mAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    config.audioSampleRate,
                    audioChannel,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )
            } catch (e: Exception) {
                LogUtil.e(msg = "can't create audio recorder!", tr = e)
                throw e
            }
            if (AudioRecord.STATE_INITIALIZED != mAudioRecord?.state) {
                throw IllegalStateException("audio recorder state is not initialized!")
            }
        }

        if (config.audioSourceType != RecorderConfig.AUDIO_SOURCE_TYPE_MIC
            && RecorderConfig.supportRecordPlaybackAudio() && mediaProjection != null) {
            val apccBuilder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            apccBuilder.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            apccBuilder.addMatchingUsage(AudioAttributes.USAGE_GAME) // 游戏类型声音
            apccBuilder.addMatchingUsage(AudioAttributes.USAGE_MEDIA) // 媒体类型声音
            val audioRecorderBuilder = AudioRecord.Builder()
//                .setAudioSource(MediaRecorder.AudioSource.DEFAULT) // 注意！无法同时设置音频来源
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.audioSampleRate)
                        .setChannelMask(audioChannel)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setAudioPlaybackCaptureConfig(apccBuilder.build())
            mPlaybackAudioRecord = try {
                audioRecorderBuilder.build()
            } catch (e: Exception) {
                LogUtil.e(msg = "can't create playback audio recorder!", tr = e)
                null
            }
            mPlaybackAudioRecord?.let { audioRecord ->
                if (AudioRecord.STATE_INITIALIZED != audioRecord.state) {
                    mPlaybackAudioRecord = null // 状态异常，不进行系统播放声音录制
                    LogUtil.e(msg = "playback audio recorder state error!")
                }
            }
        }
    }

    fun quit() {
        isRunning = false
        try {
            join()
        } catch (e: InterruptedException) {
            LogUtil.e(msg = "audio capturer thread join error!", tr = e)
        }
    }

    override fun run() {
        // 开始录音
        mAudioRecord?.startRecording()
        mPlaybackAudioRecord?.startRecording()

        var audioBuffer: AudioBuffer
        var size: Int
        var pbAudioBuffer: AudioBuffer? = null
        var pbSize: Int
        while (isRunning) {
            audioBuffer = encoder.getAudioBuffer()
            size = mAudioRecord?.read(audioBuffer.byteArray, 0, audioBuffer.byteArray.size) ?: 0

            pbSize = 0
            mPlaybackAudioRecord?.let { audioRecord ->
                pbAudioBuffer = encoder.getAudioBuffer().also {
                    pbSize = audioRecord.read(it.byteArray, 0, it.byteArray.size)
                }
            }

            if (isRunning && (size > 0 || pbSize > 0) && !TimestampHelper.isPause) {
                audioBuffer.size = size
                pbAudioBuffer?.let {
                    it.size = pbSize
                    mixPcm(audioBuffer, it)
                    it.free = true
                    pbAudioBuffer = null
                }
                encoder.feedPCMAudioBuffer(audioBuffer)
            } else {
                audioBuffer.free = true
                pbAudioBuffer?.free = true
                pbAudioBuffer = null
            }
        }

        mAudioRecord?.stop()
        mPlaybackAudioRecord?.stop()

        mAudioRecord?.release()
        mAudioRecord = null
        mPlaybackAudioRecord?.release()
        mPlaybackAudioRecord = null
    }

    private val DEF_MAX = Short.MAX_VALUE.toInt()
    private val DEF_MIN = Short.MIN_VALUE.toInt()
    private val pbVolumeScale = 0.7f // 系统播放声音衰减因子

    /**
     * 参考：https://github.com/quanwstone/AudioMix/blob/master/main.cpp
     * 自适应混音加权
     * 使用可变的衰减因子对语音进行衰减，该衰减因子代表了语音的权重
     * 衰减因子随着音频数据的变化而变化，当溢出时，衰减因子变小，使得后续的数据在衰减后处于临界值以内
     * 没有溢出时，又让衰减因子慢慢增大，使数据较为平缓的变化
     *
     * 最终合成数据存储到audioBuffer
     *
     * @param audioBuffer
     * @param pbAudioBuffer
     */
    private fun mixPcm(audioBuffer: AudioBuffer, pbAudioBuffer: AudioBuffer) {
        if (pbAudioBuffer.size == 0) {
            return // 无需混合
        }
        if (audioBuffer.size == 0) {
            // 当前无麦克风录音数据，使用系统录音数据
            audioBuffer.size = pbAudioBuffer.size
            for (i in 0 until pbAudioBuffer.size) {
                audioBuffer.byteArray[i] = pbAudioBuffer.byteArray[i]
            }
            return
        }
        var f = 1.0 // 衰减因子
        var ioutput: Int
        val realSize = audioBuffer.size.coerceAtLeast(pbAudioBuffer.size)
        for (i in 0 until realSize step 2) {
            val micShort = if (audioBuffer.size > i + 1) {
                AudioUtil.convertPcm16BitByteToShort(audioBuffer.byteArray, i)
            } else {
                0
            }
            val pbShort = if (pbAudioBuffer.size > i + 1) {
                (AudioUtil.convertPcm16BitByteToShort(pbAudioBuffer.byteArray, i) * pbVolumeScale).toInt()
            } else {
                0
            }
            val iT = micShort + pbShort
            ioutput = (iT * f).toInt()
            if (ioutput > DEF_MAX) {
                f = DEF_MAX / ioutput.toDouble()
                ioutput = DEF_MAX
            } else if (ioutput < DEF_MIN) {
                f = DEF_MIN / ioutput.toDouble()
                ioutput = DEF_MIN
            }
            if (f < 1) {
                f += (1 - f) / 32
            }
            AudioUtil.convertPcm16BitShortToByte(ioutput.toShort(), audioBuffer.byteArray, i)
        }
        audioBuffer.size = realSize
    }

}