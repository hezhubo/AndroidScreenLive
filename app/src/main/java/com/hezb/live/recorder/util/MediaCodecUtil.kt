package com.hezb.live.recorder.util

import android.media.MediaCodecInfo
import java.lang.reflect.Modifier

/**
 * Project Name: AndroidScreenLive
 * File Name:    MediaCodecUtil
 *
 * Description: MediaCodec工具类.
 *
 * @author  hezhubo
 * @date    2022年09月07日 11:12
 */
object MediaCodecUtil {

    private val codecProfileLevelMap = HashMap<String, Int>()
    private val codecColorFormatMap = HashMap<String, Int>()

    fun initCodecProfileLevel() {
        val fields = MediaCodecInfo.CodecProfileLevel::class.java.fields
        for (f in fields) {
            if (!Modifier.isFinal(f.modifiers) || !Modifier.isStatic(f.modifiers) || f.type != Int::class.java) {
                continue // 非静态常量
            }
            try {
                codecProfileLevelMap[f.name] = f.getInt(null)
            } catch (e: Exception) {}
        }
    }

    fun findProfileName(profile: Int, codecName: String): String? {
        if (codecProfileLevelMap.isEmpty()) {
            initCodecProfileLevel()
        }
        val profileNameStart = "${codecName}Profile"
        for ((key, value) in codecProfileLevelMap) {
            if (key.startsWith(profileNameStart) && value == profile) {
                return key
            }
        }
        return null
    }

    fun findLevelName(level: Int, codecName: String): String? {
        if (codecProfileLevelMap.isEmpty()) {
            initCodecProfileLevel()
        }
        val levelNameStart = "${codecName}Level"
        for ((key, value) in codecProfileLevelMap) {
            if (key.startsWith(levelNameStart) && value == level) {
                return key
            }
        }
        return null
    }

    fun findAACObjectProfileName(profile: Int): String? {
        if (codecProfileLevelMap.isEmpty()) {
            initCodecProfileLevel()
        }
        val profileNameStart = "AACObject"
        for ((key, value) in codecProfileLevelMap) {
            if (key.startsWith(profileNameStart) && value == profile) {
                return key
            }
        }
        return null
    }

    fun initCodecColorFormat() {
        val colorFormatStart = "COLOR_"
        val fields = MediaCodecInfo.CodecCapabilities::class.java.fields
        for (f in fields) {
            if (!Modifier.isFinal(f.modifiers) || !Modifier.isStatic(f.modifiers) || f.type != Int::class.java) {
                continue // 非静态常量
            }
            if (f.name.startsWith(colorFormatStart)) {
                try {
                    codecColorFormatMap[f.name] = f.getInt(null)
                } catch (e: Exception) {}
            }
        }
    }

    fun findCodecColorFormatName(colorFormat: Int): String? {
        if (codecProfileLevelMap.isEmpty()) {
            initCodecColorFormat()
        }
        for ((key, value) in codecColorFormatMap) {
            if (value == colorFormat) {
                return key
            }
        }
        return null
    }

}