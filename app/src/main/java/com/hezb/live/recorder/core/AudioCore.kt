package com.hezb.live.recorder.core

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import com.hezb.live.recorder.RecorderConfig
import com.hezb.live.recorder.filter.audio.BaseAudioFilter
import com.hezb.live.recorder.model.AudioBuffer
import com.hezb.live.recorder.util.LogUtil
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Project Name: AndroidScreenLive
 * File Name:    AudioCore
 *
 * Description: 音频录制核心.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
class AudioCore(private var mediaProjection: MediaProjection? = null) : BaseCore() {

    private var mEncodeFormat: MediaFormat? = null
    private var mEncoder: MediaCodec? = null
    private var mAudioRecord: AudioRecord? = null
    private var mPlaybackAudioRecord: AudioRecord? = null
    private var mRecorderThread: AudioRecordThread? = null
    private var mEncoderInputThread: AudioEncodeInputThread? = null
    private var mEncoderOutputThread: AudioEncodeOutputThread? = null

    /** 滤镜锁 */
    private val mFilterLock = ReentrantLock(false)
    /** 滤镜 */
    private var mAudioFilter: BaseAudioFilter? = null

    /** 音频数据缓存池(复用ByteArray，避免多次申请内存) */
    private val audioBufferPool = HashSet<AudioBuffer>()
    /** 音频PCM数据阻塞队列 */
    private val pcmAudioBufferQueue: BlockingQueue<AudioBuffer> = LinkedBlockingQueue()
    private val pcmBufferSize = 2048 // PCM数据缓存大小 2k
    private val mineType = MediaFormat.MIMETYPE_AUDIO_AAC // 编码格式

    @SuppressLint("MissingPermission")
    override fun prepare(config: RecorderConfig): Int {
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

        if (!createAudioCodec(config)) {
            return ErrorCode.AUDIO_FORMAT_ERROR
        }

        if (config.audioSourceType != RecorderConfig.AUDIO_SOURCE_TYPE_PLAYBACK
            || !RecorderConfig.supportRecordPlaybackAudio()) {
            try {
                mAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    config.audioSampleRate,
                    audioChannel,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2
                )
            } catch (e: Exception) {
                LogUtil.e(msg = "can't create audio recorder!", tr = e)
                return ErrorCode.AUDIO_RECORD_CREATE_ERROR
            }
            if (AudioRecord.STATE_INITIALIZED != mAudioRecord?.state) {
                return ErrorCode.AUDIO_RECORD_STATE_ERROR
            }
            if (config.audioSourceType == RecorderConfig.AUDIO_SOURCE_TYPE_MIC) {
                return ErrorCode.NO_ERROR
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjection?.let {
                val apccBuilder = AudioPlaybackCaptureConfiguration.Builder(it)
                apccBuilder.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                apccBuilder.addMatchingUsage(AudioAttributes.USAGE_GAME) // 游戏类型声音
                apccBuilder.addMatchingUsage(AudioAttributes.USAGE_MEDIA) // 媒体类型声音
                val audioRecorderBuilder = AudioRecord.Builder()
//                    .setAudioSource(MediaRecorder.AudioSource.DEFAULT) // 注意！无法同时设置音频来源
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(config.audioSampleRate)
                            .setChannelMask(audioChannel)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
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

        return ErrorCode.NO_ERROR
    }

    private fun createAudioCodec(config: RecorderConfig): Boolean {
        mEncodeFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mineType)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            ) // AAC编码规格 LC：低复杂度规格
            setInteger(MediaFormat.KEY_SAMPLE_RATE, config.audioSampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.audioChannelCount)
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
        }
        LogUtil.i(msg = "audio encode format : ${mEncodeFormat.toString()}")
        return try {
            mEncoder = MediaCodec.createEncoderByType(mineType)
            true
        } catch (e: Exception) {
            LogUtil.e(msg = "can't create audio encoder!", tr = e)
            false
        }
    }

    override fun start(collector: DataCollector): Int {
        pauseTimestamp = 0L
        pauseDuration = 0L
        isPause = false

        return try {
            // 启动编码器
            if (mEncoder == null) {
                mEncoder = MediaCodec.createEncoderByType(mineType)
            }
            mEncoder?.apply {
                configure(mEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            // 启动编码输入线程
            mEncoderInputThread = AudioEncodeInputThread().apply { start() }
            // 启动编码输出线程
            mEncoderOutputThread = AudioEncodeOutputThread(collector).apply { start() }
            // 开始录音
            mAudioRecord?.startRecording()
            mPlaybackAudioRecord?.startRecording()
            mRecorderThread = AudioRecordThread().apply { start() }

            ErrorCode.NO_ERROR
        } catch (e: Exception) {
            LogUtil.e(msg = "audio core start error!", tr = e)
            ErrorCode.AUDIO_START_UNKNOWN_ERROR
        }
    }

    /**
     * 需IO线程调用，会阻塞调用线程
     */
    override fun stop() {
        mEncoderInputThread?.let {
            it.quit()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "audio encoder input thread join error!", tr = e)
            }
        }
        mEncoderInputThread = null

        mEncoderOutputThread?.let {
            it.quit()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "audio encoder output thread join error!", tr = e)
            }
        }
        mEncoderOutputThread = null

        mRecorderThread?.let {
            it.quit()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "audio recorder thread join error!", tr = e)
            }
        }
        mRecorderThread = null

        mEncoder?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                LogUtil.e(msg = "audio encoder stop error!", tr = e)
            }
        }
        mEncoder = null

        mAudioRecord?.stop()
        mPlaybackAudioRecord?.stop()

        audioBufferPool.clear()
        pcmAudioBufferQueue.clear()
    }

    override fun release() {
        mAudioRecord?.release()
        mAudioRecord = null
        mPlaybackAudioRecord?.release()
        mPlaybackAudioRecord = null
        mediaProjection = null
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

    @Synchronized
    private fun getAudioBuffer(): AudioBuffer {
        return if (audioBufferPool.isNotEmpty()) {
            val tempBuffer = audioBufferPool.iterator().next()
            audioBufferPool.remove(tempBuffer)
            tempBuffer
        } else {
            AudioBuffer(ByteArray(pcmBufferSize))
        }
    }

    @Synchronized
    private fun putAudioBufferToPool(audioBuffer: AudioBuffer) {
        if (audioBufferPool.size >= 10) {
            return
        }
        audioBufferPool.add(audioBuffer)
    }

    /**
     * 录音线程（生产者）
     */
    private inner class AudioRecordThread : Thread() {
        private var isRunning = true

        fun quit() {
            isRunning = false
        }

        override fun run() {
            var audioBuffer: AudioBuffer
            var size: Int
            var pbAudioBuffer: AudioBuffer? = null
            var pbSize: Int
            while (isRunning) {
                audioBuffer = getAudioBuffer()
                size = mAudioRecord?.read(audioBuffer.byteArray, 0, audioBuffer.byteArray.size) ?: 0

                pbSize = 0
                mPlaybackAudioRecord?.let {
                    pbAudioBuffer = getAudioBuffer()
                    pbSize = mPlaybackAudioRecord?.read(
                        pbAudioBuffer!!.byteArray,
                        0,
                        pbAudioBuffer!!.byteArray.size
                    ) ?: 0
                }

                if (isRunning && (size > 0 || pbSize > 0) && !isPause) {
                    audioBuffer.size = size
                    pbAudioBuffer?.let {
                        it.size = pbSize
                        mixPcm(audioBuffer, it)
                        putAudioBufferToPool(it)
                        pbAudioBuffer = null
                    }
                    pcmAudioBufferQueue.put(audioBuffer)
                } else {
                    putAudioBufferToPool(audioBuffer) // 添加到缓存池
                    pbAudioBuffer?.let {
                        putAudioBufferToPool(it)
                        pbAudioBuffer = null
                    }
                }
            }
        }

        private val DEF_MAX = 32767
        private val DEF_MIN = -32768
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
            var temp = 0
            var temp2 = 0
            val realSize = Math.max(audioBuffer.size, pbAudioBuffer.size)
            for (i in 0 until realSize) {
                val micByte = if (audioBuffer.size > i) {
                    audioBuffer.byteArray[i]
                } else {
                    0
                }
                val pbByte = if (pbAudioBuffer.size > i) {
                    (pbAudioBuffer.byteArray[i] * pbVolumeScale).toInt()
                } else {
                    0
                }
                val iT = micByte + pbByte
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
                audioBuffer.byteArray[i] = ioutput.toByte()
                if (ioutput == 0) {
                    temp++
                    if (micByte == 0.toByte() && pbByte == 0) {
                        temp2++
                    }
                }
            }
            audioBuffer.size = realSize
        }
    }

    /**
     * 编码输入线程（消费者）
     */
    private inner class AudioEncodeInputThread: Thread() {
        private var isRunning = true

        fun quit() {
            isRunning = false
            pcmAudioBufferQueue.put(AudioBuffer(ByteArray(0))) // 用于唤醒阻塞线程
        }

        override fun run() {
            while (isRunning) {
                var audioBuffer = pcmAudioBufferQueue.take() // 队列为空，阻塞
                if (!isRunning) {
                    break
                }
                val isFilterLocked = lockAudioFilter()
                if (isFilterLocked) {
                    mAudioFilter?.let {
                        val targetAudioBuffer = getAudioBuffer()
                        it.onFilter(audioBuffer.byteArray, targetAudioBuffer.byteArray, audioBuffer.size)
                        targetAudioBuffer.size = audioBuffer.size
                        val tempAudioBuffer = audioBuffer
                        audioBuffer = targetAudioBuffer
                        putAudioBufferToPool(tempAudioBuffer)
                    }
                    unlockAudioFilter()
                }

                val pts = getPresentationTimeUs() // 先计算时间戳，减少dequeueInputBuffer耗时误差

                // 写入编码器
                mEncoder?.let { encoder ->
                    var eibIndex = -1
                    try {
                        eibIndex = encoder.dequeueInputBuffer(-1) // 无限等待
                    } catch (e: Exception) {
                        LogUtil.e(msg = "audio encoder dequeue input buffer error!", tr = e)
                    }
                    if (!isRunning) {
                        return
                    }
                    if (eibIndex >= 0) {
                        val encoderInputBuffer = encoder.inputBuffers[eibIndex] ?: return@let
                        encoderInputBuffer.position(0)
                        encoderInputBuffer.put(audioBuffer.byteArray, 0, audioBuffer.size)
                        encoder.queueInputBuffer(
                            eibIndex,
                            0,
                            audioBuffer.size,
                            pts, // 微妙
                            0
                        )
                    } else {
                        LogUtil.d(msg = "audio encoder dequeue input buffer < 0")
                    }
                }

                putAudioBufferToPool(audioBuffer)
            }
        }

        /**
         * 获取时间戳（单位：微秒）
         */
        private fun getPresentationTimeUs(): Long {
            val millis = if (pauseDuration > 0) {
                SystemClock.uptimeMillis() - pauseDuration
            } else {
                SystemClock.uptimeMillis()
            }
            return millis * 1000
        }

        /**
         * @return true if filter locked & filter!=null
         */
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
            } catch (e: InterruptedException) {}
            return false
        }

        private fun unlockAudioFilter() {
            mFilterLock.unlock()
        }
    }

    /**
     * 编码输出线程
     */
    private inner class AudioEncodeOutputThread(var collector: DataCollector?) : Thread() {
        private var isRunning = true

        fun quit() {
            isRunning = false
            collector = null
        }

        override fun run() {
            val outputBufferInfo = MediaCodec.BufferInfo()
            while (isRunning) {
                mEncoder?.let { encoder ->
                    val eobIndex = try {
                        encoder.dequeueOutputBuffer(outputBufferInfo, 3000)
                    } catch (e: Exception) {
                        LogUtil.e(msg = "dequeue audio output buffer error!", tr = e)
                        if (isRunning) {
                            mOnErrorCallback?.onError("dequeue audio output buffer error!")
                        }
                        quit() // 出错直接停止运行线程
                        return
                    }
                    if (!isRunning) {
                        return
                    }
                    collector?.let {
                        when (eobIndex) {
                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                it.addTrack(encoder.outputFormat, false)
                            }
                            else -> {
                                if (outputBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && outputBufferInfo.size != 0) {
                                    try {
                                        val encodedData = encoder.getOutputBuffer(eobIndex) ?: return@let
                                        encodedData.position(outputBufferInfo.offset)
                                        encodedData.limit(outputBufferInfo.offset + outputBufferInfo.size)
                                        it.writeData(encodedData, outputBufferInfo, false)
                                    } catch (e : Exception) {
                                        LogUtil.e(msg = "audio write data error!", tr = e)
                                    }
                                }
                                encoder.releaseOutputBuffer(eobIndex, false)
                            }
                        }
                    }
                } ?: break
            }
        }
    }

}