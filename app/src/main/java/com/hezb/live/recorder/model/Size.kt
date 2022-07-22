package com.hezb.live.recorder.model

/**
 * Project Name: AndroidScreenLive
 * File Name:    Size
 *
 * Description: 视频尺寸（分辨率）.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
class Size(val width: Int, val height: Int) {

    override fun toString(): String {
        return "${width}x${height}"
    }

}