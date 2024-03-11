package com.hezb.lib.rtmp;

/**
 * Project Name: AndroidScreenLive
 * File Name:    RtmpClient
 *
 * Description: rtmp客户端.
 *
 * @author  hezhubo
 * @date    2022年07月21日 13:00
 */
public class RtmpClient {

    static {
        System.loadLibrary("rtmp");
    }

    private long handle = 0; // 底层实例指针

    public native static String getVersion();

    private native long open(String url, boolean isPublishMode);

    private native int read(long rtmpPointer, byte[] data, int offset, int size);

    private native int write(long rtmpPointer, byte[] data, int size, int type, int ts);

    private native void close(long rtmpPointer);

    public boolean isConnected() {
        return handle != 0;
    }

    public boolean connect(String url, boolean isPublishMode) {
        close();
        // 底层返回rtmp的指针; handle == 0 则创建失败
        handle = open(url, isPublishMode);
        return handle != 0;
    }

    public int read(byte[] data, int offset, int size) {
        if (handle != 0) {
            return read(handle, data, offset, size);
        }
        return 0;
    }

    public boolean write(byte[] data, int size, int type, int ts) {
        if (handle != 0) {
            return write(handle, data, size, type, ts) == 1;
        }
        return false;
    }

    public void close() {
        if (handle != 0) {
            close(handle);
            handle = 0;
        }
    }

}
