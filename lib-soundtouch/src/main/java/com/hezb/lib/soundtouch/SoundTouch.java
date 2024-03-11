package com.hezb.lib.soundtouch;

/**
 * Project Name: AndroidScreenLive
 * File Name:    SoundTouch
 *
 * Description: SoundTouch.
 *
 * @author  hezhubo
 * @date    2023年09月26日 20:26
 */
public final class SoundTouch {

    static {
        System.loadLibrary("soundtouch");
    }

    private long handle = 0; // 底层实例指针

    public native static String getVersion();

    private native long newInstance();

    private native void deleteInstance(long handle);

    private native void setSampleRate(long handle, int sampleRate);

    private native void setChannels(long handle, int channels);

    private native void setTempo(long handle, float tempo);

    private native void setTempoChange(long handle, int changeTempo);

    private native void setRate(long handle, float rate);

    private native void setRateChange(long handle, int changeRate);

    private native void setPitch(long handle, float pitch);

    private native void setPitchOctaves(long handle, float pitch);

    private native void setPitchSemiTones(long handle, int pitch);

    private native int process(long handle, byte[] source, int sourceSize, byte[] target);

    public SoundTouch() {
        handle = newInstance();
    }

    public void release() {
        deleteInstance(handle);
        handle = 0;
    }

    /**
     * 设置音频格式
     *
     * @param sampleRate 采样率
     * @param channels   通道数
     */
    public void setAudioFormat(int sampleRate, int channels) {
        setSampleRate(handle, sampleRate);
        setChannels(handle, channels);
    }

    /**
     * 设置音频的播放节奏(变速)
     * 不会改变音频的音调或音量
     *
     * @param tempo
     */
    public void setTempo(float tempo) {
        setTempo(handle, tempo);
    }

    /**
     * 调整音频的播放节奏(变速)，在原节奏1.0基础上，按百分比做增量
     * 不会改变音频的音调或音量
     *
     * @param changeTempo 取值范围[-50, 100]%
     */
    public void setTempoChange(int changeTempo) {
        setTempoChange(handle, changeTempo);
    }

    /**
     * 设置播放速度
     * 会改变音调
     *
     * @param rate
     */
    public void setRate(float rate) {
        setRate(handle, rate);
    }

    /**
     * 设置播放速度，在原速1.0基础上，按百分比做增量
     * 会改变音调
     *
     * @param changeRate 取值范围[-50, 100]%
     */
    public void setRateChange(int changeRate) {
        setRateChange(handle, changeRate);
    }

    /**
     * 设置音调值
     *
     * @param pitch
     */
    public void setPitch(float pitch) {
        setPitch(handle, pitch);
    }

    /**
     * 设置音调值，设置与原始音高相比的八度音高变化
     *
     * @param pitch 取值范围[-1.0, 1.0]
     */
    public void setPitchOctaves(float pitch) {
        setPitchOctaves(handle, pitch);
    }

    /**
     * 设置音调值，设置与原始音高相比的半音音高变化
     *
     * @param pitch 取值范围[-12, 12]
     */
    public void setPitchSemiTones(int pitch) {
        setPitchSemiTones(handle, pitch);
    }

    /**
     * 执行实时变化
     *
     * @param source     原始音频数据
     * @param sourceSize 原始音频数据大小
     * @param target     变化后音频数据
     * @return 变化后音频数据的大小
     */
    public int process(byte[] source, int sourceSize, byte[] target) {
        return process(handle, source, sourceSize, target);
    }

}
