package com.hezb.lib.live

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.HandlerThread
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.core.AudioCapturerThread
import com.hezb.lib.live.core.AudioEncoderThread
import com.hezb.lib.live.core.EncodeDataCollector
import com.hezb.lib.live.core.EncoderOutputThread
import com.hezb.lib.live.core.ScreenCapturer
import com.hezb.lib.live.core.TimestampHelper
import com.hezb.lib.live.core.VideoEncoder
import com.hezb.lib.live.core.VideoRenderHandler
import com.hezb.lib.live.filter.audio.BaseAudioFilter
import com.hezb.lib.live.filter.video.BaseVideoFilter
import com.hezb.lib.live.rtmp.RtmpPusher
import com.hezb.lib.live.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * Project Name: AndroidScreenLive
 * File Name:    LiveClient
 *
 * Description: 直播客户端.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:04
 */
class LiveClient : EncodeDataCollector {

    private var mAudioCapturerThread: AudioCapturerThread? = null
    private var mAudioEncoderThread: AudioEncoderThread? = null
    private var mAudioEncoderOutputThread: EncoderOutputThread? = null

    private var mScreenCapturer: ScreenCapturer? = null
    private var mVideoEncoder: VideoEncoder? = null
    private var mVideoRenderHandler: VideoRenderHandler? = null
    private var mVideoRenderThread: HandlerThread? = null
    private var mVideoEncoderOutputThread: EncoderOutputThread? = null

    private var mMediaMuxer: MediaMuxer? = null

    private var mRtmpPusher: RtmpPusher? = null

    private var videoOutputPath: String? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private val mMuxerLock = ReentrantLock(false)
    private var isRecording = false

    private var isStopping = false
    var isRunning = false
        private set

    var liveClientListener: LiveClientListener? = null

    private fun startCore(config: RecorderConfig, mediaProjection: MediaProjection) : Boolean {
        if (config.recordAudio) {
            val audioEncoder: AudioEncoderThread
            try {
                audioEncoder = AudioEncoderThread(config)
            } catch (e: Exception) {
                liveClientListener?.onStartFailure(e)
                return false
            }
            val audioCodec: MediaCodec
            try {
                audioCodec = audioEncoder.startEncoder()
            } catch (e: Exception) {
                liveClientListener?.onStartFailure(e)
                return false
            }
            val audioCapturer: AudioCapturerThread
            try {
                audioCapturer = AudioCapturerThread(audioEncoder, config, mediaProjection)
            } catch (e: Exception) {
                liveClientListener?.onStartFailure(e)
                return false
            }
            val audioEncoderOutputThread = EncoderOutputThread(audioCodec, this, false)
            mAudioEncoderThread = audioEncoder.apply { start() }
            mAudioCapturerThread = audioCapturer.apply { start() }
            mAudioEncoderOutputThread = audioEncoderOutputThread.apply { start() }
        }

        val videoEncoder: VideoEncoder
        try {
            videoEncoder = VideoEncoder(config)
        } catch (e: Exception) {
            onErrorStop()
            liveClientListener?.onStartFailure(e)
            return false
        }
        mVideoEncoder = videoEncoder
        val videoCodec: MediaCodec
        try {
            videoCodec = videoEncoder.startEncoder()
        } catch (e: Exception) {
            onErrorStop()
            liveClientListener?.onStartFailure(e)
            return false
        }
        val videoEncoderOutputThread = EncoderOutputThread(videoCodec, this, true)
        mVideoEncoderOutputThread = videoEncoderOutputThread.apply { start() }
        mVideoRenderThread = VideoRenderHandler.startRender {
            it.onErrorCallback = { renderException ->
                onError(renderException)
            }
            mVideoRenderHandler = it
            it.init(videoEncoder)
            val videoCapturer: ScreenCapturer
            try {
                videoCapturer = ScreenCapturer(mediaProjection, config.videoSize, config.virtualDisplayDpi)
            } catch (e: Exception) {
                onErrorStop()
                liveClientListener?.onStartFailure(e)
                return@startRender
            }
            videoCapturer.setOnFrameAvailableListener { surfaceTexture, surfaceTextureId ->
                it.onFrameAvailable(surfaceTexture, surfaceTextureId)
            }
            mScreenCapturer = videoCapturer
        }
        return true
    }

    /**
     * 启动录制
     */
    fun startRecord(config: RecorderConfig, outputPath: String?, mediaProjection: MediaProjection) {
        if (isRunning) {
            liveClientListener?.onStartFailure(Exception("Live client is running."))
            return
        }
        if (isStopping) {
            liveClientListener?.onStartFailure(Exception("Live client is stopping."))
            return
        }
        if (outputPath.isNullOrEmpty()) {
            liveClientListener?.onStartFailure(Exception("record output path(`${outputPath}`) error."))
            return
        }
        try {
            mMediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoOutputPath = outputPath
        } catch (e: Exception) {
            videoOutputPath = null
            liveClientListener?.onStartFailure(e)
            return
        }

        isRunning = startCore(config, mediaProjection)
    }

    /**
     * 启动推流
     */
    fun startPush(config: RecorderConfig, rtmpUrl: String?, mediaProjection: MediaProjection, outputPath: String? = null) {
        if (isRunning) {
            liveClientListener?.onStartFailure(Exception("Live client is running."))
            return
        }
        if (isStopping) {
            liveClientListener?.onStartFailure(Exception("Live client is stopping."))
            return
        }
        if (rtmpUrl.isNullOrEmpty() || !rtmpUrl.startsWith("rtmp")) {
            liveClientListener?.onStartFailure(Exception("rtmp push url(`${rtmpUrl}`) error."))
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
                liveClientListener?.onMuxerStartFailure(e)
            }
        }

        mRtmpPusher = RtmpPusher().also { rtmpPusher ->
            rtmpPusher.onWriteErrorCallback = { errorTimes ->
                liveClientListener?.onPushingError(Exception("rtmp write error!"))
                if (errorTimes == 200) {
                    onErrorStop()
                }
            }
            rtmpPusher.init(config)
        }

        MainScope().launch {
            val deferred = async(Dispatchers.IO) {
                return@async mRtmpPusher?.start(rtmpUrl) ?: false
            }
            val result = deferred.await()
            if (!result) {
                stopMuxer()
                liveClientListener?.onStartFailure(Exception("rtmp connect error!"))
                return@launch
            }

            if (startCore(config, mediaProjection)) {
                isRunning = true
                liveClientListener?.onPusherStartSuccess()
            }
        }

    }

    private fun stopMuxer() {
        mMediaMuxer?.let {
            mMuxerLock.lock()
            videoTrackIndex = -1
            audioTrackIndex = -1
            var muxerSuccess = true
            if (isRecording) {
                isRecording = false
                try {
                    it.stop()
                } catch (e: Exception) {
                    muxerSuccess = false
                    liveClientListener?.onMuxerStopFailure(e)
                }
            }
            try {
                it.release()
            } catch (e: Exception) {
                muxerSuccess = false
                liveClientListener?.onMuxerStopFailure(e)
            }
            if (muxerSuccess) {
                liveClientListener?.onMuxerStopSuccess(videoOutputPath!!)
            }
            mMediaMuxer = null
            videoOutputPath = null
            mMuxerLock.unlock()
        }
    }

    private fun stopPusher() {
        mRtmpPusher?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.stop()
                it.release()
            }
            mRtmpPusher = null
        }
    }

    fun stop() {
        if (isStopping || !isRunning) {
            return
        }
        MainScope().launch {
            isStopping = true
            stopMuxer()
            stopPusher()

            val deferred = async(Dispatchers.IO) {
                mAudioEncoderOutputThread?.quit()
                mAudioCapturerThread?.quit()
                mAudioEncoderThread?.quit()

                mVideoEncoderOutputThread?.quit()
                mScreenCapturer?.release()
                mVideoRenderHandler?.stop(mVideoRenderThread!!)
                try {
                    mVideoEncoder?.stopEncoder()
                } catch (e: Exception) {
                    LogUtil.e(msg = "video encoder stop error!", tr = e)
                }
            }
            deferred.await()
            mAudioCapturerThread = null
            mAudioEncoderThread = null
            mAudioEncoderOutputThread = null
            mScreenCapturer = null
            mVideoEncoder = null
            mVideoRenderHandler = null
            mVideoRenderThread = null
            mVideoEncoderOutputThread = null
            isStopping = false
            isRunning = false
            liveClientListener?.onStopped()
        }
    }

    /**
     * 暂停录制
     */
    fun pauseRecord() {
        TimestampHelper.pause()
    }

    /**
     * 恢复录制
     */
    fun resumeRecord() {
        TimestampHelper.resume()
    }

    /**
     * 动态设置码率
     *
     * @param bitrate
     */
    fun resetVideoBitrate(bitrate: Int) {
        mVideoEncoder?.setVideoBitrate(bitrate)
    }

    fun setVideoFilter(baseVideoFilter: BaseVideoFilter?) {
        mVideoRenderHandler?.setVideoFilter(baseVideoFilter)
    }

    fun setAudioFilter(baseAudioFilter: BaseAudioFilter?) {
        mAudioEncoderThread?.setAudioFilter(baseAudioFilter)
    }

    override fun addTrack(format: MediaFormat, isVideo: Boolean) {
        LogUtil.d(msg = "add track, isVideo = $isVideo, mediaFormat = $format")
        mRtmpPusher?.feedConfig(format, isVideo)

        mMediaMuxer?.let {
            try {
                if (isRecording) {
                    LogUtil.e(msg = "media muxer already started! videoTrackIndex = $videoTrackIndex, audioTrackIndex = $audioTrackIndex")
                    return
                }
                mMuxerLock.lock()
                if (isVideo) {
                    videoTrackIndex = it.addTrack(format)
                } else {
                    audioTrackIndex = it.addTrack(format)
                }

                if (videoTrackIndex == -1) {
                    mMuxerLock.unlock()
                    return
                }
                if (mAudioEncoderThread != null && audioTrackIndex == -1) {
                    mMuxerLock.unlock()
                    return
                }
                it.start()
                isRecording = true
                mMuxerLock.unlock()
                liveClientListener?.onMuxerStartSuccess()
            } catch (e: Exception) {
                if (mMuxerLock.isLocked) {
                    mMuxerLock.unlock()
                    return@let
                }
                liveClientListener?.onStartFailure(e)
            }
        }
    }

    override fun writeData(
        byteBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        isVideo: Boolean
    ) {
        mRtmpPusher?.feedData(byteBuffer, bufferInfo, isVideo)

        mMediaMuxer?.let {
            if (!isRecording) {
                return
            }
            if (isVideo) {
                if (videoTrackIndex == -1) {
                    return@let
                }
                mMuxerLock.lock()
                try {
                    it.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
                } catch (e: Exception) {
                    LogUtil.e(msg = "media muxer write video sample data error!", tr = e)
                }
                mMuxerLock.unlock()
            } else {
                if (audioTrackIndex == -1) {
                    return@let
                }
                mMuxerLock.lock()
                try {
                    it.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                } catch (e: Exception) {
                    LogUtil.e(msg = "media muxer write audio sample data error!", tr = e)
                }
                mMuxerLock.unlock()
            }
        }

    }

    override fun onError(e: Exception) {
        liveClientListener?.onEncodingError(e)
        onErrorStop()
    }

    private fun onErrorStop() {
        stopMuxer()
        stopPusher()

        val audioEncoderOutputThread = mAudioEncoderOutputThread
        val audioCapturerThread = mAudioCapturerThread
        val audioEncoderThread = mAudioEncoderThread
        val videoEncoderOutputThread = mVideoEncoderOutputThread
        val screenCapturer = mScreenCapturer
        val videoRenderHandler = mVideoRenderHandler
        val videoRenderThread = mVideoRenderThread
        val videoEncoder = mVideoEncoder
        mAudioCapturerThread = null
        mAudioEncoderThread = null
        mAudioEncoderOutputThread = null
        mScreenCapturer = null
        mVideoEncoder = null
        mVideoRenderHandler = null
        mVideoRenderThread = null
        mVideoEncoderOutputThread = null
        CoroutineScope(Dispatchers.IO).launch {
            audioEncoderOutputThread?.quit()
            audioCapturerThread?.quit()
            audioEncoderThread?.quit()

            videoEncoderOutputThread?.quit()
            screenCapturer?.release()
            if (videoRenderHandler != null && videoRenderThread != null) {
                videoRenderHandler.stop(videoRenderThread)
            }
            try {
                videoEncoder?.stopEncoder()
            } catch (e: Exception) {
                LogUtil.e(msg = "video encoder stop error!", tr = e)
            }
        }

        isRunning = false
        liveClientListener?.onStopped()
    }

}