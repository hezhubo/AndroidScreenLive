package com.hezb.lib.live.rtmp

import android.media.MediaCodec
import android.media.MediaFormat
import com.hezb.lib.rtmp.RtmpClient
import com.hezb.lib.live.config.RecorderConfig
import com.hezb.lib.live.rtmp.packet.FlvData
import com.hezb.lib.live.rtmp.packet.FlvHelper
import com.hezb.lib.live.rtmp.packet.FlvMetaData
import com.hezb.lib.live.rtmp.packet.pool.LruArrayPool
import com.hezb.lib.live.util.LogUtil
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Project Name: AndroidScreenLive
 * File Name:    RtmpSender
 *
 * Description: rtmp推流器. TODO 丢帧，自动重连，抗弱网（弱网判断，CBR编码模式下固定的比特率，若低于某值就降码率帧率分辨率等）
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
    /** rtmp客户端 */
    private var rtmpClient: RtmpClient? = null

    private var videoStartTimestamp: Long = 0
    private var audioStartTimestamp: Long = 0

    /** 推流错误回调 */
    var onWriteErrorCallback: ((errorTimes: Int) -> Unit)? = null

    fun init(config: RecorderConfig) {
        metaData = FlvMetaData(config).getMetaData()
        rtmpClient = RtmpClient()
    }

    /**
     * 开始推流
     * 会阻塞主线程，需要异步线程调用
     *
     * @param rtmpUrl
     * @return 是否成功
     */
    fun start(rtmpUrl: String): Boolean {
        var connected = false;
        if (rtmpUrl.isNotEmpty()) {
            try {
                connected = rtmpClient?.connect(rtmpUrl, true) ?: false
            } catch (e: Throwable) {
                LogUtil.e(msg = "rtmp client open error!", tr = e)
            }
        }
        if (!connected) {
            LogUtil.e(msg = "rtmp open native error!")
            return false
        }
        LogUtil.i(msg = "rtmp open url : $rtmpUrl")
        metaData?.let {
            rtmpClient!!.write(
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
        rtmpClient?.let {
            try {
                it.close()
                LogUtil.i(msg = "rtmp client close!")
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
        private val MAX_VIDEO_QUEUE_LENGTH = 100 // TODO 队列优化 帧率 关键帧间隔 25fps * 2s 必须大于 ，丢帧要丢一整个GOP

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
                if (!isRunning || rtmpClient?.isConnected != true) {
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
                var writeSuccess = false
                flvData.byteBuffer?.let {
                    writeSuccess = rtmpClient!!.write(
                        it,
                        flvData.size,
                        flvData.flvTagType,
                        flvData.dts.toInt()
                    )
                    lruArrayPool.put(it)
                }
                flvData.recycle()
                if (writeSuccess) {
                    errorTimes = 0
                } else {
                    ++errorTimes
                    LogUtil.i(msg = "rtmp write error : $errorTimes")
                    onWriteErrorCallback?.invoke(errorTimes)
                }
            }
        }

    }

}