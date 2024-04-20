package com.hezb.lib.live

/**
 * Project Name: AndroidScreenLive
 * File Name:    LiveClientListener
 *
 * Description: 直播客户端状态回调接口.
 *
 * @author  hezhubo
 * @date    2024年03月13日 22:24
 */
interface LiveClientListener {

    fun onStartFailure(e: Exception)

    fun onStopped()

    fun onEncodingError(e: Exception)

    fun onMuxerStartSuccess()

    /**
     * 仅边推流边录制时回调混合器启动失败状态
     */
    fun onMuxerStartFailure(e: Exception)

    fun onMuxerStopSuccess(outputPath: String)

    fun onMuxerStopFailure(e: Exception)

    fun onPusherStartSuccess()

    fun onPushingError(e: Exception)

}