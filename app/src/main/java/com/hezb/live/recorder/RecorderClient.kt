package com.hezb.live.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import com.hezb.live.recorder.core.*
import com.hezb.live.recorder.filter.audio.BaseAudioFilter
import com.hezb.live.recorder.filter.video.BaseVideoFilter
import com.hezb.live.recorder.model.Size
import com.hezb.live.recorder.rtmp.RtmpPusher
import com.hezb.live.recorder.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecordClient
 *
 * Description: 录制/推流客户端.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:32
 */
class RecorderClient : BaseCore.OnErrorCallback, RtmpPusher.OnWriteErrorCallback {

    companion object {
        /** 初始状态 */
        const val STATE_INIT = 0
        /** 预处理完成 */
        const val STATE_PREPARED = 1
        /** 运行（录制）中 */
        const val STATE_RUNNING = 2
        /** 正在停止 */
        const val STATE_STOPPING = 3
        /** 已停止 */
        const val STATE_STOPPED = 4
        /** 释放资源 */
        const val STATE_RELEASE = 5
    }

    var currentState: Int = STATE_INIT
        private set

    var onStateChangeCallback: OnStateChangeCallback? = null

    private var mRecorderConfig: RecorderConfig? = null

    private var mScreenCore: ScreenCore? = null
    private var mAudioCore: AudioCore? = null

    private var mMediaMuxer: MediaMuxer? = null

    private var mRtmpPusher: RtmpPusher? = null

    private var videoOutputPath: String? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    @Volatile
    private var isRecording = false

    private val mDataCollector: DataCollector = object : DataCollector {

        override fun addTrack(format: MediaFormat, isVideo: Boolean) {
            LogUtil.d(msg = "add track, isVideo = $isVideo, mediaFormat = $format")
            mRtmpPusher?.feedConfig(format, isVideo)

            mMediaMuxer?.let {
                try {
                    if (isRecording) {
                        LogUtil.e(msg = "media muxer already started! videoTrackIndex = $videoTrackIndex, audioTrackIndex = $audioTrackIndex")
                        return
                    }
                    if (isVideo) {
                        videoTrackIndex = it.addTrack(format)
                    } else {
                        audioTrackIndex = it.addTrack(format)
                    }

                    if (videoTrackIndex == -1) {
                        return
                    }
                    if (mAudioCore != null && audioTrackIndex == -1) {
                        return
                    }
                    it.start()
                    isRecording = true
                    onStateChangeCallback?.onMuxerStartSuccess()
                } catch (e: Exception) {
                    LogUtil.e(msg = "media muxer start error!", tr = e)
                    onStateChangeCallback?.onStartFailure(ErrorCode.MEDIA_MUXER_START_ERROR)
                }
            }
        }

        override fun writeData(
            byteBuffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
            isVideo: Boolean
        ) {
            mRtmpPusher?.feedData(byteBuffer, bufferInfo, isVideo)

            if (isRecording) {
                mMediaMuxer?.let {
                    if (isVideo) {
                        if (videoTrackIndex == -1) {
                            return@let
                        }
                        try {
                            it.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
                        } catch (e: Exception) {
                            LogUtil.e(msg = "media muxer write video sample data error!", tr = e)
                        }
                    } else {
                        if (audioTrackIndex == -1) {
                            return@let
                        }
                        try {
                            it.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                        } catch (e: Exception) {
                            LogUtil.e(msg = "media muxer write audio sample data error!", tr = e)
                        }
                    }
                }
            }
        }

    }

    /**
     * 预处理
     * 初始化一些配置项
     *
     * @param config
     * @param mediaProjection
     */
    fun prepare(config: RecorderConfig, mediaProjection: MediaProjection): Int {
        if (currentState != STATE_INIT) {
            return ErrorCode.CLIENT_STATE_ERROR
        }
        mRecorderConfig = config
        mScreenCore = ScreenCore(mediaProjection)
        var error = mScreenCore?.prepare(config) ?: ErrorCode.CLIENT_STATE_ERROR
        if (error != ErrorCode.NO_ERROR) {
            return error
        }
        if (config.recordAudio) {
            mAudioCore = AudioCore(mediaProjection)
            error = mAudioCore?.prepare(config) ?: ErrorCode.CLIENT_STATE_ERROR
            if (error != ErrorCode.NO_ERROR) {
                return error
            }
        }
        currentState = STATE_PREPARED
        return ErrorCode.NO_ERROR
    }

    /**
     * 启动录制
     *
     * @param outputPath 录制输出路径
     */
    fun startRecord(outputPath: String?) {
        if (currentState != STATE_PREPARED && currentState != STATE_STOPPED || mRtmpPusher != null) {
            onStateChangeCallback?.onStartFailure(ErrorCode.CLIENT_STATE_ERROR)
            return
        }
        if (outputPath.isNullOrEmpty()) {
            onStateChangeCallback?.onStartFailure(ErrorCode.RECORD_OUTPUT_PATH_ERROR)
            return
        }
        try {
            mMediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoOutputPath = outputPath
        } catch (e: Exception) {
            LogUtil.e(msg = "mediaMuxer create error!", tr = e)
            videoOutputPath = null
            onStateChangeCallback?.onStartFailure(ErrorCode.MEDIA_MUXER_CREATE_ERROR)
            return
        }
        var error = mScreenCore?.start(mDataCollector) ?: ErrorCode.CLIENT_STATE_ERROR
        if (error != ErrorCode.NO_ERROR) {
            onStateChangeCallback?.onStartFailure(error)
            return
        }
        mAudioCore?.let {
            error = it.start(mDataCollector)
            if (error != ErrorCode.NO_ERROR) {
                onStateChangeCallback?.onStartFailure(error)
                return
            }
        }

        currentState = STATE_RUNNING
    }

    /**
     * 开始推流
     *
     * @param rtmpUrl
     * @param outputPath 录制输出路径，传入则一边推流一边录制
     */
    fun startPush(rtmpUrl: String?, outputPath: String? = null) {
        if (currentState != STATE_PREPARED && currentState != STATE_STOPPED) {
            onStateChangeCallback?.onStartFailure(ErrorCode.CLIENT_STATE_ERROR)
            return
        }
        if (rtmpUrl.isNullOrEmpty()) {
            onStateChangeCallback?.onStartFailure(ErrorCode.RTMP_PUSH_URL_ERROR)
            return
        }
        if (!outputPath.isNullOrEmpty()) {
            try {
                mMediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                videoOutputPath = outputPath
            } catch (e: Exception) {
                // 混合器无法创建也继续往下执行推流器代码
                LogUtil.e(msg = "mediaMuxer create error!", tr = e)
                mMediaMuxer = null
                videoOutputPath = null
                onStateChangeCallback?.onStartFailure(ErrorCode.MEDIA_MUXER_CREATE_ERROR)
            }
        }
        if (mRtmpPusher == null) {
            mRtmpPusher = RtmpPusher().also { rtmpPusher ->
                mRecorderConfig?.let {
                    rtmpPusher.init(it)
                }
            }
        }
        GlobalScope.launch(Dispatchers.Default) {
            val deferred = GlobalScope.async(Dispatchers.IO) {
                return@async mRtmpPusher?.start(rtmpUrl) ?: false
            }
            val result = deferred.await()
            if (!result) {
                onStateChangeCallback?.onStartFailure(ErrorCode.RTMP_PUSH_START_ERROR)
                return@launch
            }

            var error = mScreenCore?.start(mDataCollector) ?: ErrorCode.CLIENT_STATE_ERROR
            if (error != ErrorCode.NO_ERROR) {
                onStateChangeCallback?.onStartFailure(error)
                return@launch
            }
            mAudioCore?.let {
                error = it.start(mDataCollector)
                if (error != ErrorCode.NO_ERROR) {
                    onStateChangeCallback?.onStartFailure(error)
                    return@launch
                }
            }

            onStateChangeCallback?.onPusherStartSuccess()

            currentState = STATE_RUNNING
        }
    }

    fun stop(autoRelease: Boolean = false) {
        if (currentState == STATE_STOPPING) {
            return
        }
        if (currentState != STATE_RUNNING) {
            onStateChangeCallback?.onStopFailure(ErrorCode.CLIENT_STATE_ERROR)
            return
        }
        isRecording = false
        currentState = STATE_STOPPING

        GlobalScope.launch(Dispatchers.Default) {
            val deferred = GlobalScope.async(Dispatchers.IO) {
                mRtmpPusher?.stop()
                mAudioCore?.stop()
                mScreenCore?.stop()
            }
            deferred.await()
            currentState = STATE_STOPPED
            if (mRtmpPusher != null) {
                onStateChangeCallback?.onPusherStop()
            }

            var error = ErrorCode.NO_ERROR
            mMediaMuxer?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    LogUtil.e(msg = "media muxer stop error!", tr = e)
                    error = ErrorCode.MEDIA_MUXER_STOP_ERROR
                }
                try {
                    it.release()
                } catch (e: Exception) {
                    LogUtil.e(msg = "media muxer release error!", tr = e)
                    error = ErrorCode.MEDIA_MUXER_RELEASE_ERROR
                }
            }
            val outputSuccess = error == ErrorCode.NO_ERROR// 标记视频混合输出成功
            videoTrackIndex = -1
            audioTrackIndex = -1
            mMediaMuxer = null

            if (outputSuccess && videoOutputPath != null) {
                onStateChangeCallback?.onMuxerStopSuccess(videoOutputPath!!)
            } else {
                if (error != ErrorCode.NO_ERROR && mRtmpPusher == null) {
                    onStateChangeCallback?.onStopFailure(error)
                }
            }
            if (autoRelease) {
                release()
            }
        }
    }

    fun release() {
        if (currentState == STATE_RUNNING) {
            stop(true)
            return
        }
        mRtmpPusher?.release()
        mRtmpPusher = null
        mScreenCore?.release()
        mScreenCore = null
        mAudioCore?.release()
        mAudioCore = null
        currentState = STATE_RELEASE
    }

    /**
     * 暂停录制
     */
    fun pauseRecord() {
        mScreenCore?.pause()
        mAudioCore?.pause()
    }

    /**
     * 恢复录制
     */
    fun resumeRecord() {
        mScreenCore?.resume()
        mAudioCore?.resume()
    }

    override fun onError(error: String) {
        GlobalScope.launch(Dispatchers.Main) {
            // 回到主线程停止client
            if (isRecording) {
                stop()
            }
        }
    }

    override fun onError(errorTimes: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            onStateChangeCallback?.onPusherWriteError(errorTimes)
        }
    }

    fun getVideoSize(): Size {
        return mRecorderConfig?.videoSize ?: Size(1, 1)
    }

    fun resetVideoBitrate(bitrate: Int) {
        mRecorderConfig?.let {
            if (it.videoBitrate != bitrate) {
                it.videoBitrate = bitrate
                mScreenCore?.resetVideoBitrate(bitrate)
            }
        }
    }

    fun setVideoFilter(baseVideoFilter: BaseVideoFilter?) {
        mScreenCore?.setVideoFilter(baseVideoFilter)
    }

    fun setAudioFilter(baseAudioFilter: BaseAudioFilter?) {
        mAudioCore?.setAudioFilter(baseAudioFilter)
    }

    /**
     * 状态回调
     */
    interface OnStateChangeCallback {

        fun onStartFailure(error: Int)

        fun onStopFailure(error: Int)

        fun onMuxerStartSuccess()

        fun onMuxerStopSuccess(outputPath: String)

        fun onPusherStartSuccess()

        fun onPusherStop()

        fun onPusherWriteError(errorTimes: Int)

    }

}