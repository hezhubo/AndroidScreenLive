package com.hezb.live.recorder.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Project Name: AndroidScreenLive
 * File Name:    Size
 *
 * Description: 视频尺寸（分辨率）.
 *
 * @author  hezhubo
 * @date    2022年07月12日 23:31
 */
class Size(var width: Int, var height: Int) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt())

    override fun toString(): String {
        return "${width}x${height}"
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(width)
        parcel.writeInt(height)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Size> {
        override fun createFromParcel(parcel: Parcel): Size {
            return Size(parcel)
        }

        override fun newArray(size: Int): Array<Size?> {
            return arrayOfNulls(size)
        }
    }

}