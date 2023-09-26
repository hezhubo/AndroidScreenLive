package com.hezb.live.recorder.filter.audio

import com.hezb.lib.soundtouch.SoundTouch

/**
 * Project Name: AndroidScreenLive
 * File Name:    SoundTouchAudioFilter
 *
 * Description: SoundTouch音频滤镜.
 *
 * @author  hezhubo
 * @date    2023年09月26日 20:36
 */
class SoundTouchAudioFilter(private val type: VoiceType) : BaseAudioFilter() {

    private var soundTouch: SoundTouch? = null

    override fun init(sampleRate: Int, channels: Int) {
        soundTouch = SoundTouch().apply {
            setAudioFormat(sampleRate, channels)

            // TODO test params
            when (type) {
                VoiceType.KITTY -> {
                    setPitchSemiTones(8)
                }
                VoiceType.ROSE -> {
                    setPitch(2.1f)
                }
                VoiceType.UNCLE -> {
                    setPitch(0.8f)
                }
                VoiceType.FUNNY -> {
                    setPitchOctaves(1.0f)
                    setRate(1.2f)
                }
            }
        }
    }

    override fun onFilter(
        originBuffer: ByteArray,
        originBufferSize: Int,
        targetBuffer: ByteArray
    ): Int {
        return soundTouch?.process(originBuffer, originBufferSize, targetBuffer) ?: 0
    }

    override fun onDestroy() {
        soundTouch?.release()
        soundTouch = null
    }

    enum class VoiceType {
        KITTY, ROSE, UNCLE, FUNNY
    }

}