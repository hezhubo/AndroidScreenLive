package com.hezb.lib.live.core

import android.media.MediaCodec
import android.media.MediaFormat
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.filter.audio.BaseAudioFilter
import com.hezb.lib.live.model.AudioBuffer
import com.hezb.lib.live.util.LogUtil
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Project Name: AndroidScreenLive
 * File Name:    AudioEncoderThread
 *
 * Description: 音频编码器线程.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:05
 */
class AudioEncoderThread(private val config: RecorderConfig) : Thread("AudioEncoderThread") {

    private val mEncodeFormat: MediaFormat
    private val mEncoder: MediaCodec

    /** 音频数据缓存池(复用ByteArray，避免多次申请内存) */
    private val audioBufferPool = ArrayList<AudioBuffer>()
    /** 音频PCM数据阻塞队列 */
    private val pcmAudioBufferQueue: BlockingQueue<AudioBuffer> = LinkedBlockingQueue()
    private val pcmBufferSize = 2048 // PCM数据缓存大小 2k
    private val mineType = MediaFormat.MIMETYPE_AUDIO_AAC // 编码格式

    private var isRunning = true

    /** 滤镜锁 */
    private val mFilterLock = ReentrantLock(false)
    /** 滤镜 */
    private var mAudioFilter: BaseAudioFilter? = null
    /** 使用中的滤镜 */
    private var mInnerAudioFilter: BaseAudioFilter? = null

    init {
        mEncodeFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mineType)
            if (config.audioCodecAACProfile != 0) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, config.audioCodecAACProfile)
            }
            setInteger(MediaFormat.KEY_SAMPLE_RATE, config.audioSampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.audioChannelCount)
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
        }
        LogUtil.i(msg = "audio encode format : $mEncodeFormat")
        mEncoder = if (!config.audioCodecName.isNullOrEmpty()) {
            MediaCodec.createByCodecName(config.audioCodecName!!)
        } else{
            MediaCodec.createEncoderByType(mineType)
        }
    }

    fun startEncoder(): MediaCodec {
        mEncoder.configure(mEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mEncoder.start()
        return mEncoder
    }

    /**
     * 设置滤镜
     *
     * @param audioFilter
     */
    fun setAudioFilter(audioFilter: BaseAudioFilter?) {
        mFilterLock.lock()
        mAudioFilter = audioFilter
        mFilterLock.unlock()
    }

    private fun lockAudioFilter(): Boolean {
        try {
            val locked: Boolean = mFilterLock.tryLock(3L, TimeUnit.MILLISECONDS)
            return if (locked) {
                if (mAudioFilter != null) {
                    true
                } else {
                    mFilterLock.unlock()
                    false
                }
            } else {
                false
            }
        } catch (_: InterruptedException) {}
        return false
    }

    private fun unlockAudioFilter() {
        mFilterLock.unlock()
    }

    @Synchronized
    fun getAudioBuffer(): AudioBuffer {
        for (audioBuffer in audioBufferPool) {
            if (audioBuffer.free) {
                audioBuffer.free = false // 标记为正在被使用
                return audioBuffer
            }
        }
        if (audioBufferPool.size < 10) {
            val newAudioBuffer = AudioBuffer(ByteArray(pcmBufferSize))
            audioBufferPool.add(newAudioBuffer)
            return newAudioBuffer
        }
        return AudioBuffer(ByteArray(pcmBufferSize))
    }

    fun feedPCMAudioBuffer(audioBuffer: AudioBuffer) {
        if (isRunning) {
            pcmAudioBufferQueue.put(audioBuffer)
        }
    }

    fun quit() {
        isRunning = false
        pcmAudioBufferQueue.put(AudioBuffer(ByteArray(0))) // 用于唤醒阻塞线程
        try {
            join()
        } catch (e: InterruptedException) {
            LogUtil.e(msg = "audio encoder thread join error!", tr = e)
        }
    }

    override fun run() {
        while (isRunning) {
            var audioBuffer = pcmAudioBufferQueue.take() // 队列为空，阻塞
            if (!isRunning) {
                break
            }

            if (lockAudioFilter()) {
                if (mAudioFilter != mInnerAudioFilter) {
                    mInnerAudioFilter?.onDestroy()
                    mInnerAudioFilter = mAudioFilter
                    mInnerAudioFilter?.init(config.audioSampleRate, config.audioChannelCount)
                }
                mAudioFilter?.let {
                    val targetAudioBuffer = getAudioBuffer()
                    val targetSize = it.onFilter(audioBuffer.byteArray, audioBuffer.size, targetAudioBuffer.byteArray)
                    targetAudioBuffer.size = targetSize
                    val tempAudioBuffer = audioBuffer
                    audioBuffer = targetAudioBuffer
                    tempAudioBuffer.free = true
                }
                unlockAudioFilter()
                if (audioBuffer.size == 0) {
                    continue
                }
            }

            val pts = TimestampHelper.getPresentationTimeUs() // 先计算时间戳，减少dequeueInputBuffer耗时误差

            // 写入编码器
            var eibIndex = -1
            try {
                eibIndex = mEncoder.dequeueInputBuffer(-1) // 无限等待
            } catch (e: Exception) {
                LogUtil.e(msg = "audio encoder dequeue input buffer error!", tr = e)
            }
            if (!isRunning) {
                break
            }
            if (eibIndex >= 0) {
                val encoderInputBuffer = mEncoder.inputBuffers[eibIndex] ?: return
                encoderInputBuffer.position(0)
                encoderInputBuffer.put(audioBuffer.byteArray, 0, audioBuffer.size)
                mEncoder.queueInputBuffer(
                    eibIndex,
                    0,
                    audioBuffer.size,
                    pts, // 微秒
                    0
                )
            } else {
                LogUtil.d(msg = "audio encoder dequeue input buffer < 0")
            }

            audioBuffer.free = true
        }
        // 销毁滤镜
        mInnerAudioFilter?.let { filter ->
            mFilterLock.lock()
            filter.onDestroy()
            mFilterLock.unlock()
        }

        try {
            mEncoder.stop()
            mEncoder.release()
        } catch (e: Exception) {
            LogUtil.e(msg = "audio encoder stop error!", tr = e)
        }

        audioBufferPool.clear()
        pcmAudioBufferQueue.clear()
    }

}