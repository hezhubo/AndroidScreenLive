package com.hezb.live.recorder.rtmp

import android.media.MediaCodec
import android.media.MediaFormat
import com.hezb.live.recorder.config.RecorderConfig
import com.hezb.live.recorder.rtmp.packet.FlvData
import com.hezb.live.recorder.rtmp.packet.FlvHelper
import com.hezb.live.recorder.rtmp.packet.FlvMetaData
import com.hezb.live.recorder.rtmp.packet.pool.LruArrayPool
import com.hezb.live.recorder.util.LogUtil
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Project Name: AndroidScreenLive
 * File Name:    RtmpSender
 *
 * Description: rtmp推流器.
 *
 * @author  hezhubo
 * @date    2022年07月21日 13:01
 */
class RtmpPusher {

    /** flv数据包阻塞队列 */
    private val flvDataBlockingQueue: BlockingQueue<FlvData> = LinkedBlockingQueue()
    /** 队列中视频包数量 */
    private var videoFlvDataCount = 0

    /** 缓存池 */
    private val lruArrayPool = LruArrayPool(LruArrayPool.DEFAULT_SIZE * 2) // 8M缓存池

    private var mPusherThread: PusherThread? = null

    /** flv metadata */
    private var metaData: ByteArray? = null
    /** 底层推流对象的指针 */
    private var jniRtmpPointer: Long = 0

    private var videoStartTimestamp: Long = 0
    private var audioStartTimestamp: Long = 0

    var onWriteErrorCallback: OnWriteErrorCallback? = null

    fun init(config: RecorderConfig) {
        metaData = FlvMetaData(config).getMetaData()
    }

    /**
     * 开始推流
     * 会阻塞主线程，需要异步线程调用
     *
     * @param rtmpUrl
     * @return 是否成功
     */
    fun start(rtmpUrl: String): Boolean {
        if (rtmpUrl.isNotEmpty()) {
            try {
                // 底层返回rtmp的指针; jniRtmpPointer == 0 则创建失败
                jniRtmpPointer = RtmpClient.open(rtmpUrl, true)
            } catch (e: Throwable) {
                jniRtmpPointer = 0
                LogUtil.e(msg = "rtmp client open error!", tr = e)
            }
        }
        if (jniRtmpPointer == 0L) {
            LogUtil.e(msg = "rtmp open native error!")
            return false
        }
        LogUtil.i(msg = "rtmp open url : $rtmpUrl")
        metaData?.let {
            RtmpClient.write(
                jniRtmpPointer,
                it,
                it.size,
                FlvData.FLV_RTMP_PACKET_TYPE_INFO,
                0
            )
        }
        mPusherThread = PusherThread().apply { start() }
        return true
    }

    /**
     * 停止推流
     * 会阻塞主线程，需要异步线程调用
     */
    fun stop() {
        if (jniRtmpPointer != 0L) {
            try {
                val closeResult = RtmpClient.close(jniRtmpPointer)
                LogUtil.i(msg = "rtmp client close result = $closeResult")
                jniRtmpPointer = 0L
            } catch (e: Exception) {
                LogUtil.e(msg = "rtmp client close error!", tr = e)
            }
        }
        mPusherThread?.let {
            it.quit()
            try {
                it.join()
            } catch (e: InterruptedException) {
                LogUtil.e(msg = "pusher thread join error!", tr = e)
            }
        }
        mPusherThread = null

        flvDataBlockingQueue.clear()
        videoFlvDataCount = 0
        videoStartTimestamp = 0
        audioStartTimestamp = 0
    }

    fun release() {
        lruArrayPool.clearMemory()
        onWriteErrorCallback = null
    }

    fun feedConfig(mediaFormat: MediaFormat, isVideo: Boolean) {
        val flvData = if (isVideo) {
            FlvHelper.getVideoConfigFlvData(lruArrayPool, mediaFormat)
        } else {
            FlvHelper.getAudioConfigFlvData(lruArrayPool, mediaFormat)
        }
        if (flvData != null) {
            flvDataBlockingQueue.put(flvData)
        }
    }

    fun feedData(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean) {
        val flvData = if (isVideo) {
            videoFlvDataCount++
            if (videoStartTimestamp == 0L) {
                videoStartTimestamp = bufferInfo.presentationTimeUs / 1000
            }
            val timestamp = bufferInfo.presentationTimeUs / 1000 - videoStartTimestamp
            FlvHelper.getVideoFlvData(lruArrayPool, timestamp, encodedData)
        } else {
            if (audioStartTimestamp == 0L) {
                audioStartTimestamp = bufferInfo.presentationTimeUs / 1000
            }
            val timestamp = bufferInfo.presentationTimeUs / 1000 - audioStartTimestamp
            FlvHelper.getAudioFlvData(lruArrayPool, timestamp, encodedData)
        }
        flvDataBlockingQueue.put(flvData)
    }

    private inner class PusherThread : Thread() {
        private val MAX_VIDEO_QUEUE_LENGTH = 100

        private var isRunning = true

        /** 推流失败次数 */
        private var errorTimes = 0

        fun quit() {
            isRunning = false
            flvDataBlockingQueue.put(FlvData.obtain()) // 用于唤醒阻塞线程
        }

        override fun run() {
            while (isRunning) {
                val flvData = flvDataBlockingQueue.take() // 队列为空，阻塞
                if (!isRunning || jniRtmpPointer == 0L) {
                    break
                }
                if (flvData.flvTagType == FlvData.FLV_RTMP_PACKET_TYPE_VIDEO) {
                    videoFlvDataCount--
                    if (videoFlvDataCount > MAX_VIDEO_QUEUE_LENGTH && flvData.droppable) {
                        LogUtil.i(msg = "sender queue is crowded, abandon video")
                        flvData.byteBuffer?.let {
                            lruArrayPool.put(it)
                        }
                        flvData.recycle()
                        continue // 丢包
                    }
                }
                var writeResult = 0
                flvData.byteBuffer?.let {
                    writeResult = RtmpClient.write(
                        jniRtmpPointer,
                        it,
                        flvData.size,
                        flvData.flvTagType,
                        flvData.dts.toInt()
                    )
                    lruArrayPool.put(it)
                }
                flvData.recycle()
                if (writeResult != 0) {
                    errorTimes = 0
                } else {
                    ++errorTimes
                    LogUtil.i(msg = "rtmp write error : $errorTimes")
                    onWriteErrorCallback?.onError(errorTimes)
                }
            }
        }

    }

    /**
     * 推流错误回调（异步线程）
     */
    interface OnWriteErrorCallback {
        fun onError(errorTimes: Int)
    }

}