package com.hezb.live.recorder.core

/**
 * Project Name: AndroidScreenLive
 * File Name:    ErrorCode
 *
 * Description: 错误码.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
object ErrorCode {

    /** 正常 */
    const val NO_ERROR = 0
    /** 视频MediaCodec格式错误 */
    const val VIDEO_FORMAT_ERROR = -10000
    /** 视频启动编码器错误 */
    const val VIDEO_START_ENCODER_ERROR = -10001
    /** 虚拟界面创建错误 */
    const val VIRTUAL_DISPLAY_ERROR = -10002
    /** 音频MediaCodec格式错误 */
    const val AUDIO_FORMAT_ERROR = -20000
    /** AudioRecord创建错误 */
    const val AUDIO_RECORD_CREATE_ERROR = -20001
    /** AudioRecord状态错误 */
    const val AUDIO_RECORD_STATE_ERROR = -20002
    /** 音频启动录制未知错误 */
    const val AUDIO_START_UNKNOWN_ERROR = -20003
    /** 录屏客户端状态错误 */
    const val CLIENT_STATE_ERROR = -30000
    /** 录屏输出路径错误 */
    const val RECORD_OUTPUT_PATH_ERROR = -40000
    /** MediaMuxer创建错误 */
    const val MEDIA_MUXER_CREATE_ERROR = -40001
    /** MediaMuxer启动错误 */
    const val MEDIA_MUXER_START_ERROR = -40002
    /** MediaMuxer停止错误 */
    const val MEDIA_MUXER_STOP_ERROR = -40003
    /** MediaMuxer释放错误 */
    const val MEDIA_MUXER_RELEASE_ERROR = -40004
    /** Rtmp推流地址错误 */
    const val RTMP_PUSH_URL_ERROR = -50000
    /** Rtmp推流器启动错误 */
    const val RTMP_PUSH_START_ERROR = -50001

}